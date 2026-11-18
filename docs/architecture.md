# StudyFlow architecture

## Runtime and layers

StudyFlow is a single-user, local-first Java 21 desktop application. Maven builds and tests it. The Swing Event Dispatch Thread (EDT) owns view construction and widget updates. FlatLaf supplies persisted light and dark themes. The domain and services provide the model side of layered MVC.

| Package | Responsibility | Dependencies |
| --- | --- | --- |
| `com.studyflow` and `config` | Parse launch options, choose the data directory and defaults, assemble the application and manage its lifecycle | Controllers, services, storage implementations, Swing |
| `view` | Present the dashboard, navigation, task forms and academic record pages. Convert Swing events into controller calls | Controllers, domain types used for display |
| `controller` | Translate UI input into service commands, schedule I/O off the EDT, report results and errors to the view | Service APIs and domain types |
| `service` | Validate and coordinate updates, projections, search, PDF retrieval, study requests and analytics | Domain, repository contracts, factories, strategies and local PDF extraction |
| `model`, `interfaces`, `utils` | Model academic records and enforce their intrinsic invariants. Define contracts and shared validation | Java standard library |
| `factory`, `strategy` | Centralize task creation by type and provide interchangeable comparators | Domain types |
| `repository` | Translate validated snapshots to local storage and restore them, using Jackson and file utilities | Storage contracts, domain types and Jackson |
| `integrations` | Export iCalendar, read Google events for display, send dated tasks to Google Calendar, open Spotify URLs and implement the optional AI provider | Java HTTP and desktop APIs, JSON, `AIProvider` |

`Main` owns the runtime lifecycle and `StudyFlowContext` assembles the profile. Dependencies are passed to constructors so tests can use an in-memory store and a fixed `Clock`. A repository adapter can change without requiring Swing or the domain to change. The view never opens a JSON file. Services have no Swing dependency.

## Records and relationships

| Relationship | Cardinality and ownership |
| --- | --- |
| Local profile → `UserSettings` | One settings record per data directory. It supplies the theme, default priority, attendance target, notification preference and focus and break durations. |
| `Task` → `Subject` | Each task has zero or one subject. Each subject can be referenced by any number of root tasks or subtasks. |
| `Note` → `Subject` | Each note has zero or one subject. A subject can be referenced by any number of notes. |
| `Workspace` ↔ `Subject` | An ordered workspace membership list has zero or more unique subject IDs. A subject can belong to multiple workspaces. |
| `Task` → child `Task` | A task owns zero or more children. A child has one parent, and a root has none. Cycles and duplicate task IDs across the forest are invalid. |
| `Task` ← task subtypes | `GeneralTask`, `Assignment`, `Exam` and `StudySession` inherit common fields and behavior and supply type-specific fields. |
| `AttendanceEntry`, `AttendanceTarget` → `Subject` | Each entry or target belongs to an existing subject. Each subject has zero or more class entries and at most one saved target. |
| `StudyMaterial` → `Subject` | A PDF can link to a subject. Material metadata and managed PDF and index files share the material ID. |
| `FocusRecord` → task, subject | Optional IDs record the original context. Focus history can outlive its task or subject. |
| `StudyHistoryEntry` → evidence | Saved answers and question sets keep source labels and page references. The referenced material can later be removed. |

Tasks, notes and workspaces store subject IDs. The task manager resolves current subjects for searches and display, so a rename propagates immediately. Deleting a referenced subject is blocked and reports the affected relationship. Attendance records and material metadata participate in that check. Deleting a workspace removes its memberships and preserves its subjects. Deleting a task removes its owned subtree. Status and completion are independent between parent and child.

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
| `data/tasks/tasks.json` | Root tasks with nested subtasks, type-specific data, recurrence advancement and nullable completion timestamps |
| `data/subjects/subjects.json` | Subjects and tags |
| `data/workspaces/workspaces.json` | Workspaces and ordered subject IDs |
| `data/notes/notes.json` | Plain-text notes, tags, timestamps and optional subject IDs |
| `data/settings/settings.json` | Local profile settings |
| `data/attendance/classes.json`, `data/attendance/targets.json` | Dated class outcomes and per-subject targets |
| `data/focus/sessions.json` | Completed focus records and their actual durations |
| `data/materials/materials.json`, `data/materials/files/`, `data/materials/index/` | PDF library metadata, managed copies and rebuildable per-page text cache |
| `data/materials/assistant-history.json` | Study answers and question sets that the student chose to save |
| `data/integrations/links.json` | Provider links and identifiers needed to reopen the linked item or update a calendar event |
| `data/backups/` | Timestamped automatic and manual backups, and preserved originals from recovery |

