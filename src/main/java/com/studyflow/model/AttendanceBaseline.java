package com.studyflow.model;

import com.studyflow.interfaces.Identifiable;
import com.studyflow.utils.ValidationUtils;

/** Previously reported attendance totals with no individual class dates. */
public record AttendanceBaseline(String subjectId, int attended, int total) implements Identifiable {
    public AttendanceBaseline {
        subjectId = ValidationUtils.requiredText(subjectId, "Subject ID");
        validateCounts(attended, total);
    }

    public static void validateCounts(int attended, int total) {
        if (attended < 0 || total < attended) {
            throw new IllegalArgumentException("Attended classes must be between zero and the total classes conducted.");
        }
    }

    @Override
    public String getId() { return subjectId; }
}
