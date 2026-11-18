# StudyFlow verification

## Release 0.2.1

Maven verification passed **192 tests**. Mock Google responses covered dated task creation and edits, reconnect catch-up, retry without duplicates, cancellation, manual removal, and loading profiles from before auto-sync. A Swing probe checked confirmation, persistence across reopen, the paused state while signed out, and turning sync off. No real Google account was used.

[Integration settings in light mode](screenshots/calendar-auto-sync/settings-light-1024x700.png) and [dark mode](screenshots/calendar-auto-sync/settings-dark-1366x768.png) show the new option with sample task data.

## Release 0.2.0

Windows 11 x64, Microsoft OpenJDK/Javac 21.0.13.0.2, Maven 3.9.16. `mvn -B -o clean verify` succeeded. Summing 26 Surefire XML reports: **182 tests, zero failures, errors or skipped**. The shaded JAR and unsigned `jpackage` app image opened and closed using isolated profiles.

The desktop workflow probe tested four synthetic create/edit/restart journeys, with task completion across views, workspace/note links, attendance/focus tracking, and PDF page 2 retrieval with local output and reopened History. External Google and AI responses were simulated; no live account was used. A Windows junction probe blocked a redirected PDF storage folder before writes.

Both themes rendered all 13 destinations. The normal desktop was at 125%; Java process overrides reached 100%, 150% and 200% without changing system settings. The monitor clamped the native 1920×1080 and the 200% 1024×700 cases, so those files show the real Swing views painted at the stated logical client sizes.

```powershell
mvn -B clean verify
java -jar target/StudyFlow.jar --data-dir "C:/Temp/studyflow-manual-qa"
java -cp target/StudyFlow.jar tools/qa/WorkflowSmoke.java
java -cp target/StudyFlow.jar tools/qa/VisualSmoke.java "C:/Temp/studyflow-screens" all
java '-Dsun.java2d.uiScale=2.0' -cp target/StudyFlow.jar tools/qa/VisualSmoke.java "C:/Temp/studyflow-dpi" dpi
.\tools\package-windows.ps1 -Destination "C:/Temp/studyflow-image"
```

Use a fresh writable profile for each destructive or recovery probe. The [completion log](completion-log.md) links actual screenshots and fixture provenance. The [final checklist](final-checklist.md) separates verified, simulated and unverified behavior.

## Historical Stage 12 results

## Strategy and results

The Maven suite uses fixed clocks, isolated temporary profiles, generated PDF fixtures, and a simulated HTTP transport. Swing probes use actual components and synthetic records. The desktop restart check runs two Java processes against the same temporary profile.

| Area | Expected behavior | Observed result |
|---|---|---|
| Domain, tasks and academic records | Valid edits persist and invalid edits preserve prior data | Unit and repository tests passed |
| Persistence and recovery | Preserve valid JSON and recover from a selected file backup | Automated tests and Windows CLI backup and restore passed |
| Dashboard and main GUI | Verify create, search, status, delete, resizing, restart and saved theme | Windows Swing smoke passed |
| Planning | Calendar and board reflect deadlines and status. Focus counts active time and saves a finished interval once | Swing probes and deterministic timer tests passed |
| Attendance | Show exact classes-to-attend and classes-that-can-be-missed thresholds and preserve corrections | Attendance tests and narrow Swing probe passed |
| Materials | Retrieve original PDF pages from cached text and reject malformed files | PDF test fixtures and a Swing probe passed |
| Optional integrations | Produce valid offline calendar files, validate Spotify links, and handle AI API response and error paths | Offline and simulated transport tests passed. Live service authentication and remote changes were not exercised |
| Executable release | Open the dashboard with Java 21, create an isolated profile and shut down normally | Windows executable JAR smoke passed |

The original Stage 12 Maven package run passed 129 tests with no failures, errors or skips.

## Defects resolved during verification

- Academic create forms initially failed on a missing prior record. Explicit create-path handling fixed the failure.
- Attendance target records initially serialized an extra ID property. A dedicated storage record fixed reload.
- The first dashboard and academic checks exposed narrow-layout behavior. The final pages use scrollable or reflowing content.
- This Windows runner failed to initialize Java networking during one isolated probe. Optional HTTP clients now initialize on demand, and no configuration is required to open local pages.
