package com.studyflow.model;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/** One editable occurrence on the local timetable. End is exclusive, including for all-day entries. */
public record TimetableEntry(String id, String title, String description, String location,
                             LocalDateTime start, LocalDateTime end, boolean allDay) {
    public TimetableEntry {
        id = text(id, "Timetable entry ID", 256);
        title = text(title, "Title", 512);
        description = optionalText(description, 20_000);
        location = optionalText(location, 1000);
        Objects.requireNonNull(start, "Start time");
        Objects.requireNonNull(end, "End time");
        if (end.isBefore(start) || (allDay && end.equals(start))) {
            throw new IllegalArgumentException(allDay ? "End date must be after start." : "End cannot be before start.");
        }
        if (start.getYear() < 1 || end.getYear() > 9999) {
            throw new IllegalArgumentException("Timetable dates need a four-digit year.");
        }
        if (allDay && (!start.toLocalTime().equals(LocalTime.MIDNIGHT)
                || !end.toLocalTime().equals(LocalTime.MIDNIGHT))) {
            throw new IllegalArgumentException("All-day entries must use whole dates.");
        }
    }

    private static String text(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(name + " is required and must be at most " + max + " characters.");
        }
        return value.strip();
    }

    private static String optionalText(String value, int max) {
        if (value == null) return "";
        if (value.length() > max) throw new IllegalArgumentException("Timetable details are too long.");
        return value.strip();
    }
}
