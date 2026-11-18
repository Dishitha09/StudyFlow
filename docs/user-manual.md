# StudyFlow user manual
## A guide to the desktop academic workspace

### Start StudyFlow

For the Windows x64 bundle, extract the complete archive and double-click `StudyFlow.exe` with its `app` and `runtime` folders intact. The unsigned bundle comes with Java. If you downloaded the portable JAR, install Java 21, confirm that `java -version` reports it, then open a terminal in the extracted folder:

```powershell
java -jar StudyFlow.jar
```

If you have the source project and Apache Maven, run these commands from the folder containing `pom.xml`.

```powershell
mvn test
mvn exec:java '-Dexec.mainClass=com.studyflow.Main'
```

The JAR normally uses `data` under the working directory. The Windows bundle normally uses `%LOCALAPPDATA%\StudyFlow\data`. Start either with `--data-dir` to open an older profile or keep a separate practice profile. Close any earlier copy that uses that directory.

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
- **Calendar.** Navigate by month and select a date to see what is scheduled. Filter StudyFlow tasks by subject. Choose **Edit task** to change a deadline or its subject, or **Edit subject** to open the linked course. Imported timetable entries and events from a connected Google Calendar appear alongside StudyFlow tasks.
- **Kanban Board.** The progress chart shows the share of tasks in each status and the number overdue. Cards show priority, type, and deadline. Move a task between **To do**, **In progress**, and **Completed** using the card controls.
- **Search.** Press **Ctrl+K**. Enter words such as a course name plus a topic. Choose **Open result** or press Enter on a selected result to open the actual record.
- **Focus.** Choose a preset or custom duration. Press **Ctrl+Space** to start, pause, or resume from Focus. The timer continues while you switch pages. Use **Finish early** to record time spent before closing; unfinished intervals do not restart.
- **Analytics.** Choose a period or enter inclusive custom dates. The range affects activity trends, focus and attendance. Attendance in Analytics counts dated class entries in that range. The task inventory always shows current saved tasks, including older completions whose dates are unknown.

### Import a timetable

1. Open **Calendar**, choose **Import timetable**, and select an `.ics` file. The import works offline. Daily or weekly events with an end date or occurrence count expand into individual timetable entries.
2. Select a date to find its **Timetable** entries. Use **Edit** to change an individual occurrence or **Delete** to take that occurrence off your timetable.
3. StudyFlow saves the entries and your changes in `timetable/timetable.json` in your profile. A repeat import keeps your edits and leaves removed occurrences off the timetable. If the file contains new occurrences, StudyFlow adds them. Imported timetable entries stay local, including when Google Calendar auto-sync is on.

You can also close StudyFlow and import from the command line. With the portable JAR, run:

```powershell
java -jar StudyFlow.jar --import-timetable "semester.ics"
```

For an alternate profile, put `--data-dir "C:\StudyFlow data"` before `--import-timetable`.

### Track attendance

1. Create the course in **Subjects** if it does not already exist.
2. Open **Attendance** and select the course. If you have earlier totals, choose **Set starting totals**, enter **Classes attended** and **Classes conducted**, then choose **Save totals**. Include earlier classes that aren't already in the dated history, so each class is counted once. Conducted includes classes you attended and classes you missed.
3. Choose a date and select **Add attended** or **Add absent** for each class you log. Dated entries are added to the starting totals. Use **Correct selected** to fix an existing dated entry.
4. Use **Change target** to set the required percentage for the subject. The Attendance page uses both starting totals and dated entries for its percentage and forecast. To clear the starting totals, save zero in both fields. The dated history stays in place.

An exact 100 percent target cannot be regained after a missed class. Attendance entries remain linked to their subject, so remove or resolve linked records before attempting to delete the subject.

<!-- pagebreak -->

## Study materials and integrations

### Add study materials

