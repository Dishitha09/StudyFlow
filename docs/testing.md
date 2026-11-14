# StudyFlow verification

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

The final Maven package run passed 129 tests with no failures, errors or skips.

## Defects resolved during verification

- Academic create forms initially failed on a missing prior record. Explicit create-path handling fixed the failure.
- Attendance target records initially serialized an extra ID property. A dedicated storage record fixed reload.
- The first dashboard and academic checks exposed narrow-layout behavior. The final pages use scrollable or reflowing content.
- This Windows runner failed to initialize Java networking during one isolated probe. Optional HTTP clients now initialize on demand, and no configuration is required to open local pages.
