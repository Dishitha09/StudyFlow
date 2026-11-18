package com.studyflow.config;

import com.studyflow.model.TimetableEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class TimetablePersistenceTest {
    @TempDir Path temp;
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-03T04:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.parse("2026-08-03");

    @Test void importEditsAndRemovalsSurviveReopenAndReimport() throws Exception {
        Path fixture = Files.writeString(temp.resolve("fixture.ics"), """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                UID:sample-weekly
                DTSTART;TZID=Asia/Kolkata:20260803T084500
                DTEND;TZID=Asia/Kolkata:20260803T094500
                SUMMARY:Math class
                LOCATION:Room A
                RRULE:FREQ=WEEKLY;BYDAY=MO;COUNT=3
                END:VEVENT
                END:VCALENDAR
                """);
        Path profile = temp.resolve("profile");
        TimetableEntry edited;
        String deletedId;
        try (var app = StudyFlowContext.open(profile, CLOCK)) {
            var imported = app.timetable().importFile(fixture, TODAY);
            assertEquals(3, imported.added());
            assertEquals(TODAY, imported.showDate());
            var entries = app.timetable().findAll();
            var original = entries.getFirst();
            edited = new TimetableEntry(original.id(), "Rescheduled class", "Bring the handout", "Room B",
                    LocalDateTime.parse("2026-08-04T14:00"), LocalDateTime.parse("2026-08-04T15:00"), false);
            app.timetable().update(edited);
            deletedId = entries.get(1).id();
            app.timetable().delete(deletedId);
            assertTrue(app.taskManager().findAll().isEmpty());
        }
        try (var reopened = StudyFlowContext.open(profile, CLOCK)) {
            var saved = reopened.timetable().findAll();
            assertEquals(2, saved.size());
            assertTrue(saved.contains(edited));
            assertFalse(saved.stream().anyMatch(entry -> entry.id().equals(deletedId)));
            var repeated = reopened.timetable().importFile(fixture, TODAY);
            assertEquals(0, repeated.added());
            assertEquals(3, repeated.alreadyHandled());
            assertEquals(edited.start().toLocalDate(), repeated.showDate());
            assertEquals(saved, reopened.timetable().findAll());
        }
    }

    @Test void timetableCanBeBackedUpAndRestoredIndependently() throws Exception {
        Path fixture = Files.writeString(temp.resolve("day.ics"), """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                UID:sample-day
                DTSTART;VALUE=DATE:20260803
                DTEND;VALUE=DATE:20260804
                SUMMARY:University holiday
                END:VEVENT
                END:VCALENDAR
                """);
        Path profile = temp.resolve("profile");
        TimetableEntry saved;
        try (var app = StudyFlowContext.open(profile, CLOCK)) {
            app.timetable().importFile(fixture, TODAY);
            saved = app.timetable().findAll().getFirst();
            assertTrue(saved.allDay());
        }
        Path backup = StudyFlowContext.backup(profile, "timetable");
        try (var app = StudyFlowContext.open(profile, CLOCK)) {
            app.timetable().delete(saved.id());
            assertTrue(app.timetable().findAll().isEmpty());
        }
        StudyFlowContext.restore(profile, "timetable", backup);
        try (var app = StudyFlowContext.open(profile, CLOCK)) {
            assertEquals(java.util.List.of(saved), app.timetable().findAll());
        }
    }
}
