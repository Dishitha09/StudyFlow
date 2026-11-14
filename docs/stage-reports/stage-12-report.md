# Stage 12 · Verification and packaging

## Objective and baseline

Package and validate the Stage 11 application for desktop use. Reviewed the Swing workflow, repositories, PDF processing, integration failure paths and the existing test suite.

## Implementation

- Updated the Maven build to produce an executable `StudyFlow.jar` with its runtime dependencies, service metadata and multi-release support.
- Added `AppLog` and lifecycle and operation logging. Log entries use fixed event names and exception types and omit document text and credentials.
- Updated the README, requirements, architecture, UML and testing notes. Added the project report and user manual sources and exported their PDF versions.
- Captured 12 actual Windows Swing screens from a synthetic profile.
- Prepared a release ZIP containing the source, screenshots, report, manual and executable JAR. Local profile data and temporary files are excluded.

## OOP and dependencies

The release retains the MVC, Repository, Strategy and adapter boundaries. Stage 12 added Maven Shade as a build plugin. It added no runtime libraries.

## Verification

- Maven packaged the JAR and passed 129 tests with zero failures or errors. The shaded artifact reports overlapping manifest and dependency metadata from bundled libraries. The manifest and service transformers produce a runnable artifact.
- Started `java -jar StudyFlow.jar` on Windows with a temporary profile. The application opened the dashboard, created its data directories and exited normally when the window closed.
- Earlier Windows smoke checks exercised task creation, edits and deletion, theme persistence, Calendar, Kanban, attendance, focus timing, PDF retrieval and local study output. The final screenshot run opened each of the 12 requested screens through the live main window.

## Limitations

Live Google Calendar and OpenAI operations require user-supplied credentials and were not verified against provider accounts. Google sync is explicit and reconnects per session. The app exports iCalendar offline and runs the local PDF assistant without accounts. OCR and embedded Spotify playback are outside the delivered scope.
