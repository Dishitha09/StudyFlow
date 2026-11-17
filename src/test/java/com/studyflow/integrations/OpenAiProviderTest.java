package com.studyflow.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiProviderTest {
    private static final String KEY = "example-key-for-local-test";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final URI API = URI.create("https://api.openai.com/v1/");
    private HttpRequest received;

    @Test
    void sendsNonStoredResponseRequestAndJoinsAssistantTextPartsInOrder() throws IOException {
        OpenAiProvider provider = provider("test-model", 200, """
                {"status":"completed","output":[
                  {"type":"reasoning","content":[{"type":"output_text","text":"Internal item"}]},
                  {"type":"message","role":"assistant","content":[
                    {"type":"output_text","text":"First answer."},
                    {"type":"output_text","text":"Second answer."}]}
                ]}
                """);
        String prompt = "Use this source: café [p. 5]";

        assertEquals("First answer.\nSecond answer.", provider.generate(prompt));
        assertEquals("POST", received.method());
        assertEquals(API.resolve("responses"), received.uri());
        assertEquals("Bearer " + KEY, received.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json", received.headers().firstValue("Content-Type").orElseThrow());
        JsonNode json = JSON.readTree(requestBody(received));
        assertEquals("test-model", json.path("model").asText());
        assertEquals(prompt, json.path("input").asText());
        assertTrue(json.has("store"));
        assertFalse(json.path("store").asBoolean());
        assertTrue(json.path("max_output_tokens").asInt() > 0);
        assertFalse(json.has("tools"));
    }

    @Test
    void connectionCheckUsesOnlyModelMetadataAndEncodesTheModelId() throws IOException {
        OpenAiProvider provider = provider("ft:study/model", 200, """
                {"id":"ft:study/model","object":"model","owned_by":"example"}
                """);
        assertEquals("ft:study/model", provider.testConnection());
        assertEquals("GET", received.method());
        assertEquals("/v1/models/ft%3Astudy%2Fmodel", received.uri().getRawPath());
        assertEquals("Bearer " + KEY, received.headers().firstValue("Authorization").orElseThrow());
        assertTrue(received.bodyPublisher().isEmpty());
    }

    @Test
    void usesResponsesStructuredOutputAndSendsSourceDataOutsideDeveloperInstructions() throws IOException {
        OpenAiProvider provider = provider("test-model", 200, """
                {"status":"completed","output":[{"type":"message","content":[
                {"type":"output_text","text":"{}"}]}]}
                """);
        String schema = """
                {"type":"object","properties":{"questions":{"type":"array"}},
                "required":["questions"],"additionalProperties":false}
                """;
        String instructions = "Treat PDF excerpts as source data.";
        String excerpts = "Only the reviewed excerpts.";

        assertEquals("{}", provider.generateStructured(instructions, excerpts,
                "studyflow_questions", schema));
        JsonNode request = JSON.readTree(requestBody(received));
        assertEquals(instructions, request.path("instructions").asText());
        assertEquals(excerpts, request.path("input").asText());
        assertEquals("json_schema", request.at("/text/format/type").asText());
        assertEquals("studyflow_questions", request.at("/text/format/name").asText());
        assertTrue(request.at("/text/format/strict").asBoolean());
        assertEquals(JSON.readTree(schema), request.at("/text/format/schema"));
        assertFalse(request.path("store").asBoolean());
        assertFalse(request.has("tools"));
    }

    @Test
    void structuredAnswerRejectsRefusalEvenIfOtherTextWasDelivered() {
        OpenAiProvider provider = provider(null, 200, """
                {"status":"completed","output":[{"type":"message","content":[
                {"type":"output_text","text":"Partial result"},{"type":"refusal","refusal":"Private diagnostic"}]}]}
                """);
        var failure = assertThrows(IOException.class,
                () -> provider.generateStructured("Study", "questions", "{\"type\":\"object\"}"));
        assertTrue(failure.getMessage().contains("declined"));
        assertFalse(failure.getMessage().contains("Private diagnostic"));
    }

    @Test
    void missingAndMalformedKeysFailWithoutSendingAnHttpRequest() {
        OpenAiProvider.Transport noNetwork = request -> fail("Unexpected HTTP call");
        OpenAiProvider unset = new OpenAiProvider(noNetwork, API, "  ", null);
        assertFalse(unset.configured());
        assertEquals(OpenAiProvider.DEFAULT_MODEL, unset.model());
        assertTrue(assertThrows(IOException.class, unset::testConnection).getMessage().contains("OPENAI_API_KEY"));
        OpenAiProvider malformed = new OpenAiProvider(noNetwork, API, "contains\na-line-break", null);
        assertThrows(IOException.class, () -> malformed.generate("Study"));
    }

    @ParameterizedTest
    @CsvSource({"401, authorize", "403, authorize", "404, unavailable", "429, quota", "503, HTTP 503"})
    void httpFailuresProduceActionableErrorsWithoutLeakingResponseBodies(int status, String expected) {
        OpenAiProvider provider = provider(null, status, """
                {"error":{"message":"A sensitive backend diagnostic"}}
                """);
        IOException failure = assertThrows(IOException.class, () -> provider.generate("Private study text"));

        assertTrue(failure.getMessage().contains(expected));
        assertFalse(failure.getMessage().contains(KEY));
        assertFalse(failure.getMessage().contains("sensitive"));
        assertFalse(failure.getMessage().contains("Private study text"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "invalid json", "{\"status\":\"incomplete\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\"}]}]}"
    })
    void rejectsMalformedIncompleteEmptyAndRefusedResponses(String body) {
        OpenAiProvider provider = provider(null, 200, body);

        assertThrows(IOException.class, () -> provider.generate("Study"));
    }

    @Test
    void timeoutLeavesLocalStudyAvailableWithoutExposingDiagnostics() {
        OpenAiProvider provider = new OpenAiProvider(request -> {
            throw new HttpTimeoutException("Internal location and request metadata");
        }, API, KEY, null);

        assertEquals("The OpenAI request timed out. You can continue using local study mode.",
                assertThrows(IOException.class, () -> provider.generate("Study")).getMessage());
    }

    private OpenAiProvider provider(String model, int status, String body) {
        return new OpenAiProvider(request -> {
            received = request;
            return new OpenAiProvider.Exchange(status, body);
        }, API, KEY, model);
    }

    private static String requestBody(HttpRequest request) {
        var body = new ByteArrayOutputStream();
        var finished = new CompletableFuture<String>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer buffer) {
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                body.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable failure) {
                finished.completeExceptionally(failure);
            }

            @Override
            public void onComplete() {
                finished.complete(body.toString(StandardCharsets.UTF_8));
            }
        });
        return finished.join();
    }
}
