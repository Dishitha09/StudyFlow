package com.studyflow.integrations;

import com.studyflow.interfaces.Identifiable;
import com.studyflow.interfaces.Repository;
import com.studyflow.model.Task;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

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

    public void export(Path path, List<Task> tasks) throws IOException {
        CalendarExport.write(path, tasks, clock.instant());
    }

    public void sync(List<Task> tasks) throws IOException {
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("Select at least one task with a deadline.");
        }
        String calendar = google.calendarId();
        for (Task task : tasks) {
            String event = google.upsert(task);
            Link mapping = new Link(mappingKey(task.getId(), calendar), "google", event);
            if (links.existsById(mapping.id())) {
                links.update(mapping);
            } else {
                links.insert(mapping);
            }
        }
    }

    public void deleteEvent(String taskId) throws IOException {
        String calendar = google.calendarId();
        String key = mappingKey(taskId, calendar);
        google.delete(links.findById(key).map(Link::value).orElseGet(() -> GoogleCalendar.eventId(taskId)), taskId);
        links.delete(key);
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

    public record Link(String id, String type, String value) implements Identifiable {
        public Link {
            if (id == null || id.isBlank() || !List.of("google", "spotify").contains(type)
                    || value == null || value.isBlank()) {
                throw new IllegalArgumentException("Invalid integration link.");
            }
        }
        @Override public String getId() { return id; }
    }
}
