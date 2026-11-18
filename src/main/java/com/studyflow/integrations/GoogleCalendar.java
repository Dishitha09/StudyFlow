package com.studyflow.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/** Desktop OAuth and event operations for the signed-in primary calendar. */
public final class GoogleCalendar {
    private static final String API = "https://www.googleapis.com/calendar/v3/calendars/primary";
    private static final URI TOKENS = URI.create("https://oauth2.googleapis.com/token");
    private static final String CALLBACK = "/oauth";
    private static final String RECONNECT = "Google authorization expired or was revoked. Connect Google Calendar again.";
    private static final String EVENT_LIST_FIELDS =
            "nextPageToken,items(id,status,summary,start,end,endTimeUnspecified,extendedProperties/private)";
    private static final DateTimeFormatter OFFSET_TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter EVENT_DATE_TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm");
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;
    private final Transport transport;
    private final Browser browser;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private final AtomicReference<Attempt> pending = new AtomicReference<>();
    private final AtomicReference<Session> session = new AtomicReference<>();

    public GoogleCalendar() {
        this(System.getenv("STUDYFLOW_GOOGLE_CLIENT_ID"), System.getenv("STUDYFLOW_GOOGLE_CLIENT_SECRET"),
                Clock.systemUTC(), new JdkTransport(), GoogleCalendar::openBrowser);
    }

