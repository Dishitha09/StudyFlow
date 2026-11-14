package com.studyflow.model;

import com.studyflow.interfaces.Identifiable;
import com.studyflow.utils.ValidationUtils;
import java.time.LocalDate;

/** One class outcome for an existing subject. */
public record AttendanceEntry(String id, String subjectId, LocalDate date, boolean attended)
        implements Identifiable {
    public AttendanceEntry {
        id = ValidationUtils.requiredText(id, "Attendance ID");
        subjectId = ValidationUtils.requiredText(subjectId, "Subject ID");
        ValidationUtils.required(date, "Class date");
    }

    @Override
    public String getId() {
        return id;
    }
}
