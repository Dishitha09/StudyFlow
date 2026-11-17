package com.studyflow.model;

import com.studyflow.utils.ValidationUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/** A study block that can compare time spent with its original estimate. */
public final class StudySession extends Task {
    private Duration plannedDuration;
    private Duration actualDuration;

    public StudySession(String id, String title, String description, String subjectId, LocalDate createdDate,
                        LocalDate dueDate, Priority priority, TaskStatus status, Duration plannedDuration,
                        Duration actualDuration) {
        this(id, title, description, subjectId, createdDate, dueDate, priority, status,
                plannedDuration, actualDuration, null);
    }

    public StudySession(String id, String title, String description, String subjectId, LocalDate createdDate,
                        LocalDate dueDate, Priority priority, TaskStatus status, Duration plannedDuration,
                        Duration actualDuration, Instant completedAt) {
        super(id, title, description, subjectId, createdDate, dueDate, priority, status, completedAt);
        setPlannedDuration(plannedDuration);
        setActualDuration(actualDuration);
    }

    private StudySession(StudySession source) {
        super(source);
        plannedDuration = source.plannedDuration;
        actualDuration = source.actualDuration;
    }

    public Duration getPlannedDuration() {
        return plannedDuration;
    }

    public void setPlannedDuration(Duration plannedDuration) {
        this.plannedDuration = ValidationUtils.positiveDuration(plannedDuration, "Planned duration");
    }

    public Duration getActualDuration() {
        return actualDuration;
    }

    public void setActualDuration(Duration actualDuration) {
        if (actualDuration != null && actualDuration.isNegative()) {
            throw new IllegalArgumentException("Actual duration cannot be negative.");
        }
        this.actualDuration = actualDuration;
    }

    public boolean exceededPlan() {
        return actualDuration != null && actualDuration.compareTo(plannedDuration) > 0;
    }

    @Override
    public TaskType getType() {
        return TaskType.STUDY_SESSION;
    }

    @Override
    public StudySession copy() {
        return new StudySession(this);
    }
}
