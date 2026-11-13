# StudyFlow architecture

## Runtime and layers

StudyFlow is a single-user, local-first Java 21 desktop application. Maven builds and tests it. The Swing Event Dispatch Thread (EDT) owns view construction and all widget updates. FlatLaf supplies the initial light look and feel. The application is organized as layered MVC, with the domain and services providing the model side of MVC.

| Package | Responsibility | Dependencies |
| --- | --- | --- |
| `com.studyflow` and `config` | Parse launch options, choose the data directory and defaults, assemble the application and manage its lifecycle | Controllers, services, storage implementations, Swing |
| `view` | Present the initial shell, input forms and result lists. Convert Swing events into controller calls | Controllers, domain types used for display |
| `controller` | Translate UI input into service commands, schedule I/O off the EDT, report results and errors to the view | Service APIs and domain types |
| `service` | Validate use cases and relationships, orchestrate updates, assemble search and task views | Domain, repository contracts, factories and strategies |
| `model`, `interfaces`, `utils` | Model academic records and enforce their intrinsic invariants. Define contracts and shared validation | Java standard library |
| `factory`, `strategy` | Centralize task creation by type and provide interchangeable comparators | Domain types |
| `repository` | Translate validated snapshots to local storage and restore them, using Jackson and file utilities | Storage contracts, domain types and Jackson |
| `integrations` | Reserve a package boundary for future adapters | Future service-defined ports |

`Main` owns the runtime lifecycle and `StudyFlowContext` assembles the profile. Dependencies are passed to constructors so tests can use an in-memory store and a fixed `Clock`. A repository adapter can change without requiring Swing or the domain to change. The view never opens a JSON file. Services have no Swing dependency.

## Records and relationships

| Relationship | Cardinality and ownership |
| --- | --- |
| Local profile → `UserSettings` | One settings record per data directory. It supplies the theme and default priority, and stores future attendance, notification and focus preferences. |
| `Task` → `Subject` | Each task has zero or one subject. Each subject can be referenced by any number of root tasks or subtasks. |
| `Note` → `Subject` | Each note has zero or one subject. A subject can be referenced by any number of notes. |
| `Workspace` ↔ `Subject` | An ordered workspace membership list has zero or more unique subject IDs. A subject can belong to multiple workspaces. |
| `Task` → child `Task` | A task owns zero or more children. A child has one parent, and a root has none. Cycles and duplicate task IDs across the forest are invalid. |
| `Task` ← task subtypes | `GeneralTask`, `Assignment`, `Exam` and `StudySession` inherit common fields and behavior and supply type-specific fields. |

Tasks, notes and workspaces store subject IDs. The task manager resolves current subjects for searches and display, so a rename propagates immediately. Deleting a subject with references from any task, note or workspace is rejected with a reason. Deleting a workspace removes its memberships and preserves its subjects. Deleting a task removes its owned subtree. Status and completion are independent between parent and child.

The task register, filters, date views, Kanban grouping and global search use root tasks. Subtasks are accessed through the parent. Reference validation and deletion checks traverse the entire tree, and subject task counts include associated subtasks. Global search groups results by task, subject and note. The task manager can match tokens across a task and its current subject in a single query.

## Task engine

TaskFactory creates a subtype for a supplied `TaskType`. Subtype behavior includes checking assignment metadata, recording exam preparation and measuring a study session. The task service validates references and invokes domain behavior for edits, status changes and subtasks. Natural ordering compares due date with nulls last and uses title then ID as deterministic tie breakers. Four comparator strategies cover deadline, priority, title and creation date. Query results use independent snapshots.

`TODO` and `IN_PROGRESS` can transition between each other or complete. An explicit reopen returns `COMPLETED` to `TODO`. Updating to the current status is idempotent. `isOverdue(today)` compares the deadline to the local date and excludes completed tasks. Today contains incomplete tasks due on that day. Upcoming contains incomplete tasks due from tomorrow through day seven, inclusive. Smart Priority ranks incomplete tasks in four urgency bands (overdue, today, next seven days, later or undated), then by priority and natural order.

