package com.studyflow.integrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studyflow.model.GeneralTask;
import com.studyflow.model.Priority;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.repository.FileStorage;
import com.studyflow.repository.IntegrationRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GoogleCalendarLifecycleTest {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 17);

    @TempDir
    Path profile;

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

    @Test void listsMonthInPrimaryCalendarTimeZoneAndFiltersOutboundAndCancelledEventsAcrossPages() throws Exception {
        var provider = new GoogleFixture();
        provider.eventPages = List.of("""
                {"nextPageToken":"opaque +/&=ß","items":[
                  {"id":"all-day","summary":"Conference","start":{"date":"2026-10-31"},
                    "end":{"date":"2026-11-03"}},
                  {"id":"cancelled","status":"cancelled"},
                  {"id":"studyflow","extendedProperties":{"private":{"studyflowTaskId":"task-id"}}},
                  {"id":"at-midnight","summary":"Ends at midnight","start":{"dateTime":"2026-11-08T04:30:00Z"},
                    "end":{"dateTime":"2026-11-08T05:00:00Z"}},
                  {"id":"timed","summary":"Seminar","start":{"dateTime":"2026-11-10T15:00:00Z"},
                    "end":{"dateTime":"2026-11-10T16:30:00Z"}}
                ]}
                """, """
                {"items":[
                  {"id":"same-time","summary":"Cross-zone","start":{"dateTime":"2026-11-11T16:00:00",
                    "timeZone":"Europe/Zurich"},"end":{"dateTime":"2026-11-11T16:30:00",
                    "timeZone":"Europe/Zurich"}},
                  {"id":"no-end","endTimeUnspecified":true,"start":{"dateTime":"2026-11-12T15:00:00Z"}},
                  {"id":"outside","start":{"date":"2026-12-01"},"end":{"date":"2026-12-02"}},
                  {"id":"all-day","summary":"Duplicate page item","start":{"date":"2026-10-31"},
                    "end":{"date":"2026-11-03"}}
                ]}
                """);
        GoogleCalendar calendar = connected(new MutableClock(), provider);
        var events = calendar.listEvents(YearMonth.of(2026, 11));

        assertEquals(List.of("all-day", "at-midnight", "timed", "same-time", "no-end"),
                events.stream().map(GoogleCalendar.Event::id).toList());
        assertEquals(new GoogleCalendar.Event("all-day", "Conference", LocalDate.of(2026, 10, 31),
                LocalDate.of(2026, 11, 3), ""), events.get(0));
        assertEquals(LocalDate.of(2026, 11, 7), events.get(1).startDate());
        assertEquals(LocalDate.of(2026, 11, 8), events.get(1).endDateExclusive());
        assertEquals(new GoogleCalendar.Event("timed", "Seminar", LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 11), "10:00 – 11:30"), events.get(2));
        assertEquals("10:00 – 10:30", events.get(3).timeLabel());
        assertEquals(new GoogleCalendar.Event("no-end", "Untitled event", LocalDate.of(2026, 11, 12),
                LocalDate.of(2026, 11, 13), "10:00"), events.get(4));

        assertEquals(2, provider.eventListRequests.size());
        Map<String, String> first = provider.eventListRequests.getFirst();
        Map<String, String> second = provider.eventListRequests.getLast();
        assertEquals("America/New_York", first.get("timeZone"));
        assertEquals(OffsetDateTime.parse("2026-11-01T00:00:00-04:00"), OffsetDateTime.parse(first.get("timeMin")));
        assertEquals(OffsetDateTime.parse("2026-12-01T00:00:00-05:00"), OffsetDateTime.parse(first.get("timeMax")));
        assertEquals("true", first.get("singleEvents"));
        assertEquals("startTime", first.get("orderBy"));
        assertEquals("false", first.get("showDeleted"));
        assertFalse(first.containsKey("pageToken"));
        var nextParameters = new HashMap<>(first);
        nextParameters.put("pageToken", "opaque +/&=ß");
        assertEquals(nextParameters, second);
        assertEquals(0, provider.created + provider.updated + provider.deleted);
    }

    @Test void cancelsEventReadingBeforeNetworkAndBetweenPagesWithoutReturningPartialResults() throws Exception {
        var provider = new GoogleFixture();
        provider.eventPages = List.of("""
                {"nextPageToken":"next","items":[{"id":"first","start":{"date":"2026-11-17"},
                "end":{"date":"2026-11-18"}}]}
                """);
        GoogleCalendar calendar = connected(new MutableClock(), provider);
        YearMonth month = YearMonth.of(2026, 11);
        int before = provider.calendarRequests;
        assertTrue(calendar.listEvents(month, () -> true).isEmpty());
        assertEquals(before, provider.calendarRequests);

        var cancelled = new AtomicBoolean();
        provider.afterEventList = () -> cancelled.set(true);
        assertTrue(calendar.listEvents(month, cancelled::get).isEmpty());
        assertEquals(1, provider.eventListRequests.size());
    }

    @Test void failsClearlyForRepeatedPageTokensAndAvoidsNetworkAfterDisconnect() throws Exception {
        var provider = new GoogleFixture();
        provider.eventPages = List.of("{\"nextPageToken\":\"repeat\"}", "{\"nextPageToken\":\"repeat\"}");
        GoogleCalendar calendar = connected(new MutableClock(), provider);
        YearMonth month = YearMonth.of(2026, 11);
        assertTrue(assertThrows(IOException.class, () -> calendar.listEvents(month)).getMessage().contains("repeated"));
        assertEquals(2, provider.eventListRequests.size());
        int before = provider.calendarRequests;
        calendar.disconnect();
        assertThrows(IOException.class, () -> calendar.listEvents(month));
        assertEquals(before, provider.calendarRequests);
    }

    @Test void automaticSyncDoesNotSendRequestsWhenOffDisconnectedCancelledOrUndated() throws Exception {
        var clock = new MutableClock();
        var provider = new GoogleFixture();
        GoogleCalendar calendar = connected(clock, provider);
        Task dated = task("dated", DATE);
        try (FileStorage storage = new FileStorage(profile)) {
            var integrations = new LocalIntegrations(new IntegrationRepository(storage), calendar, clock);
            int before = provider.calendarRequests;
            assertEquals(0, integrations.syncAutomatically(false, List.of(dated), List.of(), () -> false));
            assertEquals(0, integrations.syncAutomatically(true, List.of(dated), List.of(), () -> true));
            assertEquals(0, integrations.syncAutomatically(true, List.of(task("undated", null)), List.of(),
                    () -> false));
            calendar.disconnect();
            assertEquals(0, integrations.syncAutomatically(true, List.of(dated), List.of(), () -> false));
            assertEquals(before, provider.calendarRequests);
        }
    }

    @Test void automaticSyncChecksPersistedContentAndKeepsMappingsSeparateByCalendar() throws Exception {
        var clock = new MutableClock();
        var provider = new GoogleFixture();
        GoogleCalendar calendar = connected(clock, provider);
        Task task = task("dated", DATE);
        var subject = new Subject("optics", "Optics", "PHY101", "", List.of());
        task.setSubjectId(subject.getId());
        String id = GoogleCalendar.eventId(task.getId());
        try (FileStorage storage = new FileStorage(profile)) {
            var integrations = new LocalIntegrations(new IntegrationRepository(storage), calendar, clock);
            assertEquals(1, integrations.syncAutomatically(true, List.of(task), List.of(subject), () -> false));
            assertTrue(provider.events.get(id).path("description").asText().contains("Optics (PHY101)"));
        }

        calendar.disconnect();
        task.setDueDate(DATE.plusDays(2));
        subject.setName("Applied Optics");
        try (FileStorage storage = new FileStorage(profile)) {
            var integrations = new LocalIntegrations(new IntegrationRepository(storage), calendar, clock);
            assertEquals(0, integrations.syncAutomatically(true, List.of(task), List.of(subject), () -> false));
            assertEquals(0, provider.updated);
            calendar.connect();
            assertEquals(1, integrations.syncAutomatically(true, List.of(task), List.of(subject), () -> false));
            assertEquals(1, provider.created);
            assertEquals(1, provider.updated);
            assertEquals(DATE.plusDays(2).toString(), provider.events.get(id).at("/start/date").asText());
            assertTrue(provider.events.get(id).path("description").asText().contains("Applied Optics (PHY101)"));
        }

        try (FileStorage storage = new FileStorage(profile)) {
            var reopened = new LocalIntegrations(new IntegrationRepository(storage), calendar, clock);
            int before = provider.calendarRequests;
            assertEquals(0, reopened.syncAutomatically(true, List.of(task), List.of(subject), () -> false));
            assertEquals(before, provider.calendarRequests);

            var otherProvider = new GoogleFixture("another-calendar");
            GoogleCalendar otherCalendar = connected(clock, otherProvider);
            var otherAccount = new LocalIntegrations(new IntegrationRepository(storage), otherCalendar, clock);
            assertEquals(1, otherAccount.syncAutomatically(true, List.of(task), List.of(subject), () -> false));
            assertEquals(1, otherProvider.created);
            assertEquals(1, provider.created);
            assertTrue(otherProvider.events.containsKey(id));
            assertEquals(before, provider.calendarRequests);
        }
    }

    @Test void automaticSyncRetriesAnInterruptedBatchWithoutDuplicatingAcknowledgedOrUncertainEvents() throws Exception {
        var clock = new MutableClock();
        var provider = new GoogleFixture();
        GoogleCalendar calendar = connected(clock, provider);
        Task first = task("first", DATE);
        Task second = task("second", DATE.plusDays(1));
        String uncertain = GoogleCalendar.eventId(second.getId());
        provider.loseCreateResponseFor = uncertain;
        try (FileStorage storage = new FileStorage(profile)) {
            var integrations = new LocalIntegrations(new IntegrationRepository(storage), calendar, clock);
            assertThrows(IOException.class,
                    () -> integrations.syncAutomatically(true, List.of(first, second), List.of(), () -> false));
            assertEquals(2, provider.created);
            assertEquals(0, provider.updated);
            assertTrue(integrations.linkedCalendarTasks().contains(first.getId()));
            assertFalse(integrations.linkedCalendarTasks().contains(second.getId()));

            assertEquals(1, integrations.syncAutomatically(true, List.of(first, second), List.of(), () -> false));
            assertEquals(2, provider.created);
            assertEquals(1, provider.updated);
            assertTrue(provider.events.containsKey(uncertain));
            assertTrue(integrations.linkedCalendarTasks().containsAll(List.of(first.getId(), second.getId())));
        }
    }

    @Test void automaticSyncStopsBetweenEventsWhenCancellationIsRequested() throws Exception {
        var clock = new MutableClock();
        var provider = new GoogleFixture();
        GoogleCalendar calendar = connected(clock, provider);
        Task first = task("first", DATE);
        Task second = task("second", DATE.plusDays(1));
        try (FileStorage storage = new FileStorage(profile)) {
            var integrations = new LocalIntegrations(new IntegrationRepository(storage), calendar, clock);
            assertEquals(1, integrations.syncAutomatically(true, List.of(first, second), List.of(),
                    () -> provider.created > 0));
            assertFalse(provider.events.containsKey(GoogleCalendar.eventId(second.getId())));
            assertEquals(1, integrations.syncAutomatically(true, List.of(first, second), List.of(), () -> false));
            assertEquals(2, provider.created);
            assertEquals(0, provider.updated);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void automaticSyncCancellationDuringLookupStopsTheFollowingCreateOrUpdate(boolean existingEvent) throws Exception {
        var clock = new MutableClock();
        var provider = new GoogleFixture();
        GoogleCalendar calendar = connected(clock, provider);
        Task task = task("looked-up", DATE);
        if (existingEvent) {
            calendar.upsert(task, null);
            task.setTitle("Changed while offline");
        }
        var cancelled = new AtomicBoolean();
        provider.afterEventRead = () -> cancelled.set(true);
        try (FileStorage storage = new FileStorage(profile)) {
            var integrations = new LocalIntegrations(new IntegrationRepository(storage), calendar, clock);
            assertEquals(0, integrations.syncAutomatically(true, List.of(task), List.of(), cancelled::get));
            assertTrue(cancelled.get());
            assertEquals(existingEvent ? 1 : 0, provider.created);
            assertEquals(0, provider.updated);
            assertFalse(integrations.linkedCalendarTasks().contains(task.getId()));
        }
    }

    @Test void automaticSyncStillSendsValidTasksAfterANonExportableDate() throws Exception {
        var clock = new MutableClock();
        var provider = new GoogleFixture();
        GoogleCalendar calendar = connected(clock, provider);
        Task invalid = task("non-exportable", LocalDate.of(9999, 12, 31));
        Task valid = task("valid-range", DATE);
        try (FileStorage storage = new FileStorage(profile)) {
            var integrations = new LocalIntegrations(new IntegrationRepository(storage), calendar, clock);
            assertThrows(IllegalArgumentException.class,
                    () -> integrations.syncAutomatically(true, List.of(invalid, valid), List.of(), () -> false));
            assertEquals(1, provider.created);
            assertTrue(provider.events.containsKey(GoogleCalendar.eventId(valid.getId())));
            assertFalse(provider.events.containsKey(GoogleCalendar.eventId(invalid.getId())));
            assertEquals(Set.of(valid.getId()), integrations.linkedCalendarTasks());
        }
    }

    @Test void automaticSyncUpgradesALegacyMappingWithoutCreatingAnotherEvent() throws Exception {
        var clock = new MutableClock();
        var provider = new GoogleFixture();
        GoogleCalendar calendar = connected(clock, provider);
        Task task = task("legacy", DATE);
        String eventId = calendar.upsert(task, null);
        String mapping = "google:" + calendar.connectedCalendarId() + ":" + task.getId();
        try (FileStorage storage = new FileStorage(profile)) {
            var repository = new IntegrationRepository(storage);
            Files.writeString(profile.resolve("integrations/links.json"),
                    "[{\"id\":\"" + mapping + "\",\"type\":\"google\",\"value\":\"" + eventId + "\"}]");
            var integrations = new LocalIntegrations(repository, calendar, clock);
            assertTrue(integrations.linkedCalendarTasks().contains(task.getId()));
            assertEquals(1, integrations.syncAutomatically(true, List.of(task), List.of(), () -> false));
            assertEquals(1, provider.created);
            assertEquals(1, provider.updated);
            int before = provider.calendarRequests;
            assertEquals(0, integrations.syncAutomatically(true, List.of(task), List.of(), () -> false));
            assertEquals(before, provider.calendarRequests);
        }
    }

    @Test void manuallyRemovedEventRemainsSuppressedAcrossReopenUntilExplicitSync() throws Exception {
        var clock = new MutableClock();
        var provider = new GoogleFixture();
        GoogleCalendar calendar = connected(clock, provider);
        Task task = task("manually-removed", DATE);
        try (FileStorage storage = new FileStorage(profile)) {
            var integrations = new LocalIntegrations(new IntegrationRepository(storage), calendar, clock);
            assertEquals(1, integrations.syncAutomatically(true, List.of(task), List.of(), () -> false));
            integrations.deleteEvent(task.getId());
            assertEquals(1, provider.deleted);
            assertTrue(provider.events.isEmpty());
            assertFalse(integrations.linkedCalendarTasks().contains(task.getId()));
        }

        try (FileStorage storage = new FileStorage(profile)) {
            var reopened = new LocalIntegrations(new IntegrationRepository(storage), calendar, clock);
            task.setTitle("Offline edit");
            int before = provider.calendarRequests;
            assertEquals(0, reopened.syncAutomatically(true, List.of(task), List.of(), () -> false));
            assertEquals(before, provider.calendarRequests);
            reopened.sync(List.of(task), List.of());
            assertEquals(2, provider.created);
            assertEquals("Offline edit", provider.events.get(GoogleCalendar.eventId(task.getId()))
                    .path("summary").asText());
            before = provider.calendarRequests;
            assertEquals(0, reopened.syncAutomatically(true, List.of(task), List.of(), () -> false));
            assertEquals(before, provider.calendarRequests);
        }
    }

    private static Task task(String id, LocalDate deadline) {
        return new GeneralTask(id, "Read chapter", "", null, DATE, deadline, Priority.HIGH, TaskStatus.TODO);
    }

    private static GoogleCalendar connected(MutableClock clock, GoogleFixture provider) throws IOException {
        HttpClient browser = HttpClient.newHttpClient();
        var calendar = new GoogleCalendar("fixture-client", "fixture-secret", clock, provider, url -> {
            assertEquals("accounts.google.com", url.getHost());
            var fields = form(url.getRawQuery());
            provider.challenge = fields.get("code_challenge");
            assertEquals("S256", fields.get("code_challenge_method"));
            assertEquals("select_account consent", fields.get("prompt"));
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
        private final String calendarId;
        private final List<Map<String, String>> eventListRequests = new ArrayList<>();
        private List<String> eventPages = List.of();
        private Runnable afterEventList = () -> { };
        private String challenge;
        private String loseCreateResponseFor;
        private Runnable afterEventRead = () -> { };
        private int created, updated, deleted, grants, refreshes, calendarRequests;
        private boolean unauthorized;

        private GoogleFixture() {
            this("qa-calendar");
        }

        private GoogleFixture(String calendarId) {
            this.calendarId = calendarId;
        }

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
            calendarRequests++;
            if (unauthorized) return new GoogleCalendar.Exchange(401, "{}");
            String path = request.uri().getPath();
            if (path.endsWith("/primary")) {
                return new GoogleCalendar.Exchange(200, mapper.createObjectNode().put("id", calendarId)
                        .put("timeZone", "America/New_York").toString());
            }
            if (path.endsWith("/primary/events") && "GET".equals(request.method())) {
                assertTrue(request.bodyPublisher().isEmpty() || request.bodyPublisher().orElseThrow().contentLength() == 0);
                int index = eventListRequests.size();
                eventListRequests.add(form(request.uri().getRawQuery()));
                afterEventList.run();
                return new GoogleCalendar.Exchange(200, eventPages.get(index));
            }
            String id = path.substring(path.lastIndexOf('/') + 1);
            return switch (request.method()) {
                case "GET" -> {
                    afterEventRead.run();
                    yield events.containsKey(id) ? new GoogleCalendar.Exchange(200, events.get(id).toString())
                            : new GoogleCalendar.Exchange(404, "{}");
                }
                case "POST" -> {
                    var event = (ObjectNode) mapper.readTree(body(request));
                    String eventId = event.path("id").asText();
                    assertNull(events.put(eventId, event));
                    created++;
                    if (eventId.equals(loseCreateResponseFor)) {
                        loseCreateResponseFor = null;
                        throw new IOException("Simulated connection loss after Google saved the event.");
                    }
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
