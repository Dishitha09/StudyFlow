package com.studyflow.service;

import com.studyflow.interfaces.Storage;
import com.studyflow.model.Subject;
import com.studyflow.model.Workspace;
import com.studyflow.utils.IdGenerator;
import com.studyflow.utils.ValidationUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;

/** Groups subjects into workspaces while retaining their independent lifecycle. */
public final class WorkspaceManager {
    private final AcademicCollection<Workspace> workspaces;
    private final Function<String, Optional<Subject>> subjectLookup;
    private final Clock clock;

    public WorkspaceManager(Storage<List<Workspace>> storage, Function<String, Optional<Subject>> subjectLookup,
                            Clock clock) {
        this.workspaces = new AcademicCollection<>(storage, Workspace::copy);
        this.subjectLookup = ValidationUtils.required(subjectLookup, "Subject lookup");
        this.clock = ValidationUtils.required(clock, "Clock");
        for (Workspace workspace : workspaces.findAll()) {
            workspace.getSubjectIds().forEach(this::requireSubject);
        }
    }

    public Workspace create(String name, String description) {
        return create(name, description, List.of());
    }

    public Workspace create(String name, String description, List<String> subjectIds) {
        Workspace workspace = new Workspace(IdGenerator.next(), name, description, subjectIds, LocalDateTime.now(clock));
        workspace.getSubjectIds().forEach(this::requireSubject);
        return workspaces.insert(workspace);
    }

    public Workspace update(String id, String name, String description) {
        return workspaces.edit(id, "Workspace", workspace -> {
            workspace.setName(name);
            workspace.setDescription(description);
        });
    }

    /** Saves details and membership together after validating the selected subjects. */
    public Workspace update(String id, String name, String description, List<String> subjectIds) {
        return workspaces.edit(id, "Workspace", workspace -> {
            workspace.setName(name);
            workspace.setDescription(description);
            workspace.setSubjectIds(subjectIds);
            workspace.getSubjectIds().forEach(this::requireSubject);
        });
    }

    public Optional<Workspace> findById(String id) {
        return workspaces.findById(id);
    }

    public List<Workspace> findAll() {
        return workspaces.findAll();
    }

    public void delete(String id) {
        if (!workspaces.delete(id)) {
            throw new NoSuchElementException("Workspace not found: " + id);
        }
    }

    public Workspace addSubject(String workspaceId, String subjectId) {
        String memberId = requireSubject(subjectId).getId();
        return workspaces.edit(workspaceId, "Workspace", workspace -> {
            if (!workspace.addSubject(memberId)) {
                throw new IllegalArgumentException("Subject already belongs to workspace: " + memberId);
            }
        });
    }

    public Workspace removeSubject(String workspaceId, String subjectId) {
        return workspaces.editIfChanged(workspaceId, "Workspace", workspace -> workspace.removeSubject(subjectId));
    }

    public List<Subject> findContents(String workspaceId) {
        return workspaces.require(workspaceId, "Workspace").getSubjectIds().stream()
                .map(this::requireSubject).map(Subject::copy).toList();
    }

    private Subject requireSubject(String id) {
        String subjectId = ValidationUtils.requiredText(id, "Subject ID");
        return subjectLookup.apply(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown subject ID: " + subjectId));
    }
}
