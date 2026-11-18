# StudyFlow software requirements specification

**Project** — Design and Development of a Student Productivity and Academic Management System Using Java
**Release scope** — Stages 1–12 and completion pass
**Primary platform** — Windows 10/11 with Java 21 LTS

## Purpose and users

University students often track deadlines, coursework, study notes, and subject resources in separate applications. StudyFlow gives one student a desktop workspace for planning work, organizing subjects and notes, and retrieving academic information. The core application uses the local filesystem and supports offline use once installed. The student is the sole user and manages the records and local backups.

## Scope and constraints

The delivered Java desktop application includes the task engine, academic records, JSON persistence, the Swing workspace, planning tools, attendance, PDF retrieval and optional external integrations. Core workflows run locally. Configured integrations remain independent from core records.

Use Java 21, Swing and AWT, FlatLaf, Java Time, Java collections, Maven, Jackson Databind, local JSON, JUnit Jupiter, and Git. Use Java threads and `ExecutorService` when background work is needed. Use Maven's standard source and test layout, Java release 21, UTF-8, and compiler, Surefire, and Exec plugins with compatible versions. VS Code and IntelliJ IDEA are supported development options. The required terminal commands are `mvn clean compile`, `mvn test`, and `mvn exec:java -Dexec.mainClass="com.studyflow.Main"`.

## Domain and relationships

| Entity | Required information and rules |
|---|---|
| `Task` | Abstract base with a stable ID, title, optional description and subject association, creation date, optional deadline, priority, status, and subtasks. Supports completion, status changes, deadline calculations, and subtask management. Implements `Comparable<Task>`, `Prioritizable`, and `Searchable`. |
| `GeneralTask` | Concrete task with the common task fields and behavior. |
| `Assignment` | Concrete task with submission type and optional attachment path metadata. Material upload belongs to Stage 10. |
| `Exam` | Concrete task with exam type and preparation status. |
| `StudySession` | Concrete task with planned duration and optional actual duration. Durations must be valid for their intended use. |
| `Subject` | Stable ID, name, subject code, description, and tags. |
| `Workspace` | Stable ID, name, description, creation timestamp, and references to member subjects. |
| `Note` | Stable ID, title, plain text content, optional subject ID, creation and update timestamps, and tags. |
| `UserSettings` | A single local profile with theme preference, default task priority, attendance target, notification preferences, and focus and break durations. Attendance, notifications, and focus settings are stored in these stages for later use. |

`Priority` values are `HIGH`, `MEDIUM`, and `LOW`. `TaskStatus` values are `TODO`, `IN_PROGRESS`, and `COMPLETED`. `Theme` values are `LIGHT` and `DARK`. `TaskType` values are `GENERAL`, `ASSIGNMENT`, `EXAM`, and `STUDY_SESSION`. Creation dates and deadlines use `LocalDate`. Note and workspace timestamps use `LocalDateTime`.

| Relationship | Cardinality and ownership |
|---|---|
| Workspace to Subject | Each workspace has zero or more subjects. A subject may be in zero or more workspaces. Workspaces own their membership lists, and subjects have independent lifecycles. |
| Subject to Task | Each subject has zero or more tasks. Each task has zero or one subject. |
| Subject to Note | Each subject has zero or more notes. Each note has zero or one subject. |
| Task to subtask | A task owns zero or more child tasks. Each child has one parent. Top-level tasks have no parent. A child belongs to one tree and cycles are invalid. Task IDs are unique across roots and descendants. |
| Task to concrete types | A task is a general task, assignment, exam, or study session. A stable type discriminator preserves the concrete type across a restart. |
| Settings to installation | A local data directory has one effective settings profile. Defaults apply on a fresh installation. Settings do not own academic records. |

Tasks and notes reference subjects by ID, and workspaces store lists of subject IDs. Services resolve the current subject when a caller needs subject information. A subject rename is visible to all associated records without rewriting each one. Workspace contents are derived from membership and subject links. Future folders can introduce explicit parent-child ownership while preserving existing record IDs and relationships.

