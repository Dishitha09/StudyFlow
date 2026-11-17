package com.studyflow.model;

import com.studyflow.utils.ValidationUtils;
import java.util.List;
import java.util.function.Function;

/** A validated set of AI-generated questions and references to the supplied passages. */
public record QuestionSet(Type type, List<Question> questions) {
    public enum Type { SHORT_ANSWER, LONG_ANSWER, MCQ }
    public static final int MAX_SIZE = 8;

    public QuestionSet {
        ValidationUtils.required(type, "Question type");
        questions = List.copyOf(ValidationUtils.required(questions, "Questions"));
        if (questions.isEmpty() || questions.size() > MAX_SIZE) {
            throw new IllegalArgumentException("A question set needs between 1 and " + MAX_SIZE + " questions.");
        }
        for (Question question : questions) {
            if (type == Type.MCQ) {
                if (question.choices().size() != 4 || question.answerIndex() < 0
                        || question.answerIndex() >= question.choices().size() || !question.answer().isEmpty()) {
                    throw new IllegalArgumentException("A multiple-choice question needs four choices and one answer index.");
                }
            } else if (!question.choices().isEmpty() || question.answerIndex() != -1 || question.answer().isBlank()) {
                throw new IllegalArgumentException("A written question needs a model answer and no choices.");
            }
        }
    }

    public record Question(String prompt, List<String> choices, int answerIndex, String answer, String explanation,
                           List<String> sourceIds) {
        public Question {
            prompt = ValidationUtils.requiredText(prompt, "Question prompt");
            choices = List.copyOf(ValidationUtils.required(choices, "Question choices"));
            answer = ValidationUtils.textOrEmpty(answer);
            explanation = ValidationUtils.requiredText(explanation, "Answer explanation");
            sourceIds = List.copyOf(ValidationUtils.required(sourceIds, "Question source IDs"));
            if (sourceIds.isEmpty()) {
                throw new IllegalArgumentException("Each question needs a supplied source.");
            }
        }

        public String answerText() {
            if (answerIndex < 0) {
                return answer;
            }
            return choiceMarker(answerIndex) + " " + choices.get(answerIndex);
        }
    }

    public String toPlainText(Function<String, String> sourceName) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < questions.size(); index++) {
            Question question = questions.get(index);
            if (index != 0) {
                text.append("\n\n");
            }
            text.append(index + 1).append(". ").append(question.prompt());
            for (int choice = 0; choice < question.choices().size(); choice++) {
                text.append('\n').append(choiceMarker(choice)).append(' ').append(question.choices().get(choice));
            }
            text.append("\nAnswer: ").append(question.answerText()).append("\n").append(question.explanation());
            text.append("\nSources: ").append(String.join(", ", question.sourceIds().stream().map(sourceName).toList()));
        }
        return text.toString();
    }

    public static String choiceMarker(int index) {
        return Character.toString('A' + index) + ".";
    }
}
