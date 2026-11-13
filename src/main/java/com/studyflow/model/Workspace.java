package com.studyflow.model;

import com.studyflow.interfaces.Identifiable;
import com.studyflow.utils.ValidationUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A named collection of references to independently managed subjects. */
public final class Workspace implements Identifiable {
    private final String id;
    private String name;
    private String description;
    private final Set<String> subjectIds = new LinkedHashSet<>();
    private final LocalDateTime createdAt;

    public Workspace(String id, String name, String description, List<String> subjectIds, LocalDateTime createdAt) {
        this.id = ValidationUtils.requiredText(id, "Workspace ID");
        setName(name);
        setDescription(description);
        this.createdAt = ValidationUtils.required(createdAt, "Workspace creation time");
        for (String subjectId : ValidationUtils.required(subjectIds, "Workspace subject IDs")) {
            if (!addSubject(subjectId)) {
                throw new IllegalArgumentException("Duplicate subject ID in workspace: " + subjectId);
            }
        }
    }

    @Override
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = ValidationUtils.requiredText(name, "Workspace name");
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = ValidationUtils.textOrEmpty(description);
    }

    public List<String> getSubjectIds() {
        return List.copyOf(subjectIds);
    }

    public boolean addSubject(String subjectId) {
        return subjectIds.add(ValidationUtils.requiredText(subjectId, "Subject ID"));
    }

    public boolean removeSubject(String subjectId) {
        return subjectIds.remove(ValidationUtils.requiredText(subjectId, "Subject ID"));
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Workspace copy() {
        return new Workspace(id, name, description, getSubjectIds(), createdAt);
    }
}
