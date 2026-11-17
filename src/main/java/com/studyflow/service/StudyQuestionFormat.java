package com.studyflow.service;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studyflow.model.QuestionSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Defines the provider schema and verifies the actual response before displaying it. */
final class StudyQuestionFormat {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final int MAX_JSON_LENGTH = 64_000;

    private StudyQuestionFormat() { }

    static String schema(List<String> sourceIds) {
        ObjectNode root = object("questions");
        ObjectNode question = object("prompt", "choices", "answerIndex", "answer", "explanation", "sourceIds");
        ObjectNode properties = (ObjectNode) question.get("properties");
        properties.set("prompt", type("string"));
        properties.set("choices", array(type("string")));
        properties.set("answerIndex", type("integer"));
        properties.set("answer", type("string"));
        properties.set("explanation", type("string"));
        ObjectNode source = type("string");
        var choices = source.putArray("enum");
        sourceIds.forEach(choices::add);
        properties.set("sourceIds", array(source));
        ((ObjectNode) root.get("properties")).set("questions", array(question));
        return root.toString();
    }

    static QuestionSet parse(String text, QuestionSet.Type type, int expectedCount, Set<String> sourceIds)
            throws IOException {
        try {
            require(text != null && text.length() <= MAX_JSON_LENGTH);
            JsonNode root = JSON.readTree(text);
            require(root != null && root.isObject() && root.size() == 1);
            JsonNode entries = root.path("questions");
            require(entries.isArray() && entries.size() == expectedCount);
            var prompts = new HashSet<String>();
            var questions = new ArrayList<QuestionSet.Question>(expectedCount);
            for (JsonNode entry : entries) {
                require(entry.isObject() && entry.size() == 6);
                String prompt = text(entry.path("prompt"), 700, false);
                require(prompts.add(comparable(prompt)));
                List<String> choices = strings(entry.path("choices"), 4, 250);
                require(choices.stream().map(StudyQuestionFormat::comparable).distinct().count() == choices.size());
                JsonNode index = entry.path("answerIndex");
                require(index.isIntegralNumber() && index.canConvertToInt());
                String answer = text(entry.path("answer"), 1500, type == QuestionSet.Type.MCQ);
                String explanation = text(entry.path("explanation"), 1000, false);
                List<String> references = strings(entry.path("sourceIds"), sourceIds.size(), 10);
                require(!references.isEmpty() && new HashSet<>(references).size() == references.size()
                        && sourceIds.containsAll(references));
                questions.add(new QuestionSet.Question(prompt, choices, index.intValue(), answer, explanation, references));
            }
            return new QuestionSet(type, questions);
        } catch (IOException | IllegalArgumentException invalid) {
            throw new IOException("The AI provider did not return a valid, source-linked question set. Try again.");
        }
    }

    private static ObjectNode object(String... required) {
        ObjectNode object = type("object");
        object.putObject("properties");
        var fields = object.putArray("required");
        for (String field : required) {
            fields.add(field);
        }
        return object.put("additionalProperties", false);
    }

    private static ObjectNode type(String type) {
        return JSON.createObjectNode().put("type", type);
    }

    private static ObjectNode array(ObjectNode item) {
        return type("array").set("items", item);
    }

    private static List<String> strings(JsonNode values, int limit, int maxLength) {
        require(values.isArray() && values.size() <= limit);
        List<String> result = new ArrayList<>(values.size());
        values.forEach(value -> result.add(text(value, maxLength, false)));
        return List.copyOf(result);
    }

    private static String text(JsonNode node, int maxLength, boolean allowEmpty) {
        require(node.isTextual());
        String value = node.textValue().strip();
        require(value.length() <= maxLength && (allowEmpty || !value.isEmpty()));
        return value;
    }

    private static String comparable(String text) {
        return text.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("Invalid question set.");
        }
    }
}