## Functional requirements

### Application foundation and domain

| ID | Stage | Requirement |
|---|---|---|
| APP-01 | 2 | `com.studyflow.Main` shall initialize configuration and look and feel, create the GUI on the Swing Event Dispatch Thread, display the main window, and report startup errors. The window shall show the StudyFlow name and identity, use layout managers and usable initial and minimum dimensions, and include a sidebar with future navigation placeholders and an initial content panel. |
| APP-02 | 2 | Central configuration shall define the application name, version, default theme, data directory, and default task settings. The README shall describe prerequisites, build, tests, and launch. Environment verification shall check `java -version`, `javac -version`, and `mvn -version`, with Maven installation guidance when it is absent. |
| DOM-01 | 3 | Encapsulated model objects shall validate required fields, enum values, timestamps, duration values, and tree structure at the relevant boundary. Association targets and duplicate IDs shall be checked where the service or repository has access to related records. The domain and shared `Searchable`, `Prioritizable`, and storage contracts shall remain Swing-independent. |
| DOM-02 | 3 | A task shall report a signed days-remaining value when it has a deadline, with zero on the due date and an explicit absence when it has none. Overdue shall be derived from an incomplete status and a deadline strictly before the current local date. Historical deadlines are valid input. |

### Task management

| ID | Stage | Requirement |
|---|---|---|
| TASK-01 | 4 | Create, retrieve by ID, list, edit, and delete tasks. Create general tasks, assignments, exams, and study sessions through `TaskFactory`. Generate unique IDs. Preserve concrete types and their metadata on retrieval. Reject duplicate IDs, missing titles, unknown subject IDs, invalid deadlines, missing priority or status, and operations on missing tasks with meaningful errors. |
| TASK-02 | 4 | Set a task's description, optional deadline, optional subject, and priority. A new task starts at `TODO`. Allow `TODO` and `IN_PROGRESS` to move between each other or to `COMPLETED`. Reopen a completed task by moving it to `TODO`. Reject a direct transition from `COMPLETED` to `IN_PROGRESS`. Setting the current status again has no effect. |
| TASK-03 | 4 | Quick-add a general task from a required title, optional subject, optional deadline, and optional priority. Apply the configured default priority when omitted. Use the same validation as standard creation. |
| TASK-04 | 4 | Today shall return incomplete tasks due on the current local date. Upcoming shall return incomplete tasks due from tomorrow through seven days after today, inclusive. Both views exclude undated tasks. |
| TASK-05 | 4 | Smart Priority shall rank incomplete tasks first by urgency band (overdue, today, within seven days, and later or undated), then by descending priority, then by natural task order. The caller shall be able to identify overdue tasks from the results. |
| TASK-06 | 4 | Provide interchangeable deadline, priority, title, and creation-date sort strategies. The natural order shall be deadline-first, with undated tasks last and deterministic tie breakers. Sorting a result shall leave the stored ordering intact. |
| TASK-07 | 4 | Filter by subject, priority, status, task type, inclusive deadline range, or overdue state. Combine supplied filters with logical AND. An undated task falls outside any supplied deadline range. Reject an inverted deadline range. |
| TASK-08 | 4 | Search task title, description, current subject name, and applicable tags. Queries shall be case-insensitive, support partial matching, and ignore leading and trailing whitespace. |
| TASK-09 | 4 | Provide Kanban groupings for `TODO`, `IN_PROGRESS`, and `COMPLETED`, with each queried task appearing in its status group. Drag-and-drop belongs to Stage 8. |
| TASK-10 | 4 | Support daily, weekly, and monthly recurrence for top-level tasks with a deadline. The first completion creates one successor due one interval after the previous deadline, including when that date has already passed. Monthly recurrence uses Java's `LocalDate.plusMonths` to adjust to the last valid day of short months. Copy task and subtask fields with fresh IDs and reset statuses. Repeating completion shall preserve a single successor for that occurrence. In Stage 6, persist the completion and successor in one task-file write. |
| TASK-11 | 4 | Create, retrieve, remove, and track completion of subtasks. Preserve subtask ownership and independently track each child's status. A task tree shall reject duplicate IDs and cycles. |

