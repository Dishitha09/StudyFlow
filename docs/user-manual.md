# StudyFlow user manual
## A guide to the desktop academic workspace

### Start StudyFlow

Install a Java 21 JDK, then confirm that `java -version` reports Java 21. Open a terminal in the release directory and run the executable JAR. Use a graphical desktop session so Swing can open the window.

```powershell
java -jar StudyFlow.jar
```

If you have the source project and Apache Maven, run these commands from the folder containing `pom.xml`.

```powershell
mvn test
mvn exec:java '-Dexec.mainClass=com.studyflow.Main'
```

StudyFlow normally uses the `data` directory under the working directory. Start it with `--data-dir` to choose a specific profile location, such as when moving to another working folder or keeping a separate practice profile.

```powershell
java -jar StudyFlow.jar --data-dir "C:\StudyFlow data"
```

### Set up your workspace

1. Open **Subjects** and choose **Add subject**. Enter a name and course code. Add a description and tags if you plan to search by them.
2. Open **Workspaces** and add a workspace for a semester, project, or set of classes. Select the subjects it should contain.
3. Open **My Tasks** and choose **Add task**. Add a title and optional subject, priority, and deadline. Choose General, Assignment, Exam, or Study Session for the relevant fields. Enter deadlines as `YYYY-MM-DD`.
4. Open **Notes** to capture plain-text study notes. Choose a related subject and add tags if useful.

The Dashboard shows today's tasks, upcoming deadlines, Smart Priority, and task counts. Select **Complete** beside a due-today task to update it there. In **Settings**, select Light or Dark to save your appearance preference and enable or disable task reminders.

<!-- pagebreak -->

## Plan and track your work

### Manage tasks and use the planning views

- **My Tasks.** Select a task for its details and status or priority controls. Use **Edit** to change fields and **Delete** to remove the task and its subtasks after confirmation. Choose search terms and filters, then select **Apply**. Use **Clear filters** to restore the full listing.
- **Subtasks and recurrence.** Open the selected task's subtask and recurrence controls. Add smaller steps and complete them independently. Set daily, weekly, or monthly recurrence on a root task with a deadline. Completing it creates the next occurrence.
- **Calendar.** Navigate by month and select a date to see its scheduled tasks. Calendar deadlines use the same dates as My Tasks.
- **Kanban Board.** Move a task between To do, In progress, and Completed using the board's controls. Reopen a completed task to To do before starting it again.
- **Search.** Enter words such as a course name plus a topic. Results are labeled by record type. Choose **Open result** or press Enter on a selected result to go to the record.
- **Focus and Analytics.** Start a focus interval from the dashboard or navigation. Pause, resume, or finish it early. Analytics summarizes tasks, attendance, and completed focus sessions. Reminders are shown during an active StudyFlow session.

### Track attendance

1. Create the course in **Subjects** if it does not already exist.
2. Open **Attendance**, select the course and choose the class date.
3. Select **Add attended** or **Add absent** for each class. Use **Correct selected** to fix an existing entry.
4. Use **Change target** to set the required percentage for the subject. Check the forecast for additional absences or classes needed to regain the target.

An exact 100 percent target cannot be regained after a missed class. Attendance entries remain linked to their subject, so remove or resolve linked records before attempting to delete the subject.

<!-- pagebreak -->

## Study materials and integrations

### Work with PDFs

1. Open **Study Materials**, then select **Upload PDF**. Pick a PDF with selectable text and enter its title and optional subject and description.
2. Use **Search** to look up a saved document by title or metadata. Select **Open PDF** to open the managed copy with the computer's default PDF viewer.
3. Choose **Study this PDF** or open the assistant from Study Materials. Select a topic and mode, then select **Run assistant**. Modes include summaries, key points, formulas, revision notes, questions, and explanation.
4. Review the file and page references alongside the study output. Use **Open source PDF** to check a passage in the original context.

StudyFlow extracts existing PDF text. Image-only scans may need OCR before uploading. The local formula view looks for equation-like text, and the local question mode uses prompts and answer cues from relevant passages.

### Optional connections

- **Google Calendar.** In **Settings**, open **Integrations**. Select dated tasks and choose **Export .ics** for a local calendar file. For live sync, configure a Google desktop OAuth client with `STUDYFLOW_GOOGLE_CLIENT_ID` and `STUDYFLOW_GOOGLE_CLIENT_SECRET` before launching StudyFlow. Choose **Connect**, authorize in the browser, and sync selected tasks to the primary calendar. Reconnect each time you restart StudyFlow.
- **Spotify.** Paste an `https://open.spotify.com/` link in **Integrations**, choose **Save link**, then **Open playlist** to launch the saved item in your browser.
- **OpenAI.** Set `OPENAI_API_KEY` before launching StudyFlow. The model defaults to `gpt-6-luna` and can be changed with `STUDYFLOW_OPENAI_MODEL`. **Test provider** checks model access. Choosing **Use configured AI provider** and consenting sends selected source passages and your request. Leave it off for local study.

### Backups and recovery

Close StudyFlow before making or restoring a backup. Choose the JSON record type you are protecting. The application automatically preserves the previous valid version when updating a record file. Run the backup command to preserve the latest version explicitly.

```powershell
java -jar StudyFlow.jar --backup tasks
java -jar StudyFlow.jar --restore tasks "data/backups/backup.json"
```

Replace the example backup path with a file printed by the backup command or an earlier backup from `data/backups`. Add `--data-dir "C:\StudyFlow data"` before `--backup` or `--restore` for a non-default profile. Restoration checks references before replacing a record file, and preserves the file it replaces. Restart StudyFlow after a successful restore. For a whole-profile copy, close the app and copy the complete profile directory, including managed PDFs and their index.
