package com.studyflow.model;

import com.studyflow.utils.ValidationUtils;

import java.time.LocalDate;

/** An assessment with preparation tracked separately from the task lifecycle. */
public final class Exam extends Task {
    public static final String DEFAULT_PREPARATION_STATUS = "Not started";

    private String examType;
    private String preparationStatus;

    public Exam(String id, String title, String description, String subjectId, LocalDate createdDate,
                LocalDate dueDate, Priority priority, TaskStatus status, String examType, String preparationStatus) {
        super(id, title, description, subjectId, createdDate, dueDate, priority, status);
        setExamType(examType);
        setPreparationStatus(preparationStatus);
    }

    private Exam(Exam source) {
        super(source);
        examType = source.examType;
        preparationStatus = source.preparationStatus;
    }

    public String getExamType() {
        return examType;
    }

    public void setExamType(String examType) {
        this.examType = ValidationUtils.requiredText(examType, "Exam type");
    }

    public String getPreparationStatus() {
        return preparationStatus;
    }

    public void setPreparationStatus(String preparationStatus) {
        this.preparationStatus = ValidationUtils.requiredText(preparationStatus, "Preparation status");
    }

    @Override
    public TaskType getType() {
        return TaskType.EXAM;
    }

    @Override
    public Exam copy() {
        return new Exam(this);
    }
}