Task register listings, search, filters, date views and Kanban grouping return root tasks. Parent operations expose their subtasks. Subject task listings and counts include associated descendants.

### Academic organization and search

| ID | Stage | Requirement |
|---|---|---|
| ACAD-01 | 5 | Create, edit, delete, retrieve, and search subjects. Support assignment, reassignment, and clearing of a task's subject. List and count tasks associated with a subject. Reject deletion of subjects referenced by tasks, including descendants, notes, or workspace membership. The error shall identify the blocking association so it can be removed or reassigned. |
| ACAD-02 | 5 | Create, edit, delete, and retrieve workspaces. Add and remove existing subjects and retrieve workspace contents. Reject unknown subject references and duplicate memberships. Deleting a workspace removes its membership while preserving subjects and their tasks and notes. |
| ACAD-03 | 5 | Create, edit, delete, retrieve, and search plain text notes. Capture title and content, manage optional subject assignment and tags, and retrieve notes by subject. Set creation and update timestamps on creation and retain the creation timestamp during edits. |
| ACAD-04 | 5 | Return categorized global search results for tasks, subjects, and notes. Split a multiword query on whitespace and require each term to partially match at least one searchable field. A term may match a different field from another term. Treat missing content as empty and return empty categories for a blank query. Search subject name, code, description, and tags and note title, content, and tags. |

### Local persistence and recovery

| ID | Stage | Requirement |
|---|---|---|
| DATA-01 | 6 | Provide repositories for tasks, subjects, workspaces, notes, and settings. Collection repositories shall support create, find by ID, list, update, delete, and existence checks. Settings shall expose an appropriate single-profile load and save contract. Managers shall depend on storage contracts with Jackson and filesystem I/O handled in the persistence layer. |
| DATA-02 | 6 | Store data under the configurable `data` directory in `tasks/tasks.json`, `subjects/subjects.json`, `workspaces/workspaces.json`, `notes/notes.json`, and `settings/settings.json`. Use stable enum names, ISO date and timestamp strings, explicit handling of optional fields, and subject IDs. Preserve subtask trees, subtype fields, and associations across reopen. |
| DATA-03 | 6 | Create required directories at startup, load and validate existing data and its references, and initialize empty collections and default settings on a fresh installation. Meaningful errors shall identify the cause and affected path for corrupt or empty JSON, invalid dates and enum values, directory creation and file access errors, and serialization or deserialization failures. A failed load shall preserve existing files and prevent an unsafe writable session. |
| DATA-04 | 6 | Automatically save successful mutations. Validate before publishing changes and preserve the prior state when an operation fails. Write serialized data to a temporary file and use safe replacement where supported. Skip redundant writes for unchanged data. Complete queued saves before closing the application and release file resources and executors. |
| DATA-05 | 6 | Maintain timestamped local backups under `data/backups/`, preserving prior valid data and avoiding replacement of the only valid copy. Provide a manual command to back up the current saved file for a selected record type after validating the profile. Report an error if the selected file is missing. Provide a documented per-file restore workflow that validates the proposed profile before replacing the live file. Invalid or corrupt data shall remain available for recovery or diagnosis. |
| DATA-06 | 6 | From the initial desktop shell, a student shall be able to create a task, subject, workspace, and note, close StudyFlow, and see the same records after restart. Include a restart acceptance check in addition to repository and service tests. |

### Desktop interface

