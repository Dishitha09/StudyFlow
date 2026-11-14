# StudyFlow development log

## Week of 9 November 2026

### 13 November · Stage 1

- Inspected the new workspace. It contained empty `outputs` and `work` directories and no existing Git repository or application.
- Established the requirements baseline for Stages 1–6 and mapped future modules to Stages 7–12.
- Defined the MVC and persistence boundaries, entity ownership, recurrence behavior, query semantics and recovery rules.
- Prepared editable and rendered UML for the design review.

### 13 November · Stage 2

- Verified Java 21 and Maven 3.9.16 from workspace-local distributions. Configured the Maven build for Java 21 and added FlatLaf, Jackson and JUnit Jupiter.
- Created the Swing startup path, light-themed window, sidebar and Overview. Documented prerequisites and terminal commands.

### 13 November · Stage 3

- Added the task hierarchy, academic entities, settings, common contracts and reusable validation.
- Defined defensive copying for editable tasks and nested subtasks. Added an isolated Java language demonstration and updated the class diagram against the model API.

### 13 November · Stage 4

- Added the independent task manager, typed task factory, quick-add path, filter criteria and sort strategies. Defined urgency bands and kept root task views separate from subtask operations.
- Added daily, weekly and monthly recurrence with an advancement marker on the predecessor. One storage commit publishes the completed task and its successor.

### 13 November · Stage 5

- Added subject, workspace and note services with reference validation and detached records. Workspace membership supports independent subject lifecycles.
- Added categorized global search across the three searchable record types. Subject deletion identifies dependent tasks, notes and workspaces.

### 13 November · Stage 6

- Connected the task, subject, workspace, note and settings snapshots to Jackson-backed JSON files. Added previous-file backups and a validated restore command.
- Added a data directory lock, a managed serial executor for file work and four lightweight verification tabs.

### 13 November · Stage 6 continuation

- Audited the existing persistence and verified the 62-test baseline. Added a manual backup command using the existing profile lock, typed repository checks and atomic file writing.
- Passed 71 tests, verified command-line backup and recovery with a spaced directory path, and repeated the two-process Swing restart check. Updated the requirements, architecture, use-case diagram and Stage 6 report.

### 14 November · Stage 7

- Built the main dashboard, task management, academic pages, navigation and persistent theme settings. Fixed academic create forms and verified responsive layouts.
- Passed 75 tests and a two-process Windows Swing smoke check. Updated the README, requirements, architecture and UML.

### 14 November · Stage 8

- Added Calendar, Kanban, Focus with saved sessions, in-app reminders, global search, recurrence and subtask controls.
- Passed 80 tests and exercised the calendar and Kanban controls in a native Swing probe.

### 14 November · Stage 9

- Added subject attendance, correction history, custom targets and exact projections. Fixed target JSON serialization during integration and passed 97 tests.
