# Stage 6 report · Persistence and manual backup

## Objective

Verify the existing local persistence against the continuation brief and add an on-demand backup that works with the existing recovery command.

## Existing code inspected

The project began at commit `7d05987` with a clean working tree. The Stage 1–5 reports, Maven configuration, model and service packages, repository contracts, Swing entry point, and tests confirmed that the previous stages were present. The persistence layer already had five typed JSON repositories, immediate saves, startup validation, automatic backup of previous versions, and validated per-file recovery. Before changes, a fresh offline build with OpenJDK 21.0.13 and Maven 3.9.16 passed all 62 tests. See the [initial Stage 6 report](stage-6.md) for the original implementation.

## Features implemented

- The `--backup` command backs up a saved task, subject, workspace, note or settings file and prints the absolute backup path. An optional `--data-dir` selects the profile.
- `StudyFlowContext.backup` acquires the existing profile lock, reads all five repositories and validates their references. It then asks the selected repository to back up its current snapshot.
- The repositories apply their usual typed validation. `FileStorage.backup` writes the exact validated bytes to a new timestamped file using the same temporary write and atomic move as automatic backups. Existing live files and backups are preserved.

## Files created

- `docs/stage-reports/stage-6-report.md`

## Files modified

- `Main.java` and `config/StudyFlowContext.java`
- `repository/FileStorage.java`, `JsonCollectionRepository.java` and `SettingsRepository.java`
- `src/test/java/com/studyflow/config/StudyFlowContextTest.java`
- `README.md`, the SRS, architecture, design decisions, development log and use-case UML source and PNG

## OOP concepts and dependencies

The backup uses the same repository abstraction as normal persistence. The collection repository shares the implementation across the four collection types, and the settings repository supplies its own typed decoder. `FileStorage` encapsulates byte-level operations. Try-with-resources keeps the storage lifecycle and profile lock scoped to the command. This stage added no dependencies.

## Tests performed and results

- An offline `mvn clean test` on Windows compiled 57 main source files and passed **71 tests** with zero failures, errors or skips. Nine added cases cover first-save backups of all five data types, exact bytes, unique names, restore, missing files, corrupt profiles and lock contention.
- A Windows Maven CLI smoke test backed up a fixture profile in a directory with spaces, restored the manual backup and verified that the damaged original was preserved.
- A two-process Swing smoke test created a subject, a linked task, a workspace and a note in an isolated profile. A second launch displayed the same records and subject links.
- PlantUML syntax checks passed, and the updated use-case diagram was rendered and visually checked.

## Known limitations

Manual backup and recovery operate on one data type per command. The profile must be closed so the command can acquire its lock. A manual backup requires a saved file and a valid profile. The destination filesystem must support atomic moves.

## Next-stage dependencies

Stage 7 can build the dashboard and navigation on the current task, subject, workspace, note and settings services. It can reuse the Swing shell, background executor and JSON repositories. Stage 7 is awaiting approval.