| ID | Stage | Requirement |
|---|---|---|
| UI-01 | 7 | Open the main dashboard with persistent sidebar navigation and an active-page indicator. Keep core pages usable on resize and identify the availability of future modules. |
| UI-02 | 7 | Show the current date, task summary, five data-backed statistics, due-today tasks with a completion action, upcoming deadlines and Smart Priority. Provide shortcuts to add a task, subject or note and access the Focus and Attendance availability pages. |
| UI-03 | 7 | Create, edit and delete tasks, inspect details, change status and priority, and apply search, sorting and combined subject, priority, status, type, overdue and date filters. Support subtype metadata and show validation errors while preserving inputs for correction. |
| UI-04 | 7 | Manage subjects, workspaces and notes through their own pages. Switch between Light and Dark and restore the saved theme at startup. |

## Non-functional requirements

| ID | Quality | Requirement and verification |
|---|---|---|
| NFR-01 | Maintainability | Use focused packages and classes in layered MVC. Give business rules a single owner and document significant responsibilities and decisions. Inspect dependencies and the architecture diagrams at stage reviews. |
| NFR-02 | Extensibility | Use repository contracts, a task factory, interchangeable sort strategies, and clear service boundaries. Document extension points for future integrations and verify that the core can be tested with alternate storage implementations. |
| NFR-03 | Reliability | Validate input and persisted data. Verify that invalid transitions and associations fail without applying changes and that I/O errors report what failed. |
| NFR-04 | Usability | Label controls clearly, provide actionable messages for invalid operations, and verify the first launch and the record creation and restart workflow in the initial desktop shell. |
| NFR-05 | Performance | Keep the Swing Event Dispatch Thread available during potentially slow disk operations. Check ordinary local actions for responsiveness during the desktop workflow. |
| NFR-06 | Portability | Use Java 21 and platform-independent path construction. Verify the Maven build and desktop launch on the Windows target. Maintain compatibility with other Java desktop environments where supported. |
| NFR-07 | Data integrity | Preserve prior valid data on write or load failure, verify backups before restoration, and check task IDs and cross-record links. Test against isolated directories. |
| NFR-08 | Security | Keep credentials out of source and configuration committed to Git. Core stages use no external accounts or integrations. |
| NFR-09 | Offline operation | Perform record management, search, persistence, and local recovery without a network connection after the build and runtime dependencies are installed. |
| NFR-10 | Testability | Test domain behavior, services, and repositories independently of Swing using JUnit Jupiter, fixed dates for deadline-sensitive cases, and temporary directories for persistence. |

## Verification by stage

| Stage | Verification and evidence |
|---|---|
| 1 | Review this specification, the architecture, design decisions, and six rendered UML diagrams with their PlantUML sources. Confirm that the design can be implemented and record the review before application coding begins. |
| 2 | Check the Java, compiler, and Maven versions. Run the required compile and test commands, launch through Maven, and observe the StudyFlow window. |
| 3 | Compile and run model tests for constructors, polymorphism, priority access, deadline calculations, status, subtask ownership, and invalid data. Compare the implemented relationships with the class diagram. |
| 4 | Run task-engine tests for CRUD, quick-add and defaults, status changes, duplicate IDs, invalid inputs, search, all sorting modes, combined filters, date boundaries, smart ranking, Kanban grouping, each recurrence interval, and subtask operations. The tests shall run independently of Swing. |
| 5 | Run service tests for subject and workspace CRUD and membership, task assignment and counts, note CRUD and timestamps, case-insensitive partial and multiword search, invalid IDs, and safe deletion policies. |
| 6 | Run repository tests for CRUD and reopen, all persistent entity types, missing and empty files, invalid JSON, dates and enums, backup creation and restore, and reference integrity. Run the desktop restart acceptance check in DATA-06. |
| 7 | Verify dashboard values and completion, sidebar navigation, task CRUD and queries, record links and edits, light and dark themes, window resizing and persistence on restart. |

Each stage shall preserve previous behavior, record executed verification and outcomes in `docs/stage-reports/stage-N.md`, update the development log, and create a Git checkpoint. The project shall maintain `docs/java-concept-mapping.md` with syllabus concept locations, rationale, and viva explanations. Separate educational examples may demonstrate techniques unsuitable for production, with later-stage coverage identified explicitly.

## Release-stage requirements

