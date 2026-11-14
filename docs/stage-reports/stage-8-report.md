# Stage 8 · Productivity and planning

## Objective and baseline

Build planning tools on the Stage 7 Swing shell and its existing task queries, task status commands, and local profile.

## Implementation and files

- `view/planning` adds Calendar, Kanban, Focus, Global Search and the recurrence/subtask dialog.
- `FocusTimer`, `FocusRecord` and `FocusRepository` track active time and persist completed intervals independently of historical task and subject links.
- `ReminderService` collects due, overdue and approaching deadline messages. One Swing timer requests updates using the existing worker.
- `SearchService` adds workspace matches. Existing results use stable record IDs for navigation.
- Updated `MainFrame`, sidebar, task and settings pages, controller and profile assembly.

## Design and dependencies

The calendar and board project the same task records. The board uses status controls. Timers use an event-driven UI and an injectable monotonic time source. Reminders deduplicate during the current run and local day. No dependencies added.

## Verification

All 80 JUnit tests passed on OpenJDK 21, including five focus timing cases. A native Swing probe verified month navigation, exam display, calendar and board callbacks, and Kanban status controls at a narrow window width.

## Limitations and next stage

Focus supports the configured basic focus and break durations. Reminder history is local to the current app run. Stage 9 can add attendance records keyed by the existing subject IDs.
