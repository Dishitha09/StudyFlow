package com.studyflow.integrations;

import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Exports the selected task deadlines as all-day iCalendar events. */
public final class CalendarExport {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private CalendarExport() { }

    public static void write(Path path, List<Task> tasks, Instant generatedAt, Map<String, Subject> subjects)
            throws IOException {
        StringBuilder output = new StringBuilder();
        for (String header : List.of("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//StudyFlow//Deadline Export//EN",
                "CALSCALE:GREGORIAN")) {
            append(output, header);
        }
        int count = 0;
        for (Task task : tasks) {
            if (task.getDueDate() != null) {
                count++;
                append(output, "BEGIN:VEVENT");
                append(output, "UID:" + escape(task.getId()) + "@studyflow");
                append(output, "DTSTAMP:" + UTC.format(generatedAt));
                append(output, "DTSTART;VALUE=DATE:" + task.getDueDate().format(DateTimeFormatter.BASIC_ISO_DATE));
                append(output, "DTEND;VALUE=DATE:" + CalendarDetails.exclusiveEnd(task.getDueDate())
                        .format(DateTimeFormatter.BASIC_ISO_DATE));
                append(output, "SUMMARY:" + escape(task.getTitle()));
                Subject subject = task.getSubjectId() == null ? null : subjects.get(task.getSubjectId());
                append(output, "DESCRIPTION:" + escape(CalendarDetails.description(task, subject)));
                append(output, "END:VEVENT");
            }
        }
        if (count == 0) {
            throw new IllegalArgumentException("Select one or more tasks with deadlines.");
        }
        append(output, "END:VCALENDAR");
        Files.writeString(path, output, StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\r\n", "\n").replace("\r", "\n")
                .replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;");
    }

    private static void append(StringBuilder target, String line) {
        int length = 0;
        for (int offset = 0; offset < line.length();) {
            int code = line.codePointAt(offset);
            String unit = Character.toString(code);
            int bytes = unit.getBytes(StandardCharsets.UTF_8).length;
            if (length + bytes > 75) {
                target.append("\r\n ");
                length = 1;
            }
            target.append(unit);
            length += bytes;
            offset += Character.charCount(code);
        }
        target.append("\r\n");
    }
}
