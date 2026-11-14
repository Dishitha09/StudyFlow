# Stage 7 · Main GUI and dashboard

## Objective and baseline

Replace the Stage 6 verification shell with a usable desktop workspace. Reviewed the existing Swing shell, worker lifecycle, managers, task queries, and JSON settings before implementation.

## Features and files

- `MainFrame`, `SidebarPanel`, and `ViewStyle` provide persistent navigation, active page highlighting, and shared FlatLaf styling.
- `view/dashboard` presents live counts, today and upcoming tasks, Smart Priority, completion, and quick actions.
- `view/tasks` provides task CRUD, subtype forms, status and priority controls, details, and combined search, filters, and sorting.
- `view/academic` provides subject, workspace and note management. `WorkspaceManager` now saves membership and metadata together.
- `SettingsPanel` and `StudyFlowController` persist and apply the theme, and connect the pages to the existing application worker.
- The SRS, architecture, README and three UML diagrams were updated. The Stage 6 verification panels were retired.

## OOP and dependencies

The desktop uses MVC, composition, typed callbacks, CardLayout and the existing Strategy implementations. The EDT handles interactions while the single application worker handles service calls. No dependencies were added.

## Verification

- All 75 JUnit tests passed. New tests cover dashboard query boundaries, completion, combined filters, EDT callbacks, workspace edit validation and theme restart.
- The two-process Windows GUI test passed dashboard counts, inline completion, form validation, task add/edit/delete, combined filtering, priority and status changes, resize, theme switching, and reopening with the saved theme and linked records.
- The academic view smoke check exercised create and edit controls at a narrow content size. Dashboard probes rendered both themes at wide and narrow widths.

## Scope and next-stage dependencies

Future navigation entries show availability. Deadline displays use the model's date-only deadlines. Stage 8 uses the same task engine and application worker for planning views and focus sessions.
