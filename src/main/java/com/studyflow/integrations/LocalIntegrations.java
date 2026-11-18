package com.studyflow.integrations;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.studyflow.interfaces.Identifiable;
import com.studyflow.interfaces.Repository;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Profile-backed integration preferences and optional desktop actions. */
public final class LocalIntegrations {
    private final Repository<Link> links;
    private final GoogleCalendar google;
    private final Clock clock;

    public LocalIntegrations(Repository<Link> links, GoogleCalendar google, Clock clock) {
        this.links = links;
        this.google = google;
        this.clock = clock;
    }

    public GoogleCalendar google() { return google; }

    public List<String> playlists() {
        return links.findAll().stream().filter(link -> link.type().equals("spotify")).map(Link::value).toList();
    }

    public void addPlaylist(String url) {
        String safe = spotify(url).toString();
        if (!playlists().contains(safe)) {
            links.insert(new Link(UUID.randomUUID().toString(), "spotify", safe));
        }
    }

    public void removePlaylist(String url) {
        links.findAll().stream().filter(link -> link.type().equals("spotify") && link.value().equals(url))
                .findFirst().ifPresent(link -> links.delete(link.id()));
    }

    public void openPlaylist(String url) throws IOException {
        Desktop.getDesktop().browse(spotify(url));
    }

    public void export(Path path, List<Task> tasks, List<Subject> subjects) throws IOException {
        CalendarExport.write(path, tasks, clock.instant(), subjectIndex(subjects));
    }

    public void sync(List<Task> tasks, List<Subject> subjects) throws IOException {
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("Select at least one task with a deadline.");
        }
        // Check storage before sending an update that otherwise could not be recorded locally.
        Map<String, Link> existing = linkIndex();
        String calendar = google.calendarId();
        Map<String, Subject> byId = subjectIndex(subjects);
        for (Task task : tasks) {
            Subject subject = task.getSubjectId() == null ? null : byId.get(task.getSubjectId());
            String key = mappingKey(task.getId(), calendar);
            syncEvent(task, subject, key, signature(task, subject), existing.containsKey(key), () -> false);
        }
    }

    /** Called on the calendar worker so sign-in, manual actions and automatic writes are ordered. */
    public int syncAutomatically(boolean enabled, List<Task> tasks, List<Subject> subjects,
                                 BooleanSupplier cancelled) throws IOException {
        String calendar = google.connectedCalendarId();
        if (!enabled || calendar == null || cancelled.getAsBoolean()) {
            return 0;
        }
        Map<String, Link> existing = linkIndex();
        Map<String, Subject> byId = subjectIndex(subjects);
        int synced = 0;
        int invalidDates = 0;
        for (Task task : tasks) {
            if (cancelled.getAsBoolean()) break;
            if (task.getDueDate() == null) continue;
            String key = mappingKey(task.getId(), calendar);
            Link previous = existing.get(key);
            if (previous != null && "google-removed".equals(previous.type())) continue;
            Subject subject = task.getSubjectId() == null ? null : byId.get(task.getSubjectId());
            String signature = signature(task, subject);
            if (previous != null && signature.equals(previous.syncedSignature())) continue;
            try {
                CalendarDetails.exclusiveEnd(task.getDueDate());
            } catch (IllegalArgumentException invalidDate) {
                invalidDates++;
                continue;
            }
            if (synced == 0 && !calendar.equals(google.calendarId())) {
                throw new IOException("Google Calendar changed. Disconnect and reconnect the intended account.");
            }
            if (!syncEvent(task, subject, key, signature, previous != null, cancelled)) break;
            synced++;
        }
        if (invalidDates > 0 && !cancelled.getAsBoolean()) {
            throw new IllegalArgumentException("Skipped " + invalidDates
                    + (invalidDates == 1 ? " task: " : " tasks: ")
                    + "Google Calendar dates need a four-digit year and a valid following day.");
        }
        return synced;
    }

    private Map<String, Link> linkIndex() {
        return links.findAll().stream().collect(Collectors.toMap(Link::id, Function.identity()));
    }

    private boolean syncEvent(Task task, Subject subject, String key, String signature, boolean mapped,
                              BooleanSupplier cancelled) throws IOException {
        String event = google.upsert(task, subject, cancelled);
        if (event == null) return false;
        saveLink(new Link(key, "google", event, signature), mapped);
        return true;
    }

    private void saveLink(Link link, boolean exists) {
        if (exists) links.update(link);
        else links.insert(link);
    }

    private static String signature(Task task, Subject subject) {
        String title = task.getTitle();
        String description = CalendarDetails.description(task, subject);
        // Length prefix preserves boundaries even when titles or descriptions contain separators.
        String contents = title.length() + ":" + title + task.getDueDate() + ":" + description;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(contents.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public Set<String> linkedCalendarTasks() {
        String calendar = google.connectedCalendarId();
        if (calendar == null) {
            return Set.of();
        }
        String prefix = mappingKey("", calendar);
        return links.findAll().stream().filter(link -> "google".equals(link.type()) && link.id().startsWith(prefix))
                .map(link -> link.id().substring(prefix.length())).collect(Collectors.toUnmodifiableSet());
    }

    private static Map<String, Subject> subjectIndex(List<Subject> subjects) {
        return subjects.stream().collect(Collectors.toMap(Subject::getId, Function.identity()));
    }

    public void deleteEvent(String taskId) throws IOException {
        String calendar = google.calendarId();
        String key = mappingKey(taskId, calendar);
        var previous = links.findById(key);
        String event = previous.map(Link::value).orElseGet(() -> GoogleCalendar.eventId(taskId));
        google.delete(event, taskId);
        // Keep the opt-out for this task and account so auto-sync doesn't undo the user's removal.
        saveLink(new Link(key, "google-removed", event), previous.isPresent());
    }

    private static String mappingKey(String taskId, String account) {
        return "google:" + account + ":" + taskId;
    }

    public static URI spotify(String url) {
        URI uri = URI.create(url.strip());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"open.spotify.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getPath() == null
                || !uri.getPath().matches("/(?:playlist|album|artist|track)/[A-Za-z0-9]+/?")) {
            throw new IllegalArgumentException("Enter an https://open.spotify.com playlist, album, artist or track link.");
        }
        return URI.create("https://open.spotify.com" + uri.getPath().replaceAll("/$", ""));
    }

    public record Link(String id, String type, String value,
                       @JsonInclude(JsonInclude.Include.NON_NULL) String syncedSignature) implements Identifiable {
        public Link(String id, String type, String value) {
            this(id, type, value, null);
        }

        public Link {
            if (id == null || id.isBlank() || !List.of("google", "google-removed", "spotify").contains(type)
                    || value == null || value.isBlank() || (syncedSignature != null
                    && (!"google".equals(type) || !syncedSignature.matches("[0-9a-f]{64}")))) {
                throw new IllegalArgumentException("Invalid integration link.");
            }
        }
        @Override public String getId() { return id; }
    }
}
