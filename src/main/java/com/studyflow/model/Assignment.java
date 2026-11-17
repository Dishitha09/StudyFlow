package com.studyflow.model;

import com.studyflow.utils.ValidationUtils;

import java.time.Instant;
import java.time.LocalDate;

/** Coursework with a submission method and optional attachment path metadata. */
public final class Assignment extends Task {
    private String submissionType;
    private String attachmentPath;

    public Assignment(String id, String title, String description, String subjectId, LocalDate createdDate,
                      LocalDate dueDate, Priority priority, TaskStatus status, String submissionType,
                      String attachmentPath) {
        this(id, title, description, subjectId, createdDate, dueDate, priority, status, submissionType,
                attachmentPath, null);
    }

    public Assignment(String id, String title, String description, String subjectId, LocalDate createdDate,
                      LocalDate dueDate, Priority priority, TaskStatus status, String submissionType,
                      String attachmentPath, Instant completedAt) {
        super(id, title, description, subjectId, createdDate, dueDate, priority, status, completedAt);
        setSubmissionType(submissionType);
        setAttachmentPath(attachmentPath);
    }

    private Assignment(Assignment source) {
        super(source);
        submissionType = source.submissionType;
        attachmentPath = source.attachmentPath;
    }

    public String getSubmissionType() {
        return submissionType;
    }

    public void setSubmissionType(String submissionType) {
        this.submissionType = ValidationUtils.requiredText(submissionType, "Submission type");
    }

    public String getAttachmentPath() {
        return attachmentPath;
    }

    public void setAttachmentPath(String attachmentPath) {
        this.attachmentPath = attachmentPath == null || attachmentPath.isBlank() ? null : attachmentPath.strip();
    }

    @Override
    public TaskType getType() {
        return TaskType.ASSIGNMENT;
    }

    @Override
    public Assignment copy() {
        return new Assignment(this);
    }
}
