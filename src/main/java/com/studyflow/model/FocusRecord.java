package com.studyflow.model;

import com.studyflow.interfaces.Identifiable;
import com.studyflow.utils.ValidationUtils;
import java.time.Duration;
import java.time.Instant;

/** A completed focus interval. Historical links remain valid after a task or subject is removed. */
public record FocusRecord(String id, String taskId, String subjectId, Instant completedAt,
                          Duration planned, Duration actual) implements Identifiable {
    public FocusRecord {
        id = ValidationUtils.requiredText(id, "Focus session ID");
        taskId = ValidationUtils.optionalId(taskId, "Task ID");
        subjectId = ValidationUtils.optionalId(subjectId, "Subject ID");
        ValidationUtils.required(completedAt, "Focus completion time");
        ValidationUtils.positiveDuration(planned, "Planned focus duration");
        ValidationUtils.positiveDuration(actual, "Actual focus duration");
    }

    @Override
    public String getId() {
        return id;
    }
}
