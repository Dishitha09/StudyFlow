# StudyFlow design decisions

## D01 · Java 21, Swing and FlatLaf

Java 21 is the project baseline. Swing and AWT provide portable desktop widgets and event handling with the JDK. Swing also lets the project demonstrate the course's GUI and event model directly. FlatLaf modernizes the stock components without adding a separate UI framework. The first window uses a light theme, a compact sidebar and a central content area. Stage 6 adds only the forms and lists needed to verify real record creation and reload. Full navigation and dashboard design are Stage 7 work.

## D02 · Maven and JUnit 5

Maven supplies the standard source layout and explicit dependency and plugin versions. A compiler release of 21 makes the required Java language level clear. Surefire runs JUnit Jupiter and Exec launches `com.studyflow.Main`. Domain and service tests use fixed clocks and isolated collections. Persistence tests use temporary data directories. First-time dependency resolution uses Maven Central. The core application runs locally after dependencies are available.

## D03 · Local JSON and Jackson

One file per record type keeps a student's data readable and makes the persistence layer small enough for this scope. Jackson handles binding and Java Time values with explicit type and date configuration. Persisted subject relationships use IDs. This resolves the brief's `Task.subject` object field and its requirement to avoid repeating subject details. JSON snapshots are appropriate for a single student's dataset. Repository contracts allow a future storage engine to replace them if volume or query demands increase.

## D04 · MVC, repositories and a serial application worker

Views deal with Swing widgets, controllers coordinate use cases, and services own academic rules. Persistence stays behind repository contracts. Dependency injection keeps the same service testable with an in-memory repository. A single background worker serializes UI commands and keeps file I/O off the EDT. A data directory lock protects file snapshots from concurrent application instances. Constructors and explicit lifecycle ownership keep dependencies visible.

## D05 · Ownership and safe deletion

Subject membership in a workspace is organizational. A workspace can be removed independently and a subject can belong to multiple workspaces. Task and note associations are optional. Removing a referenced subject is blocked until the student reassigns or clears references and workspace memberships. This keeps the update in one file and preserves associated records. A task owns its subtasks, so removing a root or child also removes its descendants. A workspace contains a flat subject list during this scope. Task ownership is a real recursive hierarchy and already exercises tree traversal. A generic workspace Composite is useful when folder nodes exist.

## D06 · Date, priority and lifecycle semantics

Dates use the machine's local day, obtained from an injectable clock. Past deadlines are accepted for overdue or historical work. Invalid calendar dates and reversed ranges are rejected. Upcoming excludes today and includes the next seven days. Smart Priority prioritizes urgency bands first, then priority, then the deterministic deadline order. A completed task can reopen to `TODO`. An explicit transition can then move it to `IN_PROGRESS`. Setting the same status again has no effect.

The first completion of an enabled recurring root task creates a new occurrence in the same task commit. Each occurrence advances one interval from its previous due date. A monthly occurrence on January 31 advances to February's last day and the next month advances from that new day. Descendants receive fresh IDs and reset status. Successors and cloned descendants preserve assignment submission metadata and attachments, clear actual study durations, and reset exam preparation. An advancement marker survives reopening and prevents duplicate successors. Recurrence is event-driven during this scope, with no timer required.

## D07 · Text search and query boundaries

Search trims whitespace, normalizes case with `Locale.ROOT` and matches all whitespace-delimited tokens as partial strings across the applicable fields. Task search also uses current subject name and tags. Subjects use name, code, description and tags. Notes use title, content and tags. A blank global search returns empty categories. Retrieval lists provide the full collections. Queries operate on snapshots, so a sort leaves stored order intact.

## D08 · Backup and recovery

Stage 1 lists backup and restore among future data capabilities and Stage 6 explicitly requires a basic implementation. Stage 6 provides timestamped previous-file backups and an explicit single-file restore path. Whole-profile restore and retention scheduling can be added later. Existing empty, malformed or invalid data blocks normal startup with a useful error and is preserved. Missing files default to empty data or settings only when the complete relationship graph remains valid. A failed restore leaves the original in place. Writes use a verified candidate and temporary replacement, with a backup made before replacing a valid current file.

## D09 · Course examples and future integrations

Production classes demonstrate OOP and patterns where they serve a feature. Demonstrations for concepts such as field hiding, thread priorities and legacy text utilities live separately, with a viva-oriented mapping. The integration layer has a package boundary for future adapters. Calendar OAuth, Spotify and LLM clients will depend on application-facing contracts and use independently managed credentials when those stages arrive.