| Stage | Requirements |
|---|---|
| 8 — Productivity | Monthly calendar, Kanban status controls, reminders, focus sessions, global search, and task recurrence and subtask controls. |
| 9 — Attendance | Subject-wise attendance, percentage, target, maximum classes that can be missed, and classes required to reach a target. |
| 10 — Materials and AI | Subject-wise study material library, document and PDF uploads, PDF text extraction, topic-based document retrieval, AI summaries and study assistance, and generated revision questions. |
| 11 — Integrations | One-way Google Calendar synchronization with desktop OAuth, manual selection and opt-in auto-sync, offline iCalendar export, Spotify shortcuts, and an optional external AI provider. |
| 12 — Deployment | Expanded integration and GUI testing, executable JAR, final project report and UML, and source ZIP. |

## Completion feature matrix

| Area | Release behavior | Evidence and boundary |
| --- | --- | --- |
| Profile and recovery | One writer per profile. Strict JSON validation, atomic replacement, automatic previous copies and manual backup or restore for tasks, subjects, workspaces, notes, settings, attendance, attendance targets, focus and integration links. A damaged optional store is reported without overriding a successful core save. | `FileStorage`, `StudyFlowContext`, `ProfileRecoveryTest` and `JsonRepositoryTest`. Restoring subjects checks related attendance and material references. A full profile copy also carries PDF files. |
| Desktop shell | Grouped sidebar, global search shortcut, persistent light and dark themes, guarded form drafts, and dashboard counts built from profile records. Today and Upcoming open the corresponding task details. | `MainFrame`, `SidebarPanel`, `DashboardPanel`, `FormDialogs`, and Windows visual and workflow probes. |
| Planning and academic work | Task CRUD, typed tasks, subtasks, recurrence, combined filters, priority, Kanban, monthly calendar, subjects, workspaces, notes and grouped search. Focus records completed intervals. Local reminders surface due and overdue work. | Task, academic and reminder tests. Calendar deadlines and Kanban cards refer to existing task IDs. |
| Attendance | Dated class records, subject-specific targets, history corrections and recovery projections. | `AttendanceService`, `AttendanceMath` and attendance tests. Projection uses exact integer arithmetic and distinguishes an unreachable 100% target from a reachable one. |
| PDF library | Local PDF import with cancellation, page-linked evidence, ranked Unicode keyword search and repair of a missing, stale or invalid cache from the managed copy. | `MaterialLibrary`, `PdfPassageIndex`, `PassageRanker` and PDF retrieval tests. Scans can be stored; text search needs existing selectable text or prior OCR. |
| Study assistant | Local evidence selection and extractive study modes. Optional remote answer, summary and structured short-answer, long-answer or multiple-choice sets use explicitly approved snippets with page references. History can be disabled per request and supports deletion of individual entries. | `StudyAssistant`, `StudyQuestionFormat`, `QuestionSet`, and simulated provider and desktop consent tests. Generated material requires source review. |
| Analytics | Inclusive date filtering for recorded task completions, actual focus sessions and attendance. Separate current task inventory shows legacy completions with unknown dates without putting them in dated totals. | `AnalyticsService` and completion timestamp tests. The report has one latest timestamp per surviving root task and no invented legacy history. |
| Integrations | Offline `.ics` export, optional one-way Google Calendar sync with session-only access, and saved Spotify shortcuts opened in the system browser. Auto-sync is off by default and requires confirmation to enable. It can send completed and incomplete dated tasks while connected and retries temporary failures. After **Remove event**, auto-sync skips that task for the connected account until **Sync selected** sends it again. Clearing a deadline or deleting a local task leaves the linked remote event in place. | Calendar/export and provider transport tests use local fixtures or mocks. Live account behavior remains an external verification item. |
| Handoff | Maven source, runnable Java 21 JAR, project docs, viva guide, editable and rendered UML, and final verification evidence. | [Completion log](completion-log.md), [test guide](testing.md), and [privacy and limitations](privacy-and-limitations.md). |
