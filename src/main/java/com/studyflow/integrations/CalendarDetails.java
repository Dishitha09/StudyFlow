package com.studyflow.integrations;

import com.studyflow.model.Subject;
import com.studyflow.model.Task;

final class CalendarDetails {
    private CalendarDetails() { }

    static String description(Task task, Subject subject) {
        if (subject == null) {
            return task.getDescription();
        }
        String label = "Subject: " + subject.getName() + " (" + subject.getSubjectCode() + ")";
        return task.getDescription().isBlank() ? label : label + "\n\n" + task.getDescription();
    }
}
