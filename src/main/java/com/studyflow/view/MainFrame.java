package com.studyflow.view;

import com.studyflow.config.AppConfig;
import com.studyflow.controller.StudyFlowController;
import com.studyflow.controller.StudyFlowController.TaskQuery;
import com.studyflow.factory.TaskDraft;
import com.studyflow.model.Priority;
import com.studyflow.model.Subject;
import com.studyflow.model.TaskStatus;
import com.studyflow.model.Theme;
import com.studyflow.view.academic.AcademicPanel;
import com.studyflow.view.attendance.AttendancePanel;
import com.studyflow.view.dashboard.DashboardPanel;
import com.studyflow.view.materials.MaterialPanel;
import com.studyflow.view.navigation.SidebarPanel;
import com.studyflow.view.navigation.SidebarPanel.Page;
import com.studyflow.view.planning.CalendarPanel;
import com.studyflow.view.planning.FocusPanel;
import com.studyflow.view.planning.KanbanPanel;
import com.studyflow.view.planning.SearchPanel;
import com.studyflow.view.planning.SubtaskDialog;
import com.studyflow.view.settings.SettingsPanel;
import com.studyflow.view.settings.IntegrationsPanel;
import com.studyflow.view.tasks.TaskPanel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Hosts the active desktop page and coordinates shared interaction state. */
public final class MainFrame extends JFrame {
    private final StudyFlowController controller;
    private final CardLayout pages = new CardLayout();
    private final JPanel content = new JPanel(pages);
    private final SidebarPanel sidebar;
    private final DashboardPanel dashboard;
    private final TaskPanel tasks;
    private final AcademicPanel subjects;
    private final AcademicPanel workspaces;
    private final AcademicPanel notes;
    private final SettingsPanel settings;
    private final IntegrationsPanel integrations;
    private final CalendarPanel calendar;
    private final KanbanPanel kanban;
    private final FocusPanel focus;
    private final SearchPanel search;
    private final AttendancePanel attendance;
    private final MaterialPanel materials;
    private final javax.swing.Timer reminderTimer;
    private final javax.swing.JTextArea reminderText = new javax.swing.JTextArea(9, 48);
    private final JButton reminderButton = new JButton("Reminders");
    private final javax.swing.JTextArea analytics = new javax.swing.JTextArea();
    private final JLabel feedback = new JLabel("Loading your workspace…") {
        @Override
        public Color getForeground() {
            return ViewStyle.color(error ? "danger" : "muted");
        }
    };

    private TaskQuery taskQuery = TaskQuery.initial();
    private List<Subject> taskSubjects = List.of();
    private Theme theme;
    private boolean busy;
    private boolean closing;
    private boolean error;
    private String pendingTaskSelection;
    private boolean notificationsEnabled;

    public MainFrame(AppConfig config, StudyFlowController controller, Theme theme) {
        super(config.name());
        this.controller = controller;
        this.theme = theme;
        setName("studyFlowWindow");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(850, 560));
        Rectangle screen = getGraphicsConfiguration().getBounds();
        var screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        setSize(Math.max(850, Math.min(1260, screen.width - screenInsets.left - screenInsets.right - 40)),
                Math.max(560, Math.min(830, screen.height - screenInsets.top - screenInsets.bottom - 40)));
        setLocationRelativeTo(null);