A root task can recur daily, weekly or monthly and needs a deadline. Its first completion while recurring creates one successor from its previous due date. A late completion can create an already overdue successor. Monthly dates use `LocalDate.plusMonths` and clamp to the target month's last valid day. The successor has new IDs, a fresh creation date, `TODO` status and reset completion-specific fields. The owned subtree is copied and its status is reset. A persisted advancement marker on the predecessor prevents another successor after a reopen or retry, even if the original successor is later deleted. Completion and successor insertion are committed in one tasks snapshot.

## Write and startup paths

1. The student enters a value in a Swing form. Its controller reads the fields and submits a service command to a serial background executor.
2. The service resolves IDs, validates the request and constructs a candidate collection. Record edits are detached from the committed state.
3. A repository converts records to explicit data transfer objects and writes a candidate snapshot through `FileStorage`. JSON is written in UTF-8 with named enums, ISO local dates and ISO local date-times. Persisted task types use a fixed set of known type names. Optional fields allow explicit nulls. Invalid data, including missing required values, receives a contextual error.
4. The file adapter serializes into a sibling temporary file. When replacing an existing valid file, it saves a uniquely named timestamped copy under `data/backups`. It replaces the destination with a filesystem atomic move when supported. A failed commit leaves the previously committed state available and reports the path and cause. Successful validation and persistence precede the controller's success notification.
5. The controller publishes the result on the EDT. Shutdown drains the executor and releases the data directory lock after writes finish.

Startup loads each file if present, supplies empty lists or default settings for missing files, then checks the complete set for duplicate IDs and dangling references. An existing empty or invalid file is reported as damaged and left intact. The `--restore` command locks the profile without using normal startup. It validates a selected per-file backup and checks the proposed dataset against current sibling files before replacing the destination. Restore preserves the previous bytes, including damaged bytes when present, for inspection. The chosen data root is locked against a second StudyFlow writer. Across services the single application worker is the concurrency boundary for operations that depend on multiple record types.

The `--backup` command locks and validates the profile, then copies the selected live JSON file to `data/backups`. `FileStorage` validates the selected bytes before writing them to a uniquely named file with a UTC timestamp. The same per-file restore command accepts manual and automatic backups.

| File | Snapshot |
| --- | --- |
| `data/tasks/tasks.json` | Root tasks with nested subtasks, type-specific data and recurrence advancement state |
| `data/subjects/subjects.json` | Subjects and tags |
| `data/workspaces/workspaces.json` | Workspaces and ordered subject IDs |
| `data/notes/notes.json` | Plain-text notes, tags, timestamps and optional subject IDs |
| `data/settings/settings.json` | Local profile settings |
| `data/backups/` | Timestamped automatic and manual backups, and preserved originals from recovery |

## Extension paths

Stage 7 can replace the verification views while keeping controller-facing service behavior. Stage 8 can query deadlines for a calendar, use existing task status groups for Kanban and route status changes through the task service. Notifications and focus timing can use application services with a managed lifecycle. Stage 9 can key attendance records by subject ID. Stage 10 can add material metadata linked by subject ID and keep file extraction and summarization behind service-owned interfaces. Stage 11 can adapt external clients to these interfaces and keep credentials outside the source and local academic records. A folder model can introduce a common workspace item contract if nested folders need uniform operations. Stage 12 can package the executable and expand end-to-end testing.

## UML

- [Use case](uml/use-case-diagram.png) · [PlantUML](uml/use-case-diagram.puml)
- [Class](uml/class-diagram.png) · [PlantUML](uml/class-diagram.puml)
- [Task creation activity](uml/activity-diagram.png) · [PlantUML](uml/activity-diagram.puml)
- [Task creation and save sequence](uml/sequence-diagram.png) · [PlantUML](uml/sequence-diagram.puml)
- [Task state](uml/state-diagram.png) · [PlantUML](uml/state-diagram.puml)
- [Components](uml/component-diagram.png) · [PlantUML](uml/component-diagram.puml)
