# StudyFlow desktop design system

The Phase 2 design gives StudyFlow one visual vocabulary across its Java Swing pages. Existing views, controller commands, and profile settings remain the foundation. The dashboard should tell students what is due, what is already late, and where they can act within the first screen.

## Color and surfaces

Use the saved FlatLaf theme and keep corresponding roles together in `ViewStyle`. Use solid card and control surfaces on a quieter workspace background. Give the primary action on each page the strongest contrast. Status always has a human-readable label. Icons and color reinforce that label.

| Role | Light | Dark | Application |
| --- | --- | --- | --- |
| Workspace | Ivory `#F7F8FC` | `#151720` | Page background and gutters |
| Surface | `#FFFFFF` | `#1E212D` | Cards, sidebar and dialogs |
| Raised surface | `#F0F2F8` | `#272B39` | Quiet section headers, hover and grouped controls |
| Text | `#172033` | `#F0F2F8` | Headings, body and values |
| Muted text | `#505A70` | `#B5BED0` | Descriptions, dates and other secondary text |
| Border | `#DDE2EF` | `#373E50` | Card outlines and dividers |
| Accent | Indigo `#5145CD` | `#BAB1FF` | Focus rings, links and selected navigation text |
| Accent background | `#EDEAFF` | `#343052` | Selected navigation and contextual accents |
| Primary button | `#5145CD` with white text | `#6457D8` with white text | Add, save and other main page actions |
| Success | `#186348` on `#E4F4EA` | `#9BE4BF` on `#203B33` | Completed status and successful operations |
| Warning | `#805119` on `#FFF0D9` | `#FAD28F` on `#403324` | Approaching deadlines and attention states |
| Danger | `#A52342` on `#FCE9EE` | `#FFACB6` on `#462A38` | Overdue, destructive actions and validation errors |

Render layouts with Swing managers and use matching borders, button states and selection colors in both themes. Derive custom paint colors from the active theme when painting. When a theme changes, update FlatLaf defaults and refresh the visible component tree.

## Typography, spacing and controls

- Use the operating system's UI font through FlatLaf. On Windows this uses the available Segoe UI family. Keep the distribution self-contained.
- Use 28 px bold for page titles, 20 px for section titles, 14 px for ordinary labels and actions, and 12–13 px for secondary information. Use bold or semibold numerals for key dashboard counts. Dates, badges, and supporting detail should remain readable at the smallest supported window.
- Use a base spacing step of 4 logical px. Reserve 24 px for standard page padding, 16 px between cards, 12–16 px within a card, 8 px within a control group, and 4 px for closely related labels. Narrow layouts may reduce page padding to 16 px before stacking regions.
- Use approximately 14–18 px corner radii on cards and 10–12 px on buttons. Use 18–20 px consistent outline icons with text labels. Icon-only controls need a clear tooltip and accessible name.
- Keep native controls keyboard operable and retain focus rings. Align related controls, keep selected rows visible, preserve form input after validation fails, and place errors near the relevant action. Show loading, empty, filtered-empty, unavailable-provider, and success states where they affect what the student can do.

## Navigation and interaction

Keep existing destinations and stable component names. Group navigation for scanning. Aim for a 220–250 px sidebar in wide windows and a more compact width on smaller displays. Page titles identify the current destination. Add subtitles and contextual actions where they clarify the next step.

| Group | Destinations |
| --- | --- |
| Plan | Dashboard, My Tasks, Calendar, Kanban Board |
| Study | Subjects, Workspaces, Notes, Study Materials |
| Track | Focus, Attendance, Analytics |
| Utilities | Settings |

Present Search as a global action near the top of the sidebar and route it to the existing Search page. `Ctrl+K` should open that page and focus the query. Preserve Enter on search results, `Ctrl+S` in academic editors, `Ctrl+Space` on Focus, and `Ctrl+Shift+P` for task planning. Group labels are headings, while buttons keep a short plain-language name. Keep the active destination apparent by label, shape and selected state. Only show shortcuts in the interface when they can be used in the current context.

Tab moves through the active page in reading order. Enter or Space activates controls. Escape closes cancellable dialogs. Label fields with `setLabelFor` or an accessible name, and distinguish completion from opening a task. Combine status text and color for warnings and overdue tasks. When custom cards expose data visually, include a text equivalent for the same data. Use meaningful names for dashboard actions and test focus when opening from a keyboard shortcut.

## Dashboard implementation plan

1. Apply the common palette and navigation first. Establish shared typography and spacing in `ViewStyle`, and make Search available from any page. Keep responsive behavior in layout code.
2. In Phase 3, rework `DashboardPanel` around the current controller snapshot. Give the welcome header and a primary Add task action a compact row, and retain up to five live metrics. Use two columns for Today's Plan and Upcoming deadlines, with a proportionate Smart Priority section. Reflow the metric cards and sections as the content width shrinks.
3. Keep the most relevant work visible. Put the actual local date and counts in the header. Make task titles open their existing detail route. Keep completion on eligible Today rows, and add clear page links to review a larger list. Prefer one page scroll with short previews over multiple nested vertical scroll areas. Within Smart Priority, ranked overdue tasks are visible first. Empty Today, Upcoming and Smart Priority states explain the available next action.
4. Retain quick access to Add subject, Add note, Focus and Attendance. Capture empty and populated layouts with real synthetic-profile data. Check keyboard navigation, task opening and completion, and refresh after a save before updating screenshots.

Use counts and dates supplied by real records. When Analytics gains charts, graph dated focus sessions and attendance where stored timestamps support it. Present task status as a current distribution. Use due-date or creation-date series when labeled as such.

## Responsive verification

Exercise the packaged Windows Swing app using a temporary profile populated with tasks, subjects, notes, focus sessions, attendance and unusually long titles. Record actual dimensions and scale alongside screenshots.

| Check | Window and scale | Expected behavior |
| --- | --- | --- |
| Compact laptop | 1024 × 700 at 100% | Main actions and navigation remain usable. Dashboard cards reflow, filters and dialogs fit, and vertical scrolling reaches every section. |
| Typical laptop | 1366 × 768 at 100% and 125% | Dashboard establishes clear priority. Calendar and Kanban remain legible. Search can open a task or material. No clipped labels block an action. |
| Larger laptop | 1440 × 900 at 100% and 125% | Charts, cards and content columns remain aligned. Controls stay easy to reach through the keyboard. |
| Full desktop | 1920 × 1080 at 100% and 150% | Extra space is used by content. Labels and buttons remain visually related and high-contrast in both themes. |
| High DPI | A 1920 × 1080 display at 200%, where supported | Main routes and dialog confirmation remain reachable. Verify the actual logical client area and scroll behavior. |
| Resize stress | Drag down to the 850 × 560 Swing minimum | Check the still-visible action, sidebar scrolling, task lists and detail panes, data tables, buttons, and modal dialogs at the smallest supported client state. |

Repeat the compact and full desktop checks in Light and Dark. Verify there is no global horizontal scrolling, long text is ellipsized or wrapped with a way to read the complete value, and selection, hover, focus, disabled and destructive states are distinguishable. Use a native DPI check where available. If JVM UI scaling is used as a fallback, record that separately from the physical Windows scaling result.