        sidebar = new SidebarPanel(config.name(), this::navigate);
        dashboard = new DashboardPanel(this::dashboardAction, id -> change(
                () -> controller.changeStatus(id, TaskStatus.COMPLETED), "Task completed.", null));
        tasks = new TaskPanel(new TaskActions());
        tasks.addPlanningAction(this::planTask);
        AcademicActions academicActions = new AcademicActions();
        subjects = new AcademicPanel(AcademicPanel.Kind.SUBJECTS, academicActions);
        workspaces = new AcademicPanel(AcademicPanel.Kind.WORKSPACES, academicActions);
        notes = new AcademicPanel(AcademicPanel.Kind.NOTES, academicActions);
        integrations = new IntegrationsPanel(new IntegrationActions());
        settings = new SettingsPanel(this::selectTheme, enabled -> change(
                () -> controller.changeNotifications(enabled), "Reminder preference saved.", null), integrations);
        settings.showTheme(theme);
        calendar = new CalendarPanel(this::openTask);
        kanban = new KanbanPanel((id, status) -> change(() -> controller.changeStatus(id, status),
                "Task moved.", null), this::openTask);
        focus = new FocusPanel(session -> change(() -> controller.recordFocus(session.id(), session.taskId(),
                session.subjectId(), session.planned(), session.actual()), "Focus session saved.",
                () -> focusSaved(true)));
        search = new SearchPanel(this::search, this::openResult);
        attendance = new AttendancePanel(new AttendancePanel.Actions() {
            @Override
            public void add(String subjectId, java.time.LocalDate date, boolean attended) {
                change(() -> controller.addAttendance(subjectId, date, attended), "Class recorded.", null);
            }

            @Override
            public void correct(String id, java.time.LocalDate date, boolean attended) {
                change(() -> controller.correctAttendance(id, date, attended), "Class corrected.", null);
            }

            @Override
            public void setTarget(String subjectId, int target) {
                change(() -> controller.setAttendanceTarget(subjectId, target), "Target saved.", null);
            }
        });
        materials = new MaterialPanel(new MaterialPanel.Actions() {
            @Override
            public void upload(java.nio.file.Path source, String title, String description, String subjectId) {
                change(() -> controller.uploadMaterial(source, title, description, subjectId), "PDF added.", null);
            }

            @Override
            public void search(String text) {
                setBusy(true);
                controller.searchMaterials(text, results -> {
                    if (!closing) {
                        materials.showMatches(results);
                        setBusy(false);
                    }
                }, MainFrame.this::showFailure);
            }

            @Override
            public void open(String id) {
                controller.openMaterial(id, path -> { if (!closing) showFeedback("PDF opened.", false); },
                        MainFrame.this::showFailure);
            }

            @Override
            public void delete(String id) {
                change(() -> controller.deleteMaterial(id), "PDF removed.", null);
            }

            @Override
            public void study(String text, String id, com.studyflow.service.StudyAssistant.Mode mode, boolean remote) {
                setBusy(true);
                showFeedback("Preparing study aid…", false);
                controller.study(text, id, mode, remote, answer -> {
                    if (!closing) {
                        materials.showAnswer(answer);
                        setBusy(false);
                        showFeedback(" ", false);
                    }
                }, MainFrame.this::showFailure);
            }
        });

        content.add(dashboard, Page.DASHBOARD.name());
        content.add(tasks, Page.TASKS.name());
        content.add(subjects, Page.SUBJECTS.name());
        content.add(workspaces, Page.WORKSPACES.name());
        content.add(notes, Page.NOTES.name());
        content.add(settings, Page.SETTINGS.name());
        content.add(calendar, Page.CALENDAR.name());
        content.add(kanban, Page.KANBAN.name());
        content.add(focus, Page.FOCUS.name());
        content.add(search, Page.SEARCH.name());
        content.add(attendance, Page.ATTENDANCE.name());
        content.add(materials, Page.MATERIALS.name());
        JPanel analyticsPage = new JPanel(new BorderLayout(0, 18));
        analyticsPage.setBorder(BorderFactory.createEmptyBorder(26, 28, 24, 28));
        analyticsPage.add(ViewStyle.heading("Analytics"), BorderLayout.PAGE_START);
        analytics.setName("analyticsTotals");
        analytics.setEditable(false);
        analytics.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        analyticsPage.add(ViewStyle.scroll(analytics), BorderLayout.CENTER);
        content.add(analyticsPage, Page.ANALYTICS.name());
        for (Page page : Page.values()) {
            if (page.availableStage() > 0) {
                content.add(availability(page.title(), page.availableStage()), page.name());
            }
        }
        feedback.setName("workspaceStatus");
        feedback.putClientProperty("html.disable", Boolean.TRUE);
        feedback.setBorder(BorderFactory.createEmptyBorder(5, 27, 8, 20));
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.add(content, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.add(feedback, BorderLayout.CENTER);
        reminderButton.setName("remindersButton");
        reminderButton.setVisible(false);
        reminderText.setEditable(false);
        reminderText.setWrapStyleWord(true);
        reminderText.setLineWrap(true);
        reminderButton.addActionListener(event -> javax.swing.JOptionPane.showMessageDialog(this,
                new javax.swing.JScrollPane(reminderText), "Task reminders", javax.swing.JOptionPane.INFORMATION_MESSAGE));
        footer.add(reminderButton, BorderLayout.LINE_END);
        workspace.add(footer, BorderLayout.PAGE_END);
        add(sidebar, BorderLayout.LINE_START);
        add(workspace, BorderLayout.CENTER);
        navigate(Page.DASHBOARD);
        reminderTimer = new javax.swing.Timer(60_000, event -> refreshReminders());
        setBusy(true);
        controller.load(taskQuery, records -> {
            if (!closing) {
                showRecords(records);
                setBusy(false);
                showFeedback(" ", false);
                refreshReminders();
                reminderTimer.start();
            }
        }, this::showFailure);
    }

