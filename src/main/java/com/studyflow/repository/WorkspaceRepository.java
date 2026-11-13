package com.studyflow.repository;

import com.studyflow.model.Workspace;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/** Persists workspaces and their ordered subject references. */
public final class WorkspaceRepository
        extends JsonCollectionRepository<Workspace, WorkspaceRepository.WorkspaceRecord> {
    public WorkspaceRepository(FileStorage storage) {
        super(storage, Path.of("workspaces", "workspaces.json"), WorkspaceRecord.class,
                WorkspaceRecord::from, WorkspaceRecord::toDomain);
    }

    record WorkspaceRecord(String id, String name, String description, List<String> subjectIds,
                           LocalDateTime createdAt) {
        static WorkspaceRecord from(Workspace workspace) {
            return new WorkspaceRecord(workspace.getId(), workspace.getName(), workspace.getDescription(),
                    workspace.getSubjectIds(), workspace.getCreatedAt());
        }

        Workspace toDomain() {
            return new Workspace(id, name, description, subjectIds, createdAt);
        }
    }
}
