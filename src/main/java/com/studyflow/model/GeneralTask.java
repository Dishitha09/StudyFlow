package com.studyflow.model;

import java.time.LocalDate;

/** A task that needs only the common fields. */
public final class GeneralTask extends Task {
    public GeneralTask(String id, String title, LocalDate createdDate, Priority priority) {
        this(id, title, "", null, createdDate, null, priority, TaskStatus.TODO);
    }

    public GeneralTask(String id, String title, String description, String subjectId, LocalDate createdDate,
                       LocalDate dueDate, Priority priority, TaskStatus status) {
        super(id, title, description, subjectId, createdDate, dueDate, priority, status);
    }

    private GeneralTask(GeneralTask source) {
        super(source);
    }

    @Override
    public TaskType getType() {
        return TaskType.GENERAL;
    }

    @Override
    public GeneralTask copy() {
        return new GeneralTask(this);
    }
}
