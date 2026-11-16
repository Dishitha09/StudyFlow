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
2. Open **Workspaces** and add a workspace for a semester, project, or set of classes. Select the subjects it should contain. Open a saved workspace to browse the subjects and the tasks and notes linked to them.
3. Open **My Tasks** and choose **Add task**. Add a title and optional subject, priority, and deadline. Choose General, Assignment, Exam, or Study Session for the relevant fields. Enter deadlines as `YYYY-MM-DD`.
4. Open **Notes** to capture lecture notes, revision points, or ideas. Choose a related subject and add tags if useful. **Save note** or **Ctrl+S** writes it to `notes/notes.json` inside your local profile. Search the saved list to find it later.

The Dashboard shows today's tasks, upcoming deadlines, Smart Priority, and task counts. Select **Complete** beside a due-today task to update it there. In **Settings**, select Light or Dark to save your appearance preference and enable or disable task reminders.

<!-- pagebreak -->

## Plan and track your work

### Manage tasks and use the planning views

- **My Tasks.** Select a task for its details and status or priority controls. Use **Edit** to change fields and **Delete** to remove the task and its subtasks after confirmation. Choose search terms and filters, then select **Apply**. Use **Clear filters** to restore the full listing.
- **Subtasks and recurrence.** Open the selected task's subtask and recurrence controls. Add smaller steps and complete them independently. Set daily, weekly, or monthly recurrence on a root task with a deadline. Completing it creates the next occurrence.
- **Calendar.** Navigate by month, filter by subject, and select a date. Choose **Edit task** to change the deadline or assign a different subject. **Edit subject** opens the linked course for editing.
- **Kanban Board.** The progress chart shows the share of tasks in each status and the number overdue. Cards show priority, type, and deadline. Move a task between **To do**, **In progress**, and **Completed** using the card controls.
- **Search.** Enter words such as a course name plus a topic. Results are labeled by record type. Choose **Open result** or press Enter on a selected result to go to the record.
- **Focus and Analytics.** Choose a focus or break preset, or type a custom length. Press **Ctrl+Space** to start, pause, or resume from the Focus panel. Link focus time to a task or subject, and use **Finish early** to record time spent. Analytics summarizes tasks, attendance, and completed focus sessions.

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
3. Choose **Study this PDF** or open the **Study Assistant** tab. Select a topic and mode, then select **Run assistant**. Modes include summaries, key points, formulas, revision notes, and topic explanation.
4. Review the file and page references alongside the study output. Use **Open source PDF** to check a passage in the original context.

**History** keeps the latest 50 saved requests and outputs in the current StudyFlow profile. Select an entry to read the output and its source references. **Use this request** fills the assistant form without running it. If you want to skip saving an output, turn off **Save in local history** before running it. **Clear history** removes the saved requests and outputs.

StudyFlow extracts existing PDF text. Image-only scans may need OCR before uploading. The local formula view looks for equation-like text in retrieved passages.

### Optional connections

- **Google Calendar.** From **Calendar**, choose **Google Calendar**. Select dated tasks and choose **Export .ics** for a file you can import without connecting StudyFlow. For live sync, choose **Setup help** and configure a desktop OAuth client before launching StudyFlow. Choose **Connect Google**, authorize in the browser, then sync selected deadlines to the primary calendar. Sync again after editing a task or subject to update its linked event. To remove an event from both places, use **Remove event** before deleting the StudyFlow task. Google-side edits do not flow back to StudyFlow. Reconnect after restarting.
- **Spotify.** Paste an `https://open.spotify.com/` link in **Integrations**, choose **Save link**, then **Open playlist** to launch the saved item in your browser.
- **OpenAI.** Set `OPENAI_API_KEY` before launching StudyFlow. The model defaults to `gpt-6-luna` and can be changed with `STUDYFLOW_OPENAI_MODEL`. **Test provider** checks model access. Choosing **Use configured AI provider** and consenting sends selected source passages and your request. Leave it off for local study.

### Backups and recovery

Close StudyFlow before making or restoring a backup. Choose the JSON record type you are protecting. The application automatically preserves the previous valid version when updating a record file. Run the backup command to preserve the latest version explicitly.

```powershell
java -jar StudyFlow.jar --backup tasks
java -jar StudyFlow.jar --restore tasks "data/backups/backup.json"
```

Replace the example backup path with a file printed by the backup command or an earlier backup from `data/backups`. Add `--data-dir "C:\StudyFlow data"` before `--backup` or `--restore` for a non-default profile. Restoration checks references before replacing a record file, and preserves the file it replaces. Restart StudyFlow after a successful restore. For a whole-profile copy, close the app and copy the complete profile directory, including managed PDFs and their index.
