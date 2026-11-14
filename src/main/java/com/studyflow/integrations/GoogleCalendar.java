package com.studyflow.integrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.model.Task;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/** Desktop OAuth and event operations for the signed-in primary calendar. */
public final class GoogleCalendar {
    private static final String API = "https://www.googleapis.com/calendar/v3/calendars/primary";
    private final String clientId = System.getenv("STUDYFLOW_GOOGLE_CLIENT_ID");
    private final String clientSecret = System.getenv("STUDYFLOW_GOOGLE_CLIENT_SECRET");
    private HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private String access;
    private String refresh;
    private Instant expires = Instant.EPOCH;

    public boolean configured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public boolean connected() {
        return access != null;
    }

    public void connect() throws IOException {
        if (!configured()) {
            throw new IOException("Set the Google Desktop OAuth client ID and secret before connecting.");
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("Google sign-in needs a system browser.");
        }
        String verifier = randomString();
        String state = randomString();
        HttpServer listener = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String redirect = "http://127.0.0.1:" + listener.getAddress().getPort() + "/oauth";
        CompletableFuture<String> code = new CompletableFuture<>();
        listener.createContext("/oauth", exchange -> {
            try (exchange) {
                Map<String, String> params = query(exchange.getRequestURI().getRawQuery());
                boolean valid = state.equals(params.get("state"));
                String message = valid ? "Return to StudyFlow to finish connecting."
                        : "This response does not match the active StudyFlow authorization.";
                byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(valid ? 200 : 400, bytes.length);
                exchange.getResponseBody().write(bytes);
                if (valid && params.containsKey("code")) {
                    code.complete(params.get("code"));
                } else if (valid) {
                    code.completeExceptionally(new IOException("Google authorization was denied."));
                }
            }
        });
        listener.start();
        try {
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash(verifier));
            Map<String, String> arguments = Map.of("client_id", clientId, "redirect_uri", redirect,
                    "response_type", "code", "state", state, "scope",
                    "https://www.googleapis.com/auth/calendar.events.owned "
                            + "https://www.googleapis.com/auth/calendar.calendars.readonly",
                    "code_challenge", challenge, "code_challenge_method", "S256", "access_type", "offline",
                    "prompt", "consent");
            Desktop.getDesktop().browse(URI.create("https://accounts.google.com/o/oauth2/v2/auth?" + form(arguments)));
            String claim = code.get(90, TimeUnit.SECONDS);
            disconnect();
            token(Map.of("client_id", clientId, "client_secret", clientSecret, "code", claim,
                    "redirect_uri", redirect, "code_verifier", verifier, "grant_type", "authorization_code"));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Google sign-in was interrupted.", error);
        } catch (TimeoutException error) {
            throw new IOException("Google sign-in timed out.", error);
        } catch (ExecutionException error) {
            throw new IOException("Google sign-in was not approved.", error);
        } finally {
            listener.stop(0);
        }
    }

    public void disconnect() {
        access = null;
        refresh = null;
        expires = Instant.EPOCH;
    }

    public String calendarId() throws IOException {
        var response = eventRequest("GET", "", null);
        requireSuccess(response);
        String identity = mapper.readTree(response.body()).path("id").asText("");
        if (identity.isBlank()) {
            throw new IOException("Google returned no primary calendar ID.");
        }
        return HexFormat.of().formatHex(hash(identity));
    }

    public String upsert(Task task) throws IOException {
        if (task.getDueDate() == null) {
            throw new IllegalArgumentException("Only tasks with deadlines can sync.");
        }
        String id = eventId(task.getId());
        String payload = mapper.writeValueAsString(Map.of(
                "id", id, "summary", task.getTitle(), "description", task.getDescription(),
                "start", Map.of("date", task.getDueDate().toString()),
                "end", Map.of("date", task.getDueDate().plusDays(1).toString()),
                "extendedProperties", Map.of("private", Map.of("studyflowTaskId", task.getId()))));
        var response = eventRequest("GET", "/events/" + id, null);
        if (response.statusCode() == 404 || response.statusCode() == 410) {
            response = eventRequest("POST", "/events", payload);
            if (response.statusCode() != 409) {
                requireSuccess(response);
                return id;
            }
            response = eventRequest("GET", "/events/" + id, null);
        }
        requireSuccess(response);
        verifyOwnership(task.getId(), response.body());
        requireSuccess(eventRequest("PUT", "/events/" + id, payload));
        return id;
    }

    public void delete(String eventId, String taskId) throws IOException {
        if (!eventId.matches("[a-v0-9]{5,1024}")) {
            throw new IllegalArgumentException("Invalid Google event ID.");
        }
        var existing = eventRequest("GET", "/events/" + eventId, null);
        if (existing.statusCode() == 404 || existing.statusCode() == 410) {
            return;
        }
        requireSuccess(existing);
        verifyOwnership(taskId, existing.body());
        var response = eventRequest("DELETE", "/events/" + eventId, null);
        if (response.statusCode() != 404 && response.statusCode() != 410) {
            requireSuccess(response);
        }
    }

    private void verifyOwnership(String taskId, String payload) throws IOException {
        String owner = mapper.readTree(payload).path("extendedProperties").path("private")
                .path("studyflowTaskId").asText();
        if (!taskId.equals(owner)) {
            throw new IOException("This Google event does not belong to the selected StudyFlow task.");
        }
    }

    public static String eventId(String taskId) {
        return HexFormat.of().formatHex(hash(taskId));
    }

    private HttpResponse<String> eventRequest(String method, String path, String body) throws IOException {
        if (access == null) {
            throw new IOException("Connect Google Calendar first.");
        }
        if (Instant.now().isAfter(expires.minusSeconds(30)) && refresh != null) {
            refresh();
        }
        var response = sendEvent(method, path, body);
        if (response.statusCode() == 401 && refresh != null) {
            refresh();
            return sendEvent(method, path, body);
        }
        return response;
    }

    private HttpResponse<String> sendEvent(String method, String path, String body) throws IOException {
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        return send(HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + access).header("Content-Type", "application/json")
                .method(method, publisher).build());
    }

    private void refresh() throws IOException {
        token(Map.of("client_id", clientId, "client_secret", clientSecret, "refresh_token", refresh,
                "grant_type", "refresh_token"));
    }

    private void token(Map<String, String> params) throws IOException {
        var response = send(HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(params))).build());
        if (response.statusCode() != 200) {
            disconnect();
            throw new IOException("Google could not authorize StudyFlow. Check the OAuth credentials and reconnect.");
        }
        var data = mapper.readTree(response.body());
        String nextAccess = data.path("access_token").asText("");
        if (nextAccess.isBlank()) {
            throw new IOException("Google returned no access token.");
        }
        access = nextAccess;
        if (data.hasNonNull("refresh_token")) {
            refresh = data.path("refresh_token").asText();
        }
        expires = Instant.now().plusSeconds(Math.max(1, data.path("expires_in").asLong(3600)));
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            if (http == null) {
                http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
            }
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.UncheckedIOException error) {
            throw new IOException("Java could not initialize networking on this computer.", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Google Calendar request interrupted.", error);
        }
    }

    private static void requireSuccess(HttpResponse<?> response) throws IOException {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Google Calendar request failed (HTTP " + status
                    + "). Check authorization, permissions and API quota.");
        }
    }

    private String randomString() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }

    private static Map<String, String> query(String input) {
        Map<String, String> values = new HashMap<>();
        if (input != null) {
            for (String item : input.split("&")) {
                String[] entry = item.split("=", 2);
                if (entry.length == 2) {
                    values.put(entry[0], URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
                }
            }
        }
        return values;
    }
}