The CLI supports `tasks`, `subjects`, `workspaces`, `notes`, `settings`, `attendance`, `attendance-targets`, `focus` and `integrations`. Subject restore validates dependent attendance and material metadata along with academic links. An unavailable optional collection can be shown as a scoped warning while a successful core change remains committed. Study history writes omit automatic previous copies so routine deletion does not create another conversation snapshot.

## PDF retrieval and study requests

`MaterialLibrary` stages PDF import and publishes metadata after it has a managed copy and a page index. The import reports copy, extraction and save progress. Cancellation before the final commit removes the staged work. `PdfPassageIndex` checks limits and cache integrity and can rebuild an outdated, missing or damaged index from the managed PDF. `PassageRanker` normalizes Unicode keywords, scores token and phrase matches, and returns bounded, deterministic evidence with at most one excerpt per page. Each result carries the material ID, PDF name and page number.

`StudyAssistant.prepare` chooses excerpts and builds immutable request data. Local modes produce extracts from those sources. The remote desktop flow previews the actual text and requires consent before sending it through `AIProvider`. Provider question JSON is checked for expected type, count and source IDs. `QuestionSet` keeps questions, answers, explanations and citations together. The local history saves only with the request's explicit option and reports save failures separately from the generated answer.

## Recorded analytics and integrations

`AnalyticsService` aggregates an inclusive date range from task completion timestamps, focus completion instants and class dates. It uses the profile clock's local zone for timestamps and the saved actual focus duration. Current task inventory remains a separate calculation. Legacy completed tasks can have `completedAt == null`. Their date is unknown and they are excluded from dated completions. Reopening preserves the latest recorded completion, and recompleting updates it. The task repository keeps no separate event ledger. Removing a task therefore also removes its completion from reports.

Calendar and Kanban project existing tasks, and Calendar can export selected deadlines to `.ics` offline. The Google adapter uses a desktop OAuth browser flow with session-only tokens. The Calendar view also requests read-only events from the connected account’s primary calendar for its displayed month. Opening the view, navigating months, the one-minute refresh while the view is open, and **Refresh Google** request an update off the Swing thread. Google expands recurring occurrences for the requested period. All-day dates are preserved, and timed occurrences use the time zone reported for the connected Google Calendar. The calendar reconciles task-linked events with the existing local task display to avoid duplicated entries. Google results remain in memory and bypass the task repository and Kanban.

The separate outbound path sends selected tasks on manual sync. Optional auto-sync sends eligible dated tasks when enabled or reconnected and processes changes while StudyFlow is open and connected. Task updates transfer from StudyFlow to Google. The local store retains task-to-event links and records manual removals per task and account. Spotify shortcuts pass normalized web URLs to the default browser. The integrations and remote assistant are optional, and automated provider tests exercise simulated responses. The local workflows need no provider account.

## Desktop pages

`MainFrame` hosts a persistent `SidebarPanel` grouped into Plan, Study, Track and Utilities, and switches content with Swing's `CardLayout`. Ctrl+K focuses global search. `DashboardPanel` renders a controller snapshot that includes live counts and lists for today's plan and upcoming tasks. Clicking a row opens the task. `TaskPanel` sends combined criteria to the controller and delegates record editing to `TaskFormDialog`. Academic pages dispatch typed commands to their managers. Guarded dialogs preserve edits while the student decides whether to discard them. The controller serializes primary commands on the application worker and sends results to the EDT. A successful settings save updates FlatLaf and the next launch loads that preference.

Focus intervals use a monotonic timer and save completed records under the local profile. Attendance retains class-level records and subject targets, with integer arithmetic for forecasts. Maven Shade bundles the application and runtime dependencies into one executable JAR.

## UML

- [Use case](uml/use-case-diagram.png) · [PlantUML](uml/use-case-diagram.puml)
- [Class](uml/class-diagram.png) · [PlantUML](uml/class-diagram.puml)
- [Task creation activity](uml/activity-diagram.png) · [PlantUML](uml/activity-diagram.puml)
- [Task creation and save sequence](uml/sequence-diagram.png) · [PlantUML](uml/sequence-diagram.puml)
- [Task state](uml/state-diagram.png) · [PlantUML](uml/state-diagram.puml)
- [Components](uml/component-diagram.png) · [PlantUML](uml/component-diagram.puml)
- [Local/remote study flow](uml/study-assistant-sequence.png) · [PlantUML](uml/study-assistant-sequence.puml)
