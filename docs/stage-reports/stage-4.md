# Stage 4 · Task management engine

## Objective

Provide complete task management through Swing-independent services.

## Implementation

`TaskManager` creates, retrieves, edits and removes tasks, applies status and priority, and validates the task forest and subject references. It exposes quick-add, search, combined filters, date views, Smart Priority and Kanban grouping. `TaskFactory` builds concrete task types and initializes recurrence successors. Four sort strategies operate on detached query results. The manager stores each candidate snapshot before publishing the new state.

## Files created

- `service/TaskManager.java`, `TaskFilter.java` and task exceptions.
- `factory/TaskDraft.java` and `TaskFactory.java`.
- `strategy/TaskSortStrategy.java` and four comparators.
- `repository/MemoryStorage.java`, `model/Recurrence.java` and `utils/IdGenerator.java`.
- `src/test/java/com/studyflow/service/TaskManagerTest.java` and this report.

## Files modified

- `model/Task.java` and the class diagram pair.
- `docs/development-log.md` and `docs/java-concept-mapping.md`.

## Java concepts and patterns

The Factory chooses a task subtype from a typed draft. Strategy makes sorting interchangeable. ArrayList holds task candidates, an enum map groups status columns, and set membership detects repeated IDs. Exceptions report invalid and missing tasks and preserve the prior state after a failed commit.

## Testing

```powershell
mvn clean compile
mvn test
```

The clean Maven build compiled 37 production sources. All 32 tests passed, including 14 task-engine cases for creation, updates, invalid inputs, filtering, sorting, ranking, date boundaries, recurrence, nested operations and rollback after a failed storage commit.

## Issues and decisions

Upcoming excludes today and includes day seven. Sorting preserves the stored order. Recurrence advances from the prior due date and may create an overdue successor after late completion. A persisted boolean prevents repeated advancement even after reopening or deleting the successor. Root views and subject queries use the scopes defined in the SRS.

## Deliverables and next stage

Stage 5 can connect subject, workspace and notes services to the task manager and add categorized global search.
