# StudyFlow completion log

## Phase 0 · Audit and baseline

- Files: `docs/completion-audit.md` and this log. No production changes.
- Why: established a recoverable base, separated reproduced defects from static limitations, and checked the actual test count.
- Verification: JDK/Javac 21.0.13.0.2, Maven 3.9.16, Windows 11. Clean verify and package succeeded; Surefire XML: 133 tests with zero failures, errors or skips. The standalone JAR opened its native window and shut down with an isolated profile.
- Gate: passed. Begin Phase 1 with restore reference validation, optional snapshot isolation and managed PDF deletion consistency.
- Gap: live Google/OpenAI calls were not run; the baseline screenshot predates this completion pass.

## Phase 1 · Reliability and data integrity

- Files: `StudyFlowContext`, `Main`, `StudyFlowController`, `LocalIntegrations`, `MaterialLibrary`, the material and integration panels, `MainFrame`, `README.md`; regressions in `ProfileRecoveryTest`, `StudyFlowControllerTest` and `MaterialLibraryTest`.
- Why: protect references during subject restore; repair core save/search reporting when optional files fail; stage PDF deletion before committing its index entry. Add CLI recovery for attendance, targets, focus and integration links, while keeping history clear deletion free of automatic backups.
- Compatibility: no schema migration. Subject restore validates attendance and material dependencies. Focus history may retain links to records later removed. Damaged optional bytes are retained and shown as unavailable. Normal academic startup emits fixed event codes and error types.
- Verification: Windows Java 21 `mvn -B -o clean verify`: **143 tests passed**, no failures/errors/skips. One subsequent test raced 12 concurrent note inserts and checked durable notes and 11 distinct preimages; the final `ProfileRecoveryTest` ran 8/8 separately. Reproduced PDF/restore/optional-save issues before fixes on synthetic profiles and exercised rollback/persistence afterward.
- Gate: passed. Begin Phase 2. Follow-up: a process kill between filesystem operations is distinct from a reported I/O error; staged PDF bytes are retained for recovery. No live account was used.

## Phase 2 · Desktop design system

- Files: `docs/design-system.md`, `ViewStyle`, `SidebarPanel`, `MainFrame`, `FormDialogs`, task/academic/subtask/PDF forms and `tools/qa/VisualSmoke.java`.
- Why: unified light/dark UI roles, grouped navigation and Ctrl+K search; guarded Escape and window-close behavior keeps unfinished edits accessible. No record format change.
- Verification: Java 21 clean verify, **144 tests passed**, zero failures/errors/skips. Native Swing probes confirmed all 13 routes, Ctrl+K query focus, dark theme contrast roles, form submission and draft retention. The 192px compact sidebar fixed clipped text; see [compact light](screenshots/completion/phase2-shell-light-1024.png) and [wide dark](screenshots/completion/phase2-shell-dark-1366.png).
- Gate: passed. Phase 3 replaces dashboard arrangement and improves direct actions.
