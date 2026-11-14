package com.studyflow.model;

import com.studyflow.interfaces.Identifiable;
import com.studyflow.utils.ValidationUtils;

/** Optional per-subject percentage override. */
public record AttendanceTarget(String subjectId, int target) implements Identifiable {
    public AttendanceTarget {
        subjectId = ValidationUtils.requiredText(subjectId, "Subject ID");
        if (target < 0 || target > 100) {
            throw new IllegalArgumentException("Attendance target must be between 0 and 100.");
        }
    }

    @Override
    public String getId() {
        return subjectId;
    }
}