    private static JPanel availability(String name, int stage) {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setBorder(BorderFactory.createEmptyBorder(26, 28, 24, 28));
        page.add(ViewStyle.heading(name), BorderLayout.PAGE_START);
        JPanel message = ViewStyle.card(new BorderLayout());
        message.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        message.add(ViewStyle.subheading("Available in Stage " + stage), BorderLayout.CENTER);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(message, BorderLayout.PAGE_START);
        page.add(body, BorderLayout.CENTER);
        return page;
    }

    private void navigate(Page page) {
        sidebar.showPage(page);
        pages.show(content, page.name());
    }

    private void dashboardAction(DashboardPanel.Action action) {
        switch (action) {
            case ADD_TASK -> {
                navigate(Page.TASKS);
                tasks.openCreate();
            }
            case ADD_SUBJECT -> {
                navigate(Page.SUBJECTS);
                subjects.openCreate();
            }
            case ADD_NOTE -> {
                navigate(Page.NOTES);
                notes.openCreate();
            }
            case START_FOCUS -> navigate(Page.FOCUS);
            case OPEN_ATTENDANCE -> navigate(Page.ATTENDANCE);
        }
    }

    private void selectTheme(Theme selected) {
        if (selected != theme) {
            change(() -> controller.changeTheme(selected), "Theme saved.", null);
        }
    }

    private void showRecords(StudyFlowController.Records records) {
        if (theme != records.theme()) {
            theme = records.theme();
            ViewStyle.applyTheme(theme);
            sidebar.applyColors();
        }
        settings.showTheme(theme);
        notificationsEnabled = records.settings().isNotificationsEnabled();
        settings.showNotifications(notificationsEnabled);
        reminderButton.setVisible(notificationsEnabled && !reminderText.getText().isBlank());
        dashboard.showData(records.dashboard());
        tasks.showTasks(records.tasks(), records.subjects());
        subjects.showRecords(records.subjects(), records.workspaces(), records.notes());
        workspaces.showRecords(records.subjects(), records.workspaces(), records.notes());
        notes.showRecords(records.subjects(), records.workspaces(), records.notes());
        taskSubjects = records.subjects();
        calendar.showTasks(records.allTasks(), taskSubjects);
        kanban.showTasks(records.allTasks(), taskSubjects);
        focus.showData(records.allTasks(), taskSubjects, records.settings(), records.completedSessions());
        attendance.showData(records.attendance());
        materials.showData(records.materials(), taskSubjects);
        materials.setRemoteAvailable(records.remoteAi());
        integrations.showData(records.allTasks(), records.googleConfigured(), records.googleConnected(),
                records.remoteAi(), records.playlists());
        var summary = records.dashboard();
        analytics.setText("Tasks completed    " + summary.completed() + " / " + summary.total()
                + "\nTasks pending       " + summary.pending()
                + "\nTasks overdue       " + summary.overdue()
                + "\nFocus sessions      " + records.completedSessions()
                + "\nClasses attended   " + records.attendance().attended() + " / " + records.attendance().total());
    }

    private void query(TaskQuery query) {
        if (busy || closing) {
            return;
        }
        setBusy(true);
        controller.query(query, rows -> {
            if (!closing) {
                taskQuery = query;
                tasks.showTasks(rows, taskSubjects);
                if (pendingTaskSelection != null) {
                    tasks.selectTask(pendingTaskSelection);
                    pendingTaskSelection = null;
                }
                setBusy(false);
                showFeedback(" ", false);
            }
        }, this::showFailure);
    }

