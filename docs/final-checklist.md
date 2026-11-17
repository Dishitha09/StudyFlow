# StudyFlow 0.2.0 submission checklist

| Acceptance | Evidence and status |
| --- | --- |
| A · Build and tests | Pass. Java 21 clean verify; 182 tests in 26 Surefire XML reports. |
| B · Runnable desktop JAR | Pass. Native JAR opened and closed on Windows 11. |
| C · Create/edit/restart | Pass. Separate isolated profiles for task/subject, workspace/note and attendance/focus. |
| D · Both themes and resizing | Pass at tested client sizes. The 1920×1080 and constrained 200% captures use actual Swing views at logical sizes; see [test results](testing.md). |
| E · Navigation and actions | All 13 destinations rendered. Native workflow and form probes sample critical actions; not an exhaustive click-through of every option. |
| F · Shared task state | Pass. Assignment appeared in Dashboard, Calendar, Kanban and Search, then as completed in Analytics and after restart. |
| G · Question generator | Short-answer, long-answer and MCQ mock responses validated; Cancel sent nothing. Live model behavior remains unverified. Offline extracts are labeled. |
| H · PDF references | Pass. Synthetic PDF uploaded, exact page 2 retrieved, studied offline, and reopened as history. |
| I · Focus and attendance | Pass. Boundary calculations and saved correction, target and session verified. |
| J · Calendar safety | Pass with simulation. Repeated sync updated one event; ownership, refresh and revocation tested. |
| K · Package and docs | Runnable Java 21 JAR, source ZIP and unsigned Windows runtime app image; see README for launch details and limitations. |
| L · Phase reporting | [Audit](completion-audit.md), [log](completion-log.md), [SRS](SRS.md) and [privacy limits](privacy-and-limitations.md). |

For a viva, use the [viva guide](viva-guide.md), [component diagram](uml/component-diagram.png) and [study sequence](uml/study-assistant-sequence.png). Demonstrate on a separate profile. Keep data and credentials out of submitted archives.
