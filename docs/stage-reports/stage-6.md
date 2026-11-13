# Stage 6 · Local JSON persistence

## Objective

Persist the academic workspace across application launches and support safe recovery of previous data.

## Implementation

Jackson-backed repositories use separate UTF-8 JSON files for tasks, subjects, workspaces, notes and settings. `StudyFlowContext` acquires the profile lock, loads and validates records and wires the existing managers. Updates stage a complete file, back up the preceding valid version and atomically replace the destination. The recovery command validates an explicit backup against the other current records before restoring it. Four Swing verification tabs call the real services through one managed background executor. Closing the window drains queued work and releases the lock.

## Files created

- `repository/` JSON repositories, mapper configuration, file storage and storage errors, plus the collection repository contract.
- `config/StudyFlowContext.java`, `config/DataDirectoryLock.java`, `controller/StudyFlowController.java` and `view/VerificationPanel.java`.
- `integrations/package-info.java` for the future adapter boundary.
- Repository and context tests, and isolated educational tests for patterns and thread lifecycle.
- `docs/stage-reports/stage-6.md`.

## Files modified

- `pom.xml`, `Main.java`, `MainFrame.java` and `README.md`.
- Architecture, component UML, development log and Java concept mapping.

## Java concepts and patterns

The stage uses Java NIO file channels, checked exceptions, try-with-resources, typed repository adapters, a managed `ExecutorService`, EDT callbacks, synchronization and an anonymous window listener. Educational tests demonstrate priorities, Singleton, Abstract Factory and Decorator in isolation.

## Testing

```powershell
mvn clean compile
mvn test
mvn exec:java '-Dexec.mainClass=com.studyflow.Main'
```

- All 62 JUnit tests passed with zero failures, errors or skips. The 18 persistence and context tests covered the five file types, task subtypes, corrupt or missing files, backup restoration, invalid relationships, lock contention and writes through an already-closed profile.
- The exact Maven Exec goal opened a visible Windows window and exited with code 0 after a normal close.
- A separate two-process Swing test created one subject, one linked task, one workspace and one note through the actual controls, closed the app, and reopened it. The visible details retained the subject links, deadline and note body.
- A Maven CLI restore used a data path containing spaces. It restored the exact backup bytes and preserved the damaged original separately.

## Issues and decisions

The file adapter requires atomic replacement support and reports the affected path when a commit cannot be made safely. Recovery is per file and preserves the previous bytes. Invalid and damaged data stops a writable startup. The desktop shell verifies creation and saved details for the four academic record types. Full workspace management screens remain in Stage 7.

## Deliverables and next stage

The local repositories, backup and restore workflow, application integration and verification interface complete the Stage 1–6 scope. Stage 7 can build on the existing manager APIs and controller boundary for full task, workspace, notes and search screens.
