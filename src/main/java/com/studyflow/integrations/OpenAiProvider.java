package com.studyflow.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.interfaces.AIProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.StringJoiner;

/** Optional, non-streaming text generation using OpenAI's Responses API. */
public final class OpenAiProvider implements AIProvider {
    public static final String DEFAULT_MODEL = "gpt-6-luna";

    private static final URI API_BASE = URI.create("https://api.openai.com/v1/");
    private static final Duration CONNECTION_TEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(35);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Transport transport;
    private final URI apiBase;
    private final String apiKey;
    private final String model;

    public static OpenAiProvider fromEnvironment() {
        return new OpenAiProvider(OpenAiProvider::exchange, API_BASE,
                System.getenv("OPENAI_API_KEY"), System.getenv("STUDYFLOW_OPENAI_MODEL"));
    }

    OpenAiProvider(Transport transport, URI apiBase, String apiKey, String model) {
        this.transport = Objects.requireNonNull(transport);
        this.apiBase = Objects.requireNonNull(apiBase);
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.strip();
    }

    @Override
    public boolean configured() {
        return !apiKey.isEmpty();
    }

    public String model() {
        return model;
    }

    /** Checks access to model metadata without sending study content or generating a response. */
    public String testConnection() throws IOException {
        String modelPath = URLEncoder.encode(model, StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest request = request("models/" + modelPath, CONNECTION_TEST_TIMEOUT).GET().build();
        JsonNode result = send(request);
        String id = result.path("id").asText("");
        if (!"model".equals(result.path("object").asText()) || id.isBlank()) {
            throw new IOException("OpenAI returned unexpected model metadata.");
        }
        return id;
    }

    @Override
    public String generate(String prompt) throws IOException {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Enter a question or choose study material.");
        }
        String payload = JSON.createObjectNode()
                .put("model", model)
                .put("input", prompt)
                .put("store", false)
                .put("max_output_tokens", 2200)
                .toString();
        HttpRequest request = request("responses", REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        JsonNode result = send(request);
        if (!"completed".equals(result.path("status").asText())) {
            throw new IOException("OpenAI did not complete the response. Try a narrower topic or use local study mode.");
        }
        return readText(result);
    }

    private HttpRequest.Builder request(String path, Duration timeout) throws IOException {
        if (!configured()) {
            throw new IOException("Set OPENAI_API_KEY to connect OpenAI, or choose local study mode.");
        }
        if (apiKey.chars().anyMatch(value -> value <= ' ' || value >= 127)) {
            throw new IOException("OPENAI_API_KEY contains invalid characters.");
        }
        return HttpRequest.newBuilder(apiBase.resolve(path)).timeout(timeout)
                .header("Accept", "application/json").header("Authorization", "Bearer " + apiKey);
    }

    private JsonNode send(HttpRequest request) throws IOException {
        Exchange response;
        try {
            response = transport.send(request);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("The OpenAI request was interrupted.");
        } catch (HttpTimeoutException failure) {
            throw new IOException("The OpenAI request timed out. You can continue using local study mode.");
        } catch (UncheckedIOException failure) {
            throw new IOException("The OpenAI connection could not be initialized. You can continue using local study mode.");
        } catch (IOException failure) {
            throw new IOException("Could not reach OpenAI. Check your connection or use local study mode.");
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException(errorMessage(response.statusCode()));
        }
        try {
            JsonNode result = JSON.readTree(response.body());
            if (result != null && result.isObject()) {
                return result;
            }
        } catch (IOException malformed) {
            // Jackson's error details may contain study text or credential diagnostics.
            throw new IOException("OpenAI returned an unreadable response.");
        }
        throw new IOException("OpenAI returned an unreadable response.");
    }

    private static Exchange exchange(HttpRequest request) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Exchange(response.statusCode(), response.body());
        }
    }

    @FunctionalInterface
    interface Transport {
        Exchange send(HttpRequest request) throws IOException, InterruptedException;
    }

    record Exchange(int statusCode, String body) {}

    private static String readText(JsonNode result) throws IOException {
        StringJoiner parts = new StringJoiner("\n");
        boolean refused = false;
        for (JsonNode item : result.path("output")) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode part : item.path("content")) {
                String type = part.path("type").asText();
                JsonNode text = part.path("text");
                if ("output_text".equals(type) && text.isTextual() && !text.textValue().isBlank()) {
                    parts.add(text.textValue());
                } else if ("refusal".equals(type)) {
                    refused = true;
                }
            }
        }
        if (parts.length() > 0) {
            return parts.toString();
        }
        if (refused) {
            throw new IOException("OpenAI declined to generate an answer. Local study passages are still available.");
        }
        throw new IOException("OpenAI returned a response without study text.");
    }

    private static String errorMessage(int status) {
        return switch (status) {
            case 401, 403 -> "OpenAI did not authorize the request. Check the API key and project permissions.";
            case 404 -> "The configured OpenAI model is unavailable. Check STUDYFLOW_OPENAI_MODEL and project access.";
            case 429 -> "OpenAI rate limit or API quota reached. Check your API plan or use local study mode.";
            default -> "OpenAI returned HTTP " + status + ". You can continue using local study mode.";
        };
    }
}