    private void change(Runnable command, String success, Runnable afterSave) {
        if (busy || closing) {
            return;
        }
        setBusy(true);
        showFeedback("Saving…", false);
        controller.change(command, taskQuery, records -> {
            if (!closing) {
                showRecords(records);
                setBusy(false);
                if (afterSave != null) {
                    afterSave.run();
                }
                showFeedback(success, false);
                refreshReminders();
            }
        }, this::showFailure);
    }

    private void showFailure(String message) {
        if (!closing) {
            settings.showTheme(theme);
            settings.showNotifications(notificationsEnabled);
            tasks.failEdit(message);
            focusSaved(false);
            setBusy(false);
            showFeedback(message, true);
        }
    }

    private void showFeedback(String text, boolean failed) {
        error = failed;
        feedback.setText(text);
        feedback.setToolTipText(failed ? text : null);
        feedback.repaint();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        dashboard.setBusy(busy);
        tasks.setBusy(busy);
        subjects.setBusy(busy);
        workspaces.setBusy(busy);
        notes.setBusy(busy);
        settings.setBusy(busy);
        kanban.setBusy(busy);
        focus.setBusy(busy);
        search.setBusy(busy);
        attendance.setBusy(busy);
        materials.setBusy(busy);
        integrations.setBusy(busy);
    }

    public void setClosing() {
        closing = true;
        reminderTimer.stop();
        focus.stop();
        setBusy(true);
        showFeedback("Closing…", false);
    }

    private void focusSaved(boolean saved) {
        focus.finishSave(saved);
    }

    private void refreshReminders() {
        if (closing || !notificationsEnabled) {
            return;
        }
        controller.reminders(messages -> {
            if (!closing && notificationsEnabled && !messages.isEmpty()) {
                reminderText.append(String.join("\n", messages) + "\n");
                reminderText.setCaretPosition(0);
                reminderButton.setVisible(true);
            }
        }, message -> { if (!closing) showFeedback(message, true); });
    }

    private void openTask(String id) {
        if (!busy) {
            navigate(Page.TASKS);
            pendingTaskSelection = id;
            tasks.clearFilters();
        }
    }

    private void openResult(SearchPanel.Result result) {
        switch (result.type()) {
            case "Task" -> openTask(result.id());
            case "Subject" -> { navigate(Page.SUBJECTS); subjects.selectRecord(result.id()); }
            case "Workspace" -> { navigate(Page.WORKSPACES); workspaces.selectRecord(result.id()); }
            case "Note" -> { navigate(Page.NOTES); notes.selectRecord(result.id()); }
            case "Material" -> { navigate(Page.MATERIALS); materials.selectMaterial(result.id()); }
            default -> throw new IllegalArgumentException("Unknown search result: " + result.type());
        }
    }

    private void search(String query) {
        if (busy || closing) {
            return;
        }
        setBusy(true);
        controller.searchAll(query, data -> {
            if (!closing) {
                var results = data.records();
                List<SearchPanel.Result> rows = new java.util.ArrayList<>();
                results.tasks().forEach(task -> rows.add(new SearchPanel.Result("Task", task.getId(), task.getTitle(),
                        task.getDescription())));
                results.subjects().forEach(item -> rows.add(new SearchPanel.Result("Subject", item.getId(),
                        item.getName(), item.getSubjectCode())));
                results.workspaces().forEach(item -> rows.add(new SearchPanel.Result("Workspace", item.getId(),
                        item.getName(), item.getDescription())));
                results.notes().forEach(item -> rows.add(new SearchPanel.Result("Note", item.getId(), item.getTitle(),
                        item.getContent().lines().findFirst().orElse(""))));
                data.materials().forEach(item -> rows.add(new SearchPanel.Result("Material", item.id(), item.title(),
                        item.fileName())));
                search.showResults(rows);
                setBusy(false);
            }
        }, this::showFailure);
    }

