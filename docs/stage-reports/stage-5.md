# Stage 5 · Subjects, workspaces and notes

## Objective

Organize academic data by subject, manage workspaces and plain-text notes, and search the workspace across record types.

## Implementation

The subject service creates and edits subjects, links tasks and subtasks, counts associated tasks, and blocks deletion while records refer to a subject. The workspace service creates workspaces, updates ordered memberships, and resolves subject details. Removing a workspace preserves its subjects and their records. The note service validates subject links, maintains timestamps and tags, and searches plain text. `SearchService` groups results from tasks, subjects and notes.

## Files created

- `service/AcademicCollection.java`, `SubjectManager.java`, `WorkspaceManager.java`, `NoteManager.java` and `SearchService.java`.
- Academic service tests under `src/test/java/com/studyflow/service/` and this report.

## Files modified

- The component diagram pair, development log and Java concept mapping.
- `TaskManager.java` and `TaskManagerTest.java` for stale edits triggered by a reentrant task callback.

## Java concepts and patterns

Generic collection operations use `Storage<T>`, typed copier functions, `Optional`, collections and streams. The services apply the repository boundary and resolve associations by ID. Workspace membership is flat and shares subjects across workspaces. The folder Composite can be introduced when the product has nested folders.

## Testing

```powershell
mvn clean compile
mvn test
```

The clean Maven run compiled 42 production sources and passed all 41 tests. Eight academic service cases covered CRUD, timestamps, association validation, search, workspace membership and deletion policies. The additional task case verified that a stale edit leaves the remaining task intact.

## Issues and decisions

Deletion guards preserve tasks and notes and identify the blocking references. A note's timestamp never regresses if the local clock moves backward. A search term can partially match any eligible field, and all query terms must match.

## Deliverables and next stage

The academic services run independently of the GUI. Stage 6 can supply JSON-backed repositories using the same `Storage` contracts and connect the verification screens.
