package com.studyflow.integrations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TimetableImportTest {
    @TempDir Path directory;

    @Test void expandsSemesterWeeklyUtcUntilAndKeepsOriginalAllDayDates() throws IOException {
        Path timetable = file("""
                BEGIN:VTIMEZONE
                TZID:Asia/Kolkata
                BEGIN:STANDARD
                DTSTART:19700101T000000
                TZOFFSETFROM:+0530
                TZOFFSETTO:+0530
                END:STANDARD
                END:VTIMEZONE
                BEGIN:VEVENT
                UID:weekly-class
                SUMMARY:Morning Class
                LOCATION:Lab 1
                DTSTART;TZID=Asia/Kolkata:20260803T084500
                DTEND;TZID=Asia/Kolkata:20260803T105000
                RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20261130T180000Z
                END:VEVENT
                BEGIN:VEVENT
                UID:one-off-exam
                SUMMARY:Final Exam
                DTSTART;TZID=Asia/Kolkata:20261208T093000
                DTEND;TZID=Asia/Kolkata:20261208T113000
                END:VEVENT
                BEGIN:VEVENT
                UID:assessment-day
                SUMMARY:Assessment
                DTSTART;VALUE=DATE:20261127
                DTEND;VALUE=DATE:20261128
                END:VEVENT
                """);
        var events = TimetableImport.read(timetable, ZoneOffset.UTC);
        assertEquals(20, events.size());
        assertEquals(20, events.stream().map(TimetableImport.Event::id).distinct().count());
        var lessons = events.stream().filter(event -> "Morning Class".equals(event.title())).toList();
        assertEquals(stamp("2026-08-03T03:15"), lessons.getFirst().start());
        assertEquals(stamp("2026-11-30T03:15"), lessons.getLast().start());
        assertEquals(stamp("2026-11-30T05:20"), lessons.getLast().end());
        assertEquals("Lab 1", lessons.getLast().location());
        var holiday = events.stream().filter(TimetableImport.Event::allDay).findFirst().orElseThrow();
        assertEquals(stamp("2026-11-27T00:00"), holiday.start());
        assertEquals(stamp("2026-11-28T00:00"), holiday.end());

        var india = TimetableImport.read(timetable, ZoneId.of("Asia/Kolkata"));
        assertEquals(events.stream().map(TimetableImport.Event::id).toList(),
                india.stream().map(TimetableImport.Event::id).toList());
        assertEquals(stamp("2026-08-03T08:45"), india.getFirst().start());
    }

    @Test void keepsRecurringWallClockTimeAcrossDstAndDoesNotCountANonexistentTime() throws IOException {
        Path calendar = file("""
                BEGIN:VEVENT
                UID:normal
                SUMMARY:Morning
                DTSTART;TZID=America/New_York:20260301T090000
                DURATION:PT1H
                RRULE:FREQ=WEEKLY;COUNT=3
                END:VEVENT
                BEGIN:VEVENT
                UID:early
                SUMMARY:Early
                DTSTART;TZID="America/New_York":20260301T023000
                DURATION:PT30M
                RRULE:FREQ=WEEKLY;COUNT=3
                END:VEVENT
                BEGIN:VEVENT
                UID:weekend
                SUMMARY:One nominal day
                DTSTART;TZID=America/New_York:20260307T090000
                DURATION:P1D
                END:VEVENT
                """);
        var events = TimetableImport.read(calendar, ZoneOffset.UTC);
        assertEquals(List.of(stamp("2026-03-01T14:00"), stamp("2026-03-08T13:00"), stamp("2026-03-15T13:00")),
                times(events, "Morning"));
        assertEquals(List.of(stamp("2026-03-01T07:30"), stamp("2026-03-15T06:30"), stamp("2026-03-22T06:30")),
                times(events, "Early"));
        var overnight = events.stream().filter(event -> "One nominal day".equals(event.title())).findFirst().orElseThrow();
        assertEquals(stamp("2026-03-07T14:00"), overnight.start());
        assertEquals(stamp("2026-03-08T13:00"), overnight.end());
    }

    @Test void appliesExplicitDateExceptionsAndKeepsTheIdOfAMovedClass() throws IOException {
        String master = """
                BEGIN:VEVENT
                UID:workshop
                SUMMARY:Workshop
                DESCRIPTION:Read Part A\\, then Part B.\\nBring notes.
                LOCATION:Room\\; East
                DTSTART:20261102T090000Z
                DTEND:20261102T100000Z
                RRULE:FREQ=WEEKLY;BYDAY=MO;COUNT=3
                EXDATE:20261109T090000Z
                RDATE:20261116T090000Z,20261123T090000Z
                END:VEVENT
                """;
        var original = TimetableImport.read(file(master), ZoneOffset.UTC);
        var exceptions = """
                BEGIN:VEVENT
                UID:workshop
                RECURRENCE-ID:20261102T090000Z
                STATUS:CANCELLED
                END:VEVENT
                BEGIN:VEVENT
                UID:workshop
                RECURRENCE-ID:20261116T090000Z
                SEQUENCE:2
                DTSTART:20261117T140000Z
                SUMMARY:Moved Workshop
                END:VEVENT
                BEGIN:VEVENT
                UID:workshop
                RECURRENCE-ID:20261116T090000Z
                SEQUENCE:1
                DTSTART:20261117T150000Z
                SUMMARY:Previous revision
                END:VEVENT
                """;
        var revised = TimetableImport.read(file(master + master + exceptions), ZoneOffset.UTC);
        assertEquals(2, revised.size());
        assertEquals("Moved Workshop", revised.getFirst().title());
        assertEquals(stamp("2026-11-17T14:00"), revised.getFirst().start());
        assertEquals(stamp("2026-11-17T15:00"), revised.getFirst().end());
        assertEquals(original.get(1).id(), revised.getFirst().id());
        assertEquals("Read Part A, then Part B.\nBring notes.", revised.getFirst().description());
        assertEquals("Room; East", revised.getFirst().location());
        assertEquals(stamp("2026-11-23T09:00"), revised.getLast().start());
    }

    @Test void unfoldsEvenWhenAFoldDividesAUtf8OctetSequence() throws IOException {
        byte[] text = envelope("""
                BEGIN:VEVENT
                UID:utf8
                SUMMARY:Révision
                DTSTART:20261117T090000
                BEGIN:VALARM
                TRIGGER:-PT15M
                ACTION:DISPLAY
                DESCRIPTION:Reminder
                END:VALARM
                END:VEVENT
                """).getBytes(StandardCharsets.UTF_8);
        int first = 0;
        while (text[first] != (byte) 0xc3) first++;
        ByteArrayOutputStream folded = new ByteArrayOutputStream();
        folded.write(text, 0, first + 1);
        folded.writeBytes("\r\n ".getBytes(StandardCharsets.UTF_8));
        folded.write(text, first + 1, text.length - first - 1);
        Path calendar = Files.write(directory.resolve("folded.ics"), folded.toByteArray());
        var utc = TimetableImport.read(calendar, ZoneOffset.UTC).getFirst();
        var india = TimetableImport.read(calendar, ZoneId.of("Asia/Kolkata")).getFirst();
        assertEquals("Révision", utc.title());
        assertEquals(stamp("2026-11-17T09:00"), utc.start());
        assertEquals(utc.start(), utc.end());
        assertEquals(utc, india);
        assertFalse(utc.allDay());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DTEND:20261117T090000Z", "DURATION:PT0S"})
    void acceptsExplicitZeroDurationTimedEvents(String end) throws IOException {
        Path timetable = file("""
                BEGIN:VEVENT
                UID:point
                DTSTART:20261117T090000Z
                """ + end + "\nEND:VEVENT\n");
        var entry = TimetableImport.read(timetable, ZoneOffset.UTC).getFirst();
        assertFalse(entry.allDay());
        assertEquals(stamp("2026-11-17T09:00"), entry.start());
        assertEquals(entry.start(), entry.end());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "RRULE:FREQ=WEEKLY",
            "RRULE:FREQ=MONTHLY;COUNT=4",
            "RRULE:FREQ=WEEKLY;COUNT=4;BYDAY=2MO",
            "RRULE:FREQ=WEEKLY;COUNT=3;UNTIL=20261201T110000Z",
            "RRULE:FREQ=WEEKLY;UNTIL=20501201T110000Z",
            "RDATE;VALUE=PERIOD:20261121T090000Z/PT1H"
    })
    void refusesUnboundedOrUnsupportedRepeatRules(String rule) throws IOException {
        Path timetable = file("""
                BEGIN:VEVENT
                UID:invalid
                DTSTART:20261117T090000Z
                DTEND:20261117T093000Z
                """ + rule + "\nEND:VEVENT\n");
        assertThrows(IOException.class, () -> TimetableImport.read(timetable, ZoneOffset.UTC));
    }

    private static List<LocalDateTime> times(List<TimetableImport.Event> events, String title) {
        return events.stream().filter(event -> title.equals(event.title())).map(TimetableImport.Event::start).toList();
    }

    private Path file(String contents) throws IOException {
        return Files.writeString(Files.createTempFile(directory, "timetable-", ".ics"), envelope(contents));
    }

    private static String envelope(String contents) {
        return "BEGIN:VCALENDAR\nVERSION:2.0\n" + contents + "END:VCALENDAR\n";
    }

    private static LocalDateTime stamp(String value) {
        return LocalDateTime.parse(value);
    }
}
