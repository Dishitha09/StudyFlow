package com.studyflow.integrations;

import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import java.time.LocalDate;

final class CalendarDetails {
    private CalendarDetails() { }

    static LocalDate exclusiveEnd(LocalDate start) {
        if (start.getYear() < 1 || start.isAfter(LocalDate.of(9999, 12, 30))) {
            throw new IllegalArgumentException("Calendar dates need a four-digit year and a valid following day.");
        }
        return start.plusDays(1);
    }

    static String description(Task task, Subject subject) {
        if (subject == null) {
            return task.getDescription();
        }
        String label = "Subject: " + subject.getName() + " (" + subject.getSubjectCode() + ")";
        return task.getDescription().isBlank() ? label : label + "\n\n" + task.getDescription();
    }
}