1. Open **Study Materials → Upload file**. Choose a PDF or a supported Office document, spreadsheet, presentation, text file, code file, or image. Enter a title and an optional subject and description. StudyFlow keeps a local copy. Check import progress or choose **Cancel import** before saving.
2. Search by title, filename, or other material details. Use **Subject** to show all materials, materials without a subject, or files linked to one course. **Open PDF** opens a PDF. For other attachments, **Show file** takes you to the saved copy so you can decide how to open it.
3. Search for words from an indexed PDF to find page evidence. **Text matches** shows ranked snippets with filenames and page numbers. **Open source PDF** opens the saved copy in your PDF viewer.
4. In **Study Assistant**, choose a topic, scope and mode. Local tools produce PDF extracts. If a provider is configured, choose remote generation to get a summary, explanation, or a structured question set. Question types are short answer, long answer and multiple choice, with a count, difficulty and answer/reference visibility controls.
5. Before any remote request, review the exact content in the confirmation dialog. Choose **Send request** to send it or **Cancel** to keep it local. Check the cited PDF pages before relying on generated output.

**History** keeps up to 50 saved results in the current local profile. You can open the cited material, reuse a request without running it, delete one selected result, or clear the whole history. Uncheck **Save in local history** before a run to skip saving.

StudyFlow indexes readable PDF text within its size, page count and text limits. Larger PDFs remain viewable as attachments, and image-only scans may need OCR for page search. Other attachments can be found by filename or metadata. The local formula view looks for equation-like text in retrieved PDF passages.

### Optional connections

**Connect Google Calendar.** From **Calendar**, choose **Google Calendar**. Use **Setup help** to configure a desktop OAuth client before launching StudyFlow. Choose **Connect Google**, select the account whose primary calendar you want to see, then authorize in the browser. Reconnect after restarting StudyFlow.

**View Google events.** StudyFlow fetches the displayed month from your connected primary calendar. Events refresh when you open Calendar or change months, and about once per minute while you stay on the page. Use **Refresh Google** to request an update, or select a date to see the events for that day. Recurring occurrences appear on their scheduled dates. All-day events keep their calendar dates, and timed events use your Google Calendar’s time zone. A linked StudyFlow task represents its synced Google event so it appears once. Google events are read-only in StudyFlow and stay in memory for the session. They do not become tasks or appear on Kanban.

**Send tasks to Google.** Choose **Sync selected** to send or update selected StudyFlow tasks as all-day events in your primary calendar. Choose **Export .ics** to save selected deadlines to a file you can import without signing in.

**Auto-sync dated tasks** starts off. To enable it, go to **Settings → Integrations → Google Calendar**, check the box, and confirm. When you enable it or reconnect, StudyFlow syncs existing dated tasks, including completed tasks. While StudyFlow is open and connected, it sends new dated tasks and changes to event details and retries temporary failures. Clear the checkbox to stop automatic sends. Google edits may appear in the calendar view after refresh, but they do not edit a StudyFlow task.

Clearing a deadline or deleting a local task leaves its Google event in place. To remove the linked event, select the task and choose **Remove event** before clearing its deadline or deleting it. This keeps auto-sync from recreating that event for the task and account. To send the task again, use **Sync selected**.

- **Spotify.** Paste an `https://open.spotify.com/` link in **Integrations**, choose **Save link**, then **Open playlist** to launch the saved item in your browser.
- **OpenAI.** Set `OPENAI_API_KEY` before launching StudyFlow. The model defaults to `gpt-6-luna` and can be changed with `STUDYFLOW_OPENAI_MODEL`. **Test provider** checks model access. Choosing **Use configured AI provider** and consenting sends selected source passages and your request. Leave it off for local study.

### Backups and recovery

Close StudyFlow before making or restoring a backup. Choose the JSON record type you are protecting. The application automatically preserves the previous valid version when updating a record file. Run the backup command to preserve the latest version explicitly.

```powershell
java -jar StudyFlow.jar --backup tasks
java -jar StudyFlow.jar --restore tasks "data/backups/backup.json"
```

Use the `timetable` type with either command to back up or restore imported timetable entries, edits and removals. Use `attendance-baselines` for starting totals in `attendance/baselines.json` and `attendance` for dated class entries.

Replace the example backup path with a file printed by the backup command or an earlier backup from `data/backups`. Add `--data-dir "C:\StudyFlow data"` before `--backup` or `--restore` for a non-default profile. Restoration checks references before replacing a record file, and preserves the file it replaces. Restart StudyFlow after a successful restore. For a whole-profile copy, close the app and copy the complete profile directory, including managed material files and the PDF index.
