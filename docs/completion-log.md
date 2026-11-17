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

## Phase 3 · Dashboard

- Files: `DashboardPanel`, `DashboardTaskList`, `SummaryCard`, `MainFrame`, `StudyFlowController`, `tools/qa/VisualSmoke.java`, and `docs/screenshots/completion/dashboard-*.png`.
- Why: place Today's Plan and Upcoming together, surface five real counts, open rows directly, keep one page scroll and make empty states actionable. The date tick refreshes task counts after midnight.
- Verification: **144 tests passed** on a clean Java 21 build. The populated dashboard opened task details, completed a real fixture task, expanded its priority list and kept status/deadline labels readable in a compact frame. Light and dark client captures at 1024×700, 1366×768, 1440×900 and 1920×1080 use a synthetic three-course profile with seven tasks, five focus records and twelve class records. This 125% host limits the native client to 1540×932; 1920×1080 is a logical-size paint of the same running Swing view.
- Gate: passed. Phase 4 targets cross-page workflows, restart and input feedback.

## Phase 4 · Academic workflows

- Files: `AttendancePanel`, `FocusPanel`, `SearchPanel`, `MainFrame`, `ReminderServiceTest` and `tools/qa/{WorkflowSmoke,VisualSmoke}.java`.
- Why: retain and correct attendance input inline; warn on closing with an unfinished focus interval; describe PDF search coverage. Preserve TaskManager as the owner of task state.
- Verification: **145 tests passed** on a clean Java 21 build. A native Swing probe used three temporary profiles for: assignment creation, search/linking and completion across shared views; workspace membership and note creation/edit/search; attendance add/correct/target and focus start/pause/resume/finish. All three reopened with saved records. The attendance probe rejected an invalid date without dropping the edit. [Attendance](screenshots/completion/attendance-light-1366x768.png) and [Focus](screenshots/completion/focus-dark-1366x768.png) use synthetic records.
- Gate: passed. Advanced PDF retrieval and study questions/history are checked in Phase 5.

## Phase 5 · PDF library and Study Assistant

- Files: `MaterialLibrary`, `PdfPassageIndex`, `PassageRanker`, `StudyAssistant`, `StudyQuestionFormat`, `QuestionSet`, `StudyHistoryEntry`, `AIProvider`, `OpenAiProvider`, material/history views, shared desktop wiring and targeted tests.
- Why: use Unicode whole-word relevance and overlapping source excerpts; recover a missing or stale index; allow progress/cancel before committing an import. Keep offline tools extractive. Use a frozen, reviewable request for remote AI, validated structured question/answer data and exact source IDs. History can remove individual rows.
- Verification: **170 tests passed**, no failures/errors/skips. PDF tests cover rankings, real source pages, OCR empty states, cache repair and cancellation. Mock provider tests cover all three question types, malformed results, refused outputs, answer and citation validation, and no duplicate history on retry. A native consent probe verified Cancel sends nothing and Send transmits the reviewed request. The packaged desktop probe imported a two-page fixture, found page 2, created offline revision notes and opened History in a reopened profile.
- Screenshots: [Text matches](screenshots/completion/pdf-text-matches.png), [Local assistant](screenshots/completion/study-assistant-local.png), [Reopened history](screenshots/completion/study-history-reopened.png).
- Gate: passed. The configured live AI service was not exercised. Phase 6 adds visual analytics using saved records.

## Phase 6 · Visual analytics

- Files: `AnalyticsService`, `AnalyticsPanel`, task model/manager/repository, main desktop wiring and QA tools. New `TaskCompletionTimeTest` and `AnalyticsServiceTest`.
- Why: report current task inventory separately from activity in an inclusive date range. Graph task completions, actual focus minutes, subject study time and recorded class attendance against targets. Legacy records without completion dates are not assigned guessed dates; the optional timestamp is omitted when unknown. No startup rewrite.
- Verification: **177 tests passed**, no failures/errors/skips. Tested real persisted completion, duplicate updates, backups and recurrence, timezone boundaries, focus totals, and empty attendance. The native journey sets a custom date range excluding the new completion and checks that current inventory still shows it. The compact chart layout was repaired and rechecked in both themes at all four dimensions.
- Screenshots: [light 1366×768](screenshots/completion/analytics-light-1366x768.png), [compact dark](screenshots/completion/analytics-dark-1024x700.png), [logical 1920×1080 dark](screenshots/completion/analytics-dark-1920x1080.png).
- Gate: passed. Per-day/week activity counts the latest known completion for each surviving top-level task, independently of its current status. Phase 7 exercises the external boundaries with mocks.

## Phase 7 · Integration and file safety

- Files: `GoogleCalendar`, `CalendarDetails`, `CalendarExport`, `LocalIntegrations`'s desktop wiring, `IntegrationsPanel`, managed PDF path checks, `GoogleCalendarLifecycleTest`, `CalendarAndSpotifyTest` and `tools/qa/ManagedPathSmoke.java`.
- Why: discard expired or revoked calendar sessions, support browser-sign-in cancellation, preserve event ownership and all-day boundaries, and keep redirected PDF directories outside managed writes. Credentials remain session/environment only.
- Verification: **181 tests passed**, zero failures/errors/skips. A local loopback OAuth simulation verified state and PKCE; a fake transport verified create/update without duplication, ownership and idempotent delete, token refresh, revocation and reconnect. An independent fixture parser checked escaped text and inclusive start/exclusive end of an all-day `.ics` event. The Windows junction smoke confirmed refusal before any external writes. [Settings integrations](screenshots/completion/integrations-light-1366x768.png) is a real unauthenticated UI capture.
- Gate: passed for mocked and offline behavior. Live Google and OpenAI checks were not performed.
