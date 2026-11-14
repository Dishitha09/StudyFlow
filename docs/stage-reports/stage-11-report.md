# Stage 11 · Optional integrations

## Objective and implementation

Inspected the existing material retrieval contract, task persistence, settings and background worker. Added Google Calendar desktop OAuth with PKCE and session-only tokens, explicit one-way sync for selected deadlines, saved remote IDs, ownership checks, and event removal. `CalendarExport` writes all-day events to iCalendar files offline. `LocalIntegrations` saves Spotify shortcuts and opens validated links in the browser. `OpenAiProvider` implements the existing generation interface and connects only on explicit user actions. `IntegrationsPanel`, the profile, and the controller wire these operations to Settings. Added a local Analytics totals page.

## Design and dependencies

The platform HTTP client keeps networking out of Swing. The desktop worker delivers results on the EDT. OAuth credentials and AI keys come from environment variables, and remote generation requires confirmation. No library dependency was added.

## Verification and limitations

All 129 tests passed, including 13 simulated OpenAI transport cases and 15 offline calendar and Spotify cases. An isolated Swing test exercised the integration controls with fake callbacks. Live provider calls require personal configuration and were not run. Google authorizes for the app session, and sync is explicit. Spotify opens a link externally. Long integration requests use the shared operation worker, so other writes wait while a request is in progress.

## Next stage

Package the build, retest the desktop workflows, and document the release.
