package com.studyflow.integrations;

import com.studyflow.model.GeneralTask;
import com.studyflow.model.Priority;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CalendarAndSpotifyTest {
    private static final Instant GENERATED = Instant.parse("2026-11-14T09:10:11Z");
    private static final LocalDate DEADLINE = LocalDate.of(2026, 11, 30);

    @TempDir
    Path directory;

    @Test
    void exportsUtf8WithCrlfAndFoldsLongLinesWithoutSplittingUnicode() throws IOException {
        Path calendar = directory.resolve("deadlines.ics");
        String title = "café 学習 📚 ".repeat(12) + "review";
        Task dated = new GeneralTask("task-1", title, "Read, revise; C:\\notes\r\nNext chapter", null,
                DEADLINE.minusDays(3), DEADLINE, Priority.HIGH, TaskStatus.TODO);
        Task undated = new GeneralTask("task-2", "Undated", DEADLINE, Priority.LOW);

        CalendarExport.write(calendar, List.of(dated, undated), GENERATED, Map.of());

        String contents = Files.readString(calendar, StandardCharsets.UTF_8);
        assertTrue(contents.endsWith("END:VCALENDAR\r\n"));
        assertFalse(contents.replace("\r\n", "").matches("(?s).*[\r\n].*"));
        List<String> physicalLines = List.of(contents.split("\r\n"));
        assertTrue(physicalLines.stream().anyMatch(line -> line.startsWith(" ")));
        for (String line : physicalLines) {
            assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75, line);
        }
        List<String> unfolded = List.of(contents.replace("\r\n ", "").split("\r\n"));
        assertTrue(unfolded.contains("UID:task-1@studyflow"));
        assertTrue(unfolded.contains("DTSTAMP:20261114T091011Z"));
        assertTrue(unfolded.contains("DTSTART;VALUE=DATE:20261130"));
        assertTrue(unfolded.contains("DTEND;VALUE=DATE:20261201"));
        assertTrue(unfolded.contains("SUMMARY:" + title));
        assertTrue(unfolded.contains("DESCRIPTION:Read\\, revise\\; C:\\\\notes\\nNext chapter"));
        assertEquals(1, unfolded.stream().filter("BEGIN:VEVENT"::equals).count());
    }

    @Test
    void rejectsExportWithoutDatedTasksBeforeWritingAFile() {
        Path calendar = directory.resolve("deadlines.ics");
        Task undated = new GeneralTask("task-1", "Undated", DEADLINE, Priority.LOW);

        assertThrows(IllegalArgumentException.class,
                () -> CalendarExport.write(calendar, List.of(undated), GENERATED, Map.of()));
        assertFalse(Files.exists(calendar));
    }

    @Test
    void exportsUpdatedSubjectWithoutChangingTheEventIdentity() throws IOException {
        Path calendar = directory.resolve("subjects.ics");
        Subject subject = new Subject("course-1", "Algebra", "MAT101", "", List.of());
        Task task = new GeneralTask("task-1", "Problem set", "Read chapter 3", subject.getId(),
                DEADLINE.minusDays(1), DEADLINE, Priority.HIGH, TaskStatus.TODO);

        CalendarExport.write(calendar, List.of(task), GENERATED, Map.of(subject.getId(), subject));
        assertTrue(Files.readString(calendar).contains("DESCRIPTION:Subject: Algebra (MAT101)"));

        subject.setName("Linear Algebra");
        CalendarExport.write(calendar, List.of(task), GENERATED, Map.of(subject.getId(), subject));
        String updated = Files.readString(calendar);
        assertTrue(updated.contains("UID:task-1@studyflow"));
        assertTrue(updated.contains("DESCRIPTION:Subject: Linear Algebra (MAT101)\\n\\nRead chapter 3"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"playlist", "album", "artist", "track"})
    void canonicalizesOnlySupportedSpotifyResourceLinks(String kind) {
        assertEquals(URI.create("https://open.spotify.com/" + kind + "/12Ab34"),
                LocalIntegrations.spotify("  HTTPS://OPEN.SPOTIFY.COM/" + kind + "/12Ab34/?si=share#preview  "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://open.spotify.com/track/12Ab34", "https://open.spotify.com.example.org/track/12Ab34",
            "https://open.spotify.com@other.example/track/12Ab34", "https://anyone@open.spotify.com/track/12Ab34",
            "https://open.spotify.com:443/track/12Ab34", "https://open.spotify.com/episode/12Ab34",
            "https://open.spotify.com/playlist/12Ab34/extra", "https://open.spotify.com/track/12%2FAb34"})
    void rejectsLinksOutsideTheSpotifyWhitelist(String url) {
        assertThrows(IllegalArgumentException.class, () -> LocalIntegrations.spotify(url));
    }

    @Test
    void derivesStableDistinctGoogleEventIdsFromTaskIds() {
        String eventId = GoogleCalendar.eventId("abc");

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", eventId);
        assertEquals(eventId, GoogleCalendar.eventId(new String(new char[]{'a', 'b', 'c'})));
        assertNotEquals(eventId, GoogleCalendar.eventId("abcd"));
        assertTrue(GoogleCalendar.eventId("学習/📚").matches("[a-v0-9]{5,1024}"));
    }
}
