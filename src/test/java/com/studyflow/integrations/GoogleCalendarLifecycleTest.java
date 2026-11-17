package com.studyflow.integrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studyflow.model.GeneralTask;
import com.studyflow.model.Priority;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoogleCalendarLifecycleTest {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 17);

    @Test void oauthRejectsForeignStateAndUsesPkceThenSyncUpdatesWithoutCreatingDuplicates() throws Exception {
        var clock = new MutableClock();
        var provider = new GoogleFixture();
        GoogleCalendar calendar = connected(clock, provider);
        assertTrue(calendar.connected());
        assertNotEquals("qa-calendar", calendar.calendarId());
        Task task = new GeneralTask("fixture-id", "Read chapter", "", null, DATE, DATE, Priority.HIGH, TaskStatus.TODO);
        String remoteId = calendar.upsert(task, null);
        task.setTitle("Read and annotate");
        assertEquals(remoteId, calendar.upsert(task, null));
        assertEquals(1, provider.created);
        assertEquals(1, provider.updated);
        assertEquals("Read and annotate", provider.events.get(remoteId).path("summary").asText());
        assertEquals(DATE.toString(), provider.events.get(remoteId).at("/start/date").asText());
        assertEquals(DATE.plusDays(1).toString(), provider.events.get(remoteId).at("/end/date").asText());
        assertThrows(IOException.class, () -> calendar.delete(remoteId, "different-task"));
        assertEquals(0, provider.deleted);
        calendar.delete(remoteId, task.getId());
        calendar.delete(remoteId, task.getId());
        assertEquals(1, provider.deleted);
        assertTrue(provider.events.isEmpty());
        calendar.disconnect();
        assertFalse(calendar.connected());
    }

    @Test void expiredTokenRefreshesAndRepeatedUnauthorizedResponseClearsSession() throws Exception {
        var clock = new MutableClock();
        var provider = new GoogleFixture();
        GoogleCalendar calendar = connected(clock, provider);
        clock.value = clock.value.plusSeconds(3_601);
        assertNotNull(calendar.calendarId());
        assertEquals(1, provider.refreshes);
        provider.unauthorized = true;
        IOException refusal = assertThrows(IOException.class, calendar::calendarId);
        assertTrue(refusal.getMessage().contains("revoked"));
        assertEquals(2, provider.refreshes);
        assertFalse(calendar.connected());
        assertNull(calendar.connectedCalendarId());
        provider.unauthorized = false;
        calendar.connect();
        assertTrue(calendar.connected());
    }

    @Test void browserLoginCanBeCancelledBeforeAnyTokenRequest() throws Exception {
        var opened = new CountDownLatch(1);
        var provider = new GoogleFixture();
        var calendar = new GoogleCalendar("fixture-client", "fixture-secret", new MutableClock(), provider,
                uri -> opened.countDown());
        CompletableFuture<IOException> outcome = CompletableFuture.supplyAsync(() -> {
            try { calendar.connect(); return null; } catch (IOException error) { return error; }
        });
        assertTrue(opened.await(5, TimeUnit.SECONDS));
        assertTrue(calendar.cancelConnect());
        assertTrue(outcome.get(5, TimeUnit.SECONDS).getMessage().contains("cancelled"));
        assertFalse(calendar.connected());
        assertEquals(0, provider.grants);
    }

    private static GoogleCalendar connected(MutableClock clock, GoogleFixture provider) throws IOException {
        HttpClient browser = HttpClient.newHttpClient();
        var calendar = new GoogleCalendar("fixture-client", "fixture-secret", clock, provider, url -> {
            assertEquals("accounts.google.com", url.getHost());
            var fields = form(url.getRawQuery());
            provider.challenge = fields.get("code_challenge");
            assertEquals("S256", fields.get("code_challenge_method"));
            String callback = fields.get("redirect_uri");
            assertTrue(callback.startsWith("http://127.0.0.1:"));
            try {
                var wrong = browser.send(HttpRequest.newBuilder(URI.create(callback + "?state=unrelated&code=wrong"))
                        .GET().build(), HttpResponse.BodyHandlers.discarding());
                assertEquals(400, wrong.statusCode());
                var right = browser.send(HttpRequest.newBuilder(URI.create(callback + "?state="
                        + fields.get("state") + "&code=fixture-grant")).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
                assertEquals(200, right.statusCode());
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException(error);
            }
        });
        calendar.connect();
        return calendar;
    }

    private static Map<String, String> form(String encoded) {
        var values = new HashMap<String, String>();
        for (String field : encoded.split("&")) {
            String[] pair = field.split("=", 2);
            values.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return values;
    }

    private static String body(HttpRequest request) throws IOException {
        var sink = new ByteArrayOutputStream();
        var finished = new CompletableFuture<Void>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer buffer) {
                byte[] part = new byte[buffer.remaining()];
                buffer.get(part);
                sink.writeBytes(part);
            }
            @Override public void onError(Throwable cause) { finished.completeExceptionally(cause); }
            @Override public void onComplete() { finished.complete(null); }
        });
        try { finished.get(5, TimeUnit.SECONDS); }
        catch (Exception error) { throw new IOException(error); }
        return sink.toString(StandardCharsets.UTF_8);
    }

    private static final class MutableClock extends Clock {
        Instant value = DATE.atStartOfDay().toInstant(ZoneOffset.UTC);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(value, zone); }
        @Override public Instant instant() { return value; }
    }

    private static final class GoogleFixture implements GoogleCalendar.Transport {
        private final ObjectMapper mapper = new ObjectMapper();
        private final Map<String, ObjectNode> events = new HashMap<>();
        private String challenge;
        private int created, updated, deleted, grants, refreshes;
        private boolean unauthorized;
        @Override public GoogleCalendar.Exchange send(HttpRequest request) throws IOException {
            if (request.uri().getHost().equals("oauth2.googleapis.com")) {
                var fields = form(body(request));
                if ("authorization_code".equals(fields.get("grant_type"))) {
                    grants++;
                    assertEquals("fixture-grant", fields.get("code"));
                    try {
                        byte[] digest = MessageDigest.getInstance("SHA-256")
                                .digest(fields.get("code_verifier").getBytes(StandardCharsets.UTF_8));
                        assertEquals(challenge, Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
                    } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
                } else {
                    refreshes++;
                    assertEquals("fixture-refresh", fields.get("refresh_token"));
                }
                return new GoogleCalendar.Exchange(200, """
                        {"access_token":"fixture-access","refresh_token":"fixture-refresh","expires_in":3600}
                        """);
            }
            assertEquals("www.googleapis.com", request.uri().getHost());
            assertEquals("Bearer fixture-access", request.headers().firstValue("Authorization").orElseThrow());
            if (unauthorized) return new GoogleCalendar.Exchange(401, "{}");
            String path = request.uri().getPath();
            if (path.endsWith("/primary")) return new GoogleCalendar.Exchange(200, "{\"id\":\"qa-calendar\"}");
            String id = path.substring(path.lastIndexOf('/') + 1);
            return switch (request.method()) {
                case "GET" -> events.containsKey(id) ? new GoogleCalendar.Exchange(200, events.get(id).toString())
                        : new GoogleCalendar.Exchange(404, "{}");
                case "POST" -> {
                    var event = (ObjectNode) mapper.readTree(body(request));
                    assertNull(events.put(event.path("id").asText(), event));
                    created++;
                    yield new GoogleCalendar.Exchange(200, event.toString());
                }
                case "PATCH" -> {
                    events.get(id).setAll((ObjectNode) mapper.readTree(body(request)));
                    updated++;
                    yield new GoogleCalendar.Exchange(200, events.get(id).toString());
                }
                case "DELETE" -> {
                    assertNotNull(events.remove(id));
                    deleted++;
                    yield new GoogleCalendar.Exchange(204, "");
                }
                default -> throw new AssertionError("Unexpected request: " + request.method());
            };
        }
    }
}