    private void planTask(com.studyflow.model.Task task) {
        SubtaskDialog.Edit edit = SubtaskDialog.show(this, task);
        if (edit == null) {
            return;
        }
        Runnable command = switch (edit) {
            case SubtaskDialog.Repeat repeat -> () -> controller.setRecurrence(task.getId(), repeat.value());
            case SubtaskDialog.Add add -> () -> controller.addSubtask(task.getId(), add.title(), add.due());
            case SubtaskDialog.Status status -> () -> controller.setSubtaskStatus(task.getId(), status.id(), status.status());
            case SubtaskDialog.Remove remove -> () -> controller.removeSubtask(task.getId(), remove.id());
        };
        change(command, "Task planning updated.", null);
    }

    private final class IntegrationActions implements IntegrationsPanel.Actions {
        @Override public void connect() { change(controller::connectGoogle, "Google Calendar connected.", null); }
        @Override public void disconnect() { change(controller::disconnectGoogle, "Google Calendar disconnected.", null); }
        @Override public void testGoogle() { change(controller::testGoogle, "Google Calendar connection verified.", null); }
        @Override public void sync(List<String> ids) {
            change(() -> controller.syncGoogle(ids), "Selected deadlines synced.", null);
        }
        @Override public void removeEvent(String id) {
            change(() -> controller.removeEvent(id), "Calendar event removed.", null);
        }
        @Override public void export(java.nio.file.Path path, List<String> ids) {
            change(() -> controller.exportCalendar(path, ids), "Calendar file exported.", null);
        }
        @Override public void addPlaylist(String url) {
            change(() -> controller.addPlaylist(url), "Spotify link saved.", null);
        }
        @Override public void removePlaylist(String url) {
            change(() -> controller.removePlaylist(url), "Spotify link removed.", null);
        }
        @Override public void openPlaylist(String url) {
            change(() -> controller.openPlaylist(url), "Spotify link opened.", null);
        }
        @Override public void testAi() { change(controller::testAi, "AI configuration verified.", null); }
    }

    private final class TaskActions implements TaskPanel.Actions {
        @Override
        public void create(TaskDraft draft) {
            change(() -> controller.createTask(draft), "Task added.", tasks::finishEdit);
        }

        @Override
        public void update(String id, TaskDraft draft) {
            change(() -> controller.updateTask(id, draft), "Task updated.", tasks::finishEdit);
        }

        @Override
        public void delete(String id) {
            change(() -> controller.deleteTask(id), "Task deleted.", null);
        }

        @Override
        public void status(String id, TaskStatus status) {
            change(() -> controller.changeStatus(id, status), "Task status updated.", null);
        }

        @Override
        public void priority(String id, Priority priority) {
            change(() -> controller.changePriority(id, priority), "Task priority updated.", null);
        }

        @Override
        public void query(TaskQuery query) {
            MainFrame.this.query(query);
        }
    }

    private final class AcademicActions implements AcademicPanel.Actions {
        @Override
        public void createSubject(String name, String code, String description, List<String> tags) {
            change(() -> controller.createSubject(name, code, description, tags), "Subject added.", subjects::finishEdit);
        }

        @Override
        public void updateSubject(String id, String name, String code, String description, List<String> tags) {
            change(() -> controller.updateSubject(id, name, code, description, tags),
                    "Subject updated.", subjects::finishEdit);
        }

        @Override
        public void deleteSubject(String id) {
            change(() -> controller.deleteSubject(id), "Subject deleted.", null);
        }

        @Override
        public void createWorkspace(String name, String description, List<String> members) {
            change(() -> controller.createWorkspace(name, description, members), "Workspace added.",
                    workspaces::finishEdit);
        }

        @Override
        public void updateWorkspace(String id, String name, String description, List<String> members) {
            change(() -> controller.updateWorkspace(id, name, description, members), "Workspace updated.",
                    workspaces::finishEdit);
        }

        @Override
        public void deleteWorkspace(String id) {
            change(() -> controller.deleteWorkspace(id), "Workspace deleted.", null);
        }

        @Override
        public void createNote(String title, String body, String subjectId, List<String> tags) {
            change(() -> controller.createNote(title, body, subjectId, tags), "Note added.", notes::finishEdit);
        }

        @Override
        public void updateNote(String id, String title, String body, String subjectId, List<String> tags) {
            change(() -> controller.updateNote(id, title, body, subjectId, tags), "Note updated.", notes::finishEdit);
        }

        @Override
        public void deleteNote(String id) {
            change(() -> controller.deleteNote(id), "Note deleted.", null);
        }
    }
}
