# Stage 3 · Core Java domain model

## Objective

Build a validated Swing-independent model for academic tasks, subjects, workspaces, notes and settings.

## Implementation

`Task` owns shared fields, status and deadline behavior, and an ordered subtask tree. `GeneralTask`, `Assignment`, `Exam` and `StudySession` supply their own types and copy behavior. The academic records own their tags and membership, while task and note associations use subject IDs. Shared helpers validate fields and implement case-insensitive token matching. Application defaults now use model enums.

## Files created

- `src/main/java/com/studyflow/model/` with the task hierarchy, four academic/settings models, and task type, status, theme and priority enums.
- `src/main/java/com/studyflow/interfaces/` with `Identifiable`, `Prioritizable`, `Searchable` and `Storage`.
- `src/main/java/com/studyflow/utils/ValidationUtils.java` and `SearchText.java`.
- `src/test/java/com/studyflow/model/` and `education/` with model tests and isolated course examples.
- `docs/stage-reports/stage-3.md`.

## Files modified

- `AppConfig.java` and the Java concept mapping, development log and class diagram pair.

## Java concepts and patterns

The abstract task base demonstrates inheritance, overriding, runtime dispatch, interfaces, encapsulation and natural ordering. A task owns its descendants through composition. An isolated educational example covers field hiding, static initialization, multidimensional arrays, `StringBuffer`, `StringTokenizer`, `LinkedList` and `ListIterator`.

## Testing

```powershell
mvn clean compile
mvn test
```

The clean Maven run compiled 23 production sources and passed all 18 tests with zero failures, errors or skips. The tests covered task types, subtype behavior, dates, ordering, invalid inputs, detached ownership, note updates, workspace membership, settings and course demonstrations.

## Issues and decisions

The model treats missing deadlines as an explicit absence and accepts past deadlines. Duplicate IDs within a task tree are checked in the model. Association validity and duplicates across root tasks are checked by the service, which sees the full collection. Note edits use a caller-supplied timestamp to keep rules reproducible in tests.

## Deliverables and next stage

The models and shared contracts provide the input and validation boundary for the task engine. Stage 4 can add factories, query strategies, recurrence and service operations using the existing domain.