    GoogleCalendar(String clientId, String clientSecret, Clock clock, Transport transport, Browser browser) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = Objects.requireNonNull(clock);
        this.transport = Objects.requireNonNull(transport);
        this.browser = Objects.requireNonNull(browser);
    }

    public boolean configured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public boolean connected() {
        return session.get() != null;
    }

    String connectedCalendarId() {
        Session active = session.get();
        return active == null ? null : active.calendarIdentity();
    }

    public void connect() throws IOException {
        if (!configured()) {
            throw new IOException("Set the Google Desktop OAuth client ID and secret before connecting.");
        }
        Attempt attempt = new Attempt();
        if (!pending.compareAndSet(null, attempt)) {
            throw new IOException("Google sign-in is already in progress.");
        }
        try {
            String verifier = randomString();
            AuthorizationCode grant = authorize(verifier, attempt);
            attempt.requireActive();
            Tokens tokens = exchangeToken(Map.of("client_id", clientId, "client_secret", clientSecret,
                    "code", grant.value(), "redirect_uri", grant.redirect(), "code_verifier", verifier,
                    "grant_type", "authorization_code"), null);
            attempt.requireActive();
            String identity = calendarInfo(sendEvent(tokens, "GET", "", null)).identity();
            attempt.publish(session, new Session(tokens, identity));
        } finally {
            pending.compareAndSet(attempt, null);
        }
    }

    /** Cancels the pending browser sign-in without waiting for the integration worker. */
    public boolean cancelConnect() {
        Attempt attempt = pending.get();
        return attempt != null && attempt.cancel();
    }

    public void disconnect() {
        cancelConnect();
        session.set(null);
    }

    private AuthorizationCode authorize(String verifier, Attempt attempt) throws IOException {
        String state = randomString();
        HttpServer listener = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String redirect = "http://127.0.0.1:" + listener.getAddress().getPort() + CALLBACK;
        listener.createContext(CALLBACK, exchange -> takeCallback(exchange, state, attempt));
        try {
            listener.start();
            attempt.requireActive();
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash(verifier));
            Map<String, String> arguments = Map.of("client_id", clientId, "redirect_uri", redirect,
                    "response_type", "code", "state", state, "scope",
                    "https://www.googleapis.com/auth/calendar.events.owned "
                            + "https://www.googleapis.com/auth/calendar.calendars.readonly",
                    "code_challenge", challenge, "code_challenge_method", "S256", "access_type", "offline",
                    "prompt", "select_account consent");
            browser.open(URI.create("https://accounts.google.com/o/oauth2/v2/auth?" + form(arguments)));
            return new AuthorizationCode(attempt.response.get(90, TimeUnit.SECONDS), redirect);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Google sign-in was interrupted.", error);
        } catch (TimeoutException error) {
            throw new IOException("Google sign-in timed out.", error);
        } catch (ExecutionException error) {
            if (error.getCause() instanceof IOException authorizationError) {
                throw authorizationError;
            }
            throw new IOException("Google sign-in was not approved.", error);
        } finally {
            listener.stop(0);
        }
    }

    private static void takeCallback(HttpExchange exchange, String state, Attempt attempt) throws IOException {
        try (exchange) {
            Map<String, String> params = query(exchange.getRequestURI().getRawQuery());
            boolean matches = "GET".equals(exchange.getRequestMethod())
                    && CALLBACK.equals(exchange.getRequestURI().getPath()) && state.equals(params.get("state"));
            String message = matches ? "Return to StudyFlow to finish connecting."
                    : "This response does not match the active StudyFlow authorization.";
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(matches ? 200 : 400, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {
                if (matches) {
                    String code = params.get("code");
                    if (code == null || code.isBlank() || params.containsKey("error")) {
                        attempt.response.completeExceptionally(new IOException("Google sign-in was not approved."));
                    } else {
                        attempt.response.complete(code);
                    }
                }
            }
        }
    }

    public String calendarId() throws IOException {
        return currentCalendar().identity();
    }

    private CalendarInfo currentCalendar() throws IOException {
        CalendarInfo calendar = calendarInfo(eventRequest("GET", "?fields=id,timeZone", null));
        if (!calendar.identity().equals(connectedCalendarId())) {
            throw new IOException("Google Calendar changed. Disconnect and reconnect the intended account.");
        }
        return calendar;
    }

    private CalendarInfo calendarInfo(Exchange response) throws IOException {
        requireSuccess(response);
        JsonNode details = readObject(response.body(), "Google returned unreadable primary calendar details.");
        JsonNode identity = details.path("id");
        if (!identity.isTextual() || identity.asText().isBlank()) {
            throw new IOException("Google returned no primary calendar ID.");
        }
        return new CalendarInfo(HexFormat.of().formatHex(hash(identity.asText())), details.path("timeZone").asText(""));
    }

    /** Fetches read-only events overlapping the given month in the primary calendar's own time zone. */
    public List<Event> listEvents(YearMonth month) throws IOException {
        return listEvents(month, () -> false);
    }

    public List<Event> listEvents(YearMonth month, BooleanSupplier cancelled) throws IOException {
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(cancelled, "cancelled");
        if (cancelled.getAsBoolean()) return List.of();
        if (month.getYear() < 1 || month.compareTo(YearMonth.of(9999, 12)) >= 0) {
            throw new IllegalArgumentException("Choose a Google Calendar month with four-digit start and end years.");
        }
        CalendarInfo calendar = currentCalendar();
        ZoneId zone;
        try {
            zone = ZoneId.of(calendar.timeZone());
        } catch (DateTimeException invalid) {
            throw new IOException("Google returned an invalid primary calendar time zone.");
        }
        LocalDate from = month.atDay(1);
        LocalDate through = month.plusMonths(1).atDay(1);
        Map<String, String> parameters = new HashMap<>(Map.of(
                "timeMin", OFFSET_TIMESTAMP.format(from.atStartOfDay(zone)),
                "timeMax", OFFSET_TIMESTAMP.format(through.atStartOfDay(zone)),
                "timeZone", zone.getId(), "singleEvents", "true", "orderBy", "startTime",
                "showDeleted", "false", "maxResults", "250", "fields", EVENT_LIST_FIELDS));
        var events = new LinkedHashMap<String, Event>();
        var fetchedTokens = new HashSet<String>();
        String next;
        do {
            if (cancelled.getAsBoolean()) return List.of();
            if (!calendar.identity().equals(connectedCalendarId())) {
                throw new IOException("The Google Calendar session changed. Refresh the month again.");
            }
            Exchange response = eventRequest("GET", "/events?" + form(parameters), null);
            if (cancelled.getAsBoolean()) return List.of();
            requireSuccess(response);
            JsonNode page = readObject(response.body(), "Google returned an unreadable calendar event list.");
            JsonNode items = page.path("items");
            if (!items.isMissingNode() && !items.isArray()) {
                throw new IOException("Google returned an unreadable calendar event list.");
            }
            for (JsonNode item : items) {
                if ("cancelled".equals(item.path("status").asText())
                        || !item.path("extendedProperties").path("private").path("studyflowTaskId").asText("").isBlank()) {
                    continue;
                }
                Event event = parseEvent(item, zone);
                if (event.startDate().isBefore(through) && event.endDateExclusive().isAfter(from)) {
                    events.putIfAbsent(event.id(), event);
                }
            }
            next = page.path("nextPageToken").asText("");
            if (!next.isBlank()) {
                if (!fetchedTokens.add(next)) {
                    throw new IOException("Google repeated a calendar event page. Try refreshing the month.");
                }
                parameters.put("pageToken", next);
            }
        } while (!next.isBlank());
        return cancelled.getAsBoolean() ? List.of() : List.copyOf(events.values());
    }

    private static Event parseEvent(JsonNode item, ZoneId zone) throws IOException {
        String id = item.path("id").asText("");
        if (!item.path("id").isTextual() || id.isBlank()) {
            throw new IOException("Google returned a calendar event without an ID.");
        }
        String title = item.path("summary").asText("").strip();
        if (title.isEmpty()) title = "Untitled event";
        JsonNode start = item.path("start");
        JsonNode end = item.path("end");
        try {
            if (start.path("date").isTextual()) {
                LocalDate first = LocalDate.parse(start.path("date").asText());
                LocalDate after = LocalDate.parse(end.path("date").asText());
                if (!after.isAfter(first)) throw new DateTimeException("Nonpositive all-day date range");
                return new Event(id, title, first, after, "");
            }

            ZonedDateTime first = eventTime(start, zone);
            LocalDate day = first.toLocalDate();
            if (item.path("endTimeUnspecified").asBoolean(false)) {
                return new Event(id, title, day, day.plusDays(1), EVENT_TIME.format(first));
            }
            ZonedDateTime last = eventTime(end, zone);
            if (last.isBefore(first)) throw new DateTimeException("End precedes start");
            if (last.isEqual(first)) {
                return new Event(id, title, day, day.plusDays(1), EVENT_TIME.format(first));
            }
            LocalDate lastDay = last.toLocalDate();
            LocalDate after = last.toLocalTime().equals(LocalTime.MIDNIGHT) ? lastDay : lastDay.plusDays(1);
            DateTimeFormatter label = day.equals(lastDay) ? EVENT_TIME : EVENT_DATE_TIME;
            return new Event(id, title, day, after, label.format(first) + " – " + label.format(last));
        } catch (DateTimeException invalid) {
            throw new IOException("Google returned a calendar event with invalid dates or times.");
        }
    }

    private static ZonedDateTime eventTime(JsonNode data, ZoneId calendarZone) {
        String value = data.path("dateTime").asText();
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(calendarZone);
        } catch (DateTimeException offsetMissingOrInvalid) {
            JsonNode zone = data.path("timeZone");
            if (!zone.isTextual()) throw offsetMissingOrInvalid;
            return LocalDateTime.parse(value).atZone(ZoneId.of(zone.asText())).withZoneSameInstant(calendarZone);
        }
    }

    public record Event(String id, String title, LocalDate startDate, LocalDate endDateExclusive, String timeLabel) { }

    private record CalendarInfo(String identity, String timeZone) { }

    public String upsert(Task task, Subject subject) throws IOException {
        return upsert(task, subject, () -> false);
    }

    // Null means cancellation stopped the operation before it could write to Google.
    String upsert(Task task, Subject subject, BooleanSupplier cancelled) throws IOException {
        if (cancelled.getAsBoolean()) return null;
        if (task.getDueDate() == null) {
            throw new IllegalArgumentException("Only tasks with deadlines can sync.");
        }
        String id = eventId(task.getId());
        Map<String, Object> fields = Map.of(
                "summary", task.getTitle(), "description", CalendarDetails.description(task, subject),
                "start", Map.of("date", task.getDueDate().toString()),
                "end", Map.of("date", CalendarDetails.exclusiveEnd(task.getDueDate()).toString()));
        Exchange response = eventRequest("GET", "/events/" + id, null);
        if (missing(response)) {
            Map<String, Object> created = new HashMap<>(fields);
            created.put("id", id);
            created.put("extendedProperties", Map.of("private", Map.of("studyflowTaskId", task.getId())));
            if (cancelled.getAsBoolean()) return null;
            response = eventRequest("POST", "/events", mapper.writeValueAsString(created));
            if (response.statusCode() != 409) {
                requireSuccess(response);
                return id;
            }
            if (cancelled.getAsBoolean()) return null;
            response = eventRequest("GET", "/events/" + id, null);
        }
        if (cancelled.getAsBoolean()) return null;
        requireSuccess(response);
        verifyOwnership(task.getId(), response.body());
        if (cancelled.getAsBoolean()) return null;
        requireSuccess(eventRequest("PATCH", "/events/" + id, mapper.writeValueAsString(fields)));
        return id;
    }

    public void delete(String eventId, String taskId) throws IOException {
        if (eventId == null || !eventId.matches("[a-v0-9]{5,1024}")) {
            throw new IllegalArgumentException("Invalid Google event ID.");
        }
        Exchange existing = eventRequest("GET", "/events/" + eventId, null);
        if (missing(existing)) {
            return;
        }
        requireSuccess(existing);
        verifyOwnership(taskId, existing.body());
        Exchange response = eventRequest("DELETE", "/events/" + eventId, null);
        if (!missing(response)) {
            requireSuccess(response);
        }
    }

    private void verifyOwnership(String taskId, String payload) throws IOException {
        JsonNode event = readObject(payload, "Google returned unreadable calendar event details.");
        JsonNode owner = event.path("extendedProperties").path("private").path("studyflowTaskId");
        if (!owner.isTextual() || !taskId.equals(owner.asText())) {
            throw new IOException("This Google event does not belong to the selected StudyFlow task.");
        }
    }

    public static String eventId(String taskId) {
        return HexFormat.of().formatHex(hash(taskId));
    }

    private Exchange eventRequest(String method, String path, String body) throws IOException {
        Session active = session.get();
        if (active == null) {
            throw new IOException("Connect Google Calendar first.");
        }
        Instant now = clock.instant();
        boolean renewed = false;
        if (!active.tokens().expires().isAfter(now.plusSeconds(30)) && active.tokens().refresh() != null) {
            active = refresh(active);
            renewed = true;
        } else if (!active.tokens().expires().isAfter(now)) {
            throw revoke(active);
        }

        Exchange response = sendForSession(active, method, path, body);
        if (response.statusCode() == 401 && !renewed && active.tokens().refresh() != null) {
            active = refresh(active);
            response = sendForSession(active, method, path, body);
        }
        if (response.statusCode() == 401) {
            throw revoke(active);
        }
        return response;
    }

    private Exchange sendForSession(Session active, String method, String path, String body) throws IOException {
        checkSession(active);
        Exchange response = sendEvent(active.tokens(), method, path, body);
        checkSession(active);
        return response;
    }

    private void checkSession(Session expected) throws IOException {
        if (session.get() != expected) {
            throw new IOException("The Google Calendar session changed. Try the operation again.");
        }
    }

    private IOException revoke(Session active) {
        session.compareAndSet(active, null);
        return new IOException(RECONNECT);
    }

    private Exchange sendEvent(Tokens tokens, String method, String path, String body) throws IOException {
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        return send(HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + tokens.access()).header("Content-Type", "application/json")
                .method(method, publisher).build());
    }

    private Session refresh(Session active) throws IOException {
        checkSession(active);
        Tokens tokens;
        try {
            tokens = exchangeToken(Map.of("client_id", clientId, "client_secret", clientSecret,
                    "refresh_token", active.tokens().refresh(), "grant_type", "refresh_token"), active.tokens().refresh());
        } catch (RejectedGrant error) {
            throw revoke(active);
        }
        Session renewed = new Session(tokens, active.calendarIdentity());
        if (!session.compareAndSet(active, renewed)) {
            throw new IOException("The Google Calendar session changed. Try the operation again.");
        }
        return renewed;
    }

    private Tokens exchangeToken(Map<String, String> params, String previousRefresh) throws IOException {
        Exchange response = send(HttpRequest.newBuilder(TOKENS).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(params))).build());
        if (response.statusCode() == 400 || response.statusCode() == 401 || response.statusCode() == 403) {
            throw new RejectedGrant();
        }
        requireSuccess(response);
        JsonNode data = readObject(response.body(), "Google returned unreadable authorization details.");
        String access = data.path("access_token").asText("");
        if (!data.path("access_token").isTextual() || access.isBlank()
                || access.chars().anyMatch(ch -> ch < 0x21 || ch > 0x7e)) {
            throw new IOException("Google returned no usable access token.");
        }
        String refresh = data.path("refresh_token").asText("");
        if (refresh.isBlank()) {
            refresh = previousRefresh;
        }
        long expiresIn = Math.clamp(data.path("expires_in").asLong(3_600), 1L, 86_400L);
        return new Tokens(access, refresh, clock.instant().plusSeconds(expiresIn));
    }

    private JsonNode readObject(String payload, String message) throws IOException {
        try {
            JsonNode node = mapper.readTree(payload);
            if (node != null && node.isObject()) {
                return node;
            }
        } catch (IOException malformed) {
            // Provider response bodies can contain secrets and do not belong in diagnostics.
        }
        throw new IOException(message);
    }

    private Exchange send(HttpRequest request) throws IOException {
        try {
            return transport.send(request);
        } catch (HttpTimeoutException error) {
            throw new IOException("The Google Calendar request timed out. Check your connection and retry.");
        } catch (UncheckedIOException | IOException error) {
            throw new IOException("Could not reach Google Calendar. Check your connection and retry.");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Google Calendar request interrupted.");
        }
    }

    private static boolean missing(Exchange response) {
        return response.statusCode() == 404 || response.statusCode() == 410;
    }

    private static void requireSuccess(Exchange response) throws IOException {
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
            try {
                for (String item : input.split("&")) {
                    String[] entry = item.split("=", 2);
                    if (entry.length == 2) {
                        String key = URLDecoder.decode(entry[0], StandardCharsets.UTF_8);
                        if (values.putIfAbsent(key, URLDecoder.decode(entry[1], StandardCharsets.UTF_8)) != null) {
                            return Map.of();
                        }
                    }
                }
            } catch (IllegalArgumentException malformed) {
                return Map.of();
            }
        }
        return values;
    }

    private static void openBrowser(URI uri) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("Google sign-in needs a system browser.");
        }
        Desktop.getDesktop().browse(uri);
    }

    @FunctionalInterface
    interface Transport {
        Exchange send(HttpRequest request) throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface Browser {
        void open(URI authorizationUrl) throws IOException;
    }

    record Exchange(int statusCode, String body) { }

    private record Tokens(String access, String refresh, Instant expires) { }
    private record Session(Tokens tokens, String calendarIdentity) { }
    private record AuthorizationCode(String value, String redirect) { }

    private static final class RejectedGrant extends IOException {
        private RejectedGrant() { super(RECONNECT); }
    }

    private static final class Attempt {
        private final CompletableFuture<String> response = new CompletableFuture<>();
        private boolean cancelled;
        private boolean published;

        private synchronized boolean cancel() {
            if (cancelled || published) {
                return false;
            }
            cancelled = true;
            response.completeExceptionally(new IOException("Google sign-in was cancelled."));
            return true;
        }

        private synchronized void requireActive() throws IOException {
            if (cancelled) {
                throw new IOException("Google sign-in was cancelled.");
            }
        }

        private synchronized void publish(AtomicReference<Session> session, Session value) throws IOException {
            requireActive();
            session.set(value);
            published = true;
        }
    }

    private static final class JdkTransport implements Transport {
        private HttpClient client;

        @Override
        public Exchange send(HttpRequest request) throws IOException, InterruptedException {
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            return new Exchange(response.statusCode(), response.body());
        }

        private synchronized HttpClient client() {
            if (client == null) {
                client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
            }
            return client;
        }
    }
}
