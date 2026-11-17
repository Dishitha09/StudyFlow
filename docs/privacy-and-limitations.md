# StudyFlow privacy and limitations

StudyFlow stores academic data in a local, single-user profile. Tasks, notes, attendance, focus, analytics, PDF search and local study modes work offline. Google Calendar and optional AI requests connect only through their explicit actions. Spotify opens a browser link, and iCalendar export writes a local file.

| Action | What leaves the profile | What StudyFlow stores |
| --- | --- | --- |
| Local study and search | Nothing from the app. | The managed PDF, its extracted text cache and any study result the student chooses to save. |
| Remote study request | The confirmed topic, selected mode and evidence snippets, with their file labels and pages, go to the configured provider. Question requests also include the chosen count and difficulty. | A result is saved only if **Save in local history** is checked. Uncheck it before a run to keep the result out of history. |
| Google Calendar | The browser opens Google's OAuth flow. Explicit sync sends selected task deadlines and their event details to the primary calendar. Removing a linked event also sends a provider request. | Task-to-event links persist. The access token stays in memory for the session. Restarting requires reconnecting. |
| iCalendar export | The app writes selected dated tasks into an `.ics` file. Any subsequent transfer or import is up to the student. | The file is written to the chosen location. |
| Spotify | The system browser opens the selected Spotify URL. | Saved links contain the shortcut data. StudyFlow keeps no Spotify token. |

`OPENAI_API_KEY`, `STUDYFLOW_OPENAI_MODEL`, `STUDYFLOW_GOOGLE_CLIENT_ID` and `STUDYFLOW_GOOGLE_CLIENT_SECRET` are environment configuration for optional features. Keep credentials outside source code, screenshots and handoff archives. Application diagnostics use event names and exception types. A diagnostic produced downstream by the operating system or an external provider is subject to that system's behavior.

The JSON snapshots, managed PDF copies, extracted text, assistant history and backups are readable on disk by accounts with access to the profile. StudyFlow does not implement profile encryption. Keep the profile under your own operating-system account and exclude it from shared repositories and source archives. For a whole-profile backup, close StudyFlow first and copy the profile directory. Manual and automatic JSON backups operate on a single supported record type, so retain the material files when preserving the library.

The history view supports deleting one entry or clearing the history. Routine history writes do not create automatic conversation backups. Deletion replaces the current local snapshot. Previously copied profiles, external provider records and operating-system copies are outside that operation. Removing a material can leave historical source labels visible while its PDF is no longer available.

PDF retrieval uses lexical matches from the readable text layer. Scanned pages require prior OCR for search. Page evidence can be incomplete when PDF reading order differs from the visual page or equations use non-text glyphs. Imports accept at most 100 MiB and 2,000 pages per document; the local text index also has limits. AI outputs can be inaccurate even with valid citations, so check explanations, questions and answers against the referenced PDF pages. Live Google and AI account behavior was outside the mocked and offline verification used for the release checks.

Analytics summarizes saved records for the selected dates. A legacy completion with an unknown timestamp stays out of dated activity, and each surviving root task contributes at most its latest recorded completion. Calendar sync is one-way, and its local mapping does not import changes made in Google. Spotify playback happens in the external browser or player.
