# StudyFlow viva guide

StudyFlow is an offline Java 21 desktop workspace for a university student. A task can link to a subject, the subject can belong to several workspaces, and notes and PDFs stay searchable through the same desktop. The application keeps its records in one local profile and offers explicitly configured external actions.

For a live demonstration, use an isolated profile. Create a subject, a workspace containing it, a dated task and a note. Open the task from Today or Calendar, complete it, and use Analytics to show its recorded completion. Restart StudyFlow and find the note again. Import a small text PDF, search for a term you know appears, and open the cited page. Offline exam revision and `.ics` export are enough to demonstrate the design with no account credentials.

| Examiner question | Answer and code to show |
| --- | --- |
| Where is inheritance useful? | `Task` supplies the shared lifecycle and deadline behavior. `Assignment`, `Exam` and `StudySession` add fields with different meaning. `TaskFactory.create` chooses the concrete subtype. A `Task` reference still calls the subtype's `getType` and `copy`. |
| Where is composition useful? | A `Task` owns an acyclic subtree and removes its descendants when deleted. A `Workspace` keeps subject IDs, allowing the same subject to be organized in more than one workspace. See `Task.addSubtask` and `Workspace.getSubjectIds`. |
| Why use MVC with Swing? | `TaskPanel` gathers input, `StudyFlowController` submits the command, and `TaskManager` validates the rule and persists the candidate. The EDT remains free to process UI events. Show the task creation sequence diagram. |
| Can you switch sorting without editing tasks? | `TaskSortStrategy` is a comparator contract. Choose `SortByDeadline` or another strategy when requesting a view, with the saved record order left intact. `Task.compareTo` supplies the natural deadline order. |
| What protects a record during a write? | `FileStorage` validates and stages a sibling file, archives the previous bytes, then replaces the live file atomically. `DataDirectoryLock` gives one StudyFlow process ownership of the profile. Recovery checks the candidate and its references before changing a live file. |
| Why use exact attendance arithmetic? | The display may show a rounded number, but `AttendanceMath` uses ceiling and floor to answer how many sessions actually meet the target. At 7 attended out of 10 with an 80% target, the next five classes must all be attended to reach 12 out of 15. |
| How does the PDF assistant find evidence? | PDFBox extracts text by page. `PdfPassageIndex` validates the local cache and `PassageRanker` scores Unicode token and phrase matches. The answer shows real file and page references. Optional provider calls use reviewed excerpts, and structured questions must cite a supplied source ID. |
| What happens when an old task has no completion date? | The `TaskRepository` still loads it and its current status is visible. `AnalyticsService` treats the missing timestamp as unknown and excludes it from date-filtered results. A new completion records the clock's instant. |
| How do you test external features without credentials? | Provider transport tests supply simulated responses and verify request and parser behavior. Local fixtures verify export and recovery, and an isolated Swing profile verifies user-visible workflows. A real provider connection is a separate check. |

Use the [Java concept mapping](java-concept-mapping.md) for syllabus examples and the [architecture and UML](architecture.md) for the package boundaries. The [test guide](testing.md) and [completion log](completion-log.md) record the verification evidence.
