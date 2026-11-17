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
import com.studyflow.view.analytics.AnalyticsPanel;
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
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

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
    private final AnalyticsPanel analytics;
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
    private java.time.LocalDate displayedDate;
    private Page activePage;
    private long analyticsRequest;
    private java.util.concurrent.atomic.AtomicBoolean cancelPdfImport;

    public MainFrame(AppConfig config, StudyFlowController controller, Theme theme) {
        super(config.name());
        this.controller = controller;
        this.theme = theme;
        analytics = new AnalyticsPanel(controller.currentDate(), this::refreshAnalytics);
        setName("studyFlowWindow");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        fitDesktop();

        sidebar = new SidebarPanel(config.name(), this::navigate);
        sidebar.fitWidth(getWidth());
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                sidebar.fitWidth(getWidth());
            }
        });
        dashboard = new DashboardPanel(this::dashboardAction, id -> change(
                () -> controller.changeStatus(id, TaskStatus.COMPLETED), "Task completed.", null), this::openTask);
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
        calendar = new CalendarPanel(this::openTask, tasks::openEdit, this::openSubjectEditor,
                this::openCalendarIntegrations);
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
                if (busy || closing) return;
                var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
                var latest = new java.util.concurrent.atomic.AtomicReference<
                        com.studyflow.service.MaterialLibrary.ImportProgress>();
                cancelPdfImport = cancelled;
                materials.beginImport(() -> cancelled.set(true));
                change(() -> controller.uploadMaterial(source, title, description, subjectId, cancelled::get,
                        progress -> {
                            if (latest.getAndSet(progress) == null) SwingUtilities.invokeLater(() -> {
                                var current = latest.getAndSet(null);
                                if (!closing && current != null) materials.showImportProgress(current);
                            });
                        }), "PDF added.", MainFrame.this::finishPdfImport, message -> {
                            finishPdfImport();
                            if ("PDF import cancelled.".equals(message)) {
                                setBusy(false);
                                showFeedback("PDF import cancelled. Nothing was added.", false);
                            } else showFailure(message);
                        });
            }

            @Override
            public void search(String text) {
                setBusy(true);
                controller.searchPdfLibrary(text, results -> {
                    if (!closing) {
                        materials.showPdfSearch(text, results.matches(), results.evidence());
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
            public void prepareStudy(com.studyflow.service.StudyAssistant.Request request) {
                setBusy(true);
                showFeedback("Finding source passages…", false);
                controller.prepareStudy(request, prepared -> {
                    if (!closing) {
                        setBusy(false);
                        showFeedback(" ", false);
                        materials.reviewPrepared(prepared);
                    }
                }, MainFrame.this::showFailure);
            }

            @Override
            public void runStudy(com.studyflow.service.StudyAssistant.Prepared prepared) {
                setBusy(true);
                showFeedback("Preparing study output…", false);
                controller.runStudy(prepared, result -> {
                    if (!closing) {
                        materials.showAnswer(result.answer());
                        setBusy(false);
                        showFeedback(result.historySaveFailed() ? "The output is ready. History could not be saved." : " ",
                                result.historySaveFailed());
                    }
                }, MainFrame.this::showFailure);
            }

            @Override
            public void loadHistory() {
                setBusy(true);
                controller.loadStudyHistory(entries -> {
                    if (!closing) {
                        materials.showHistory(entries);
                        setBusy(false);
                        showFeedback(" ", false);
                    }
                }, message -> {
                    if (!closing) {
                        materials.showHistoryFailure();
                        showFailure(message);
                    }
                });
            }

            @Override
            public void clearHistory() {
                change(controller::clearStudyHistory, "Study history cleared.", () -> materials.showHistory(List.of()));
            }

            @Override
            public void deleteHistory(String entryId) {
                setBusy(true);
                controller.deleteStudyHistory(entryId, entries -> {
                    if (!closing) {
                        materials.showHistory(entries);
                        setBusy(false);
                        showFeedback("Saved study output deleted.", false);
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
        content.add(analytics, Page.ANALYTICS.name());
        for (Page page : Page.values()) {
            if (page.availableStage() > 0) {
                content.add(availability(page.title(), page.availableStage()), page.name());
            }
        }
        feedback.setName("workspaceStatus");
        feedback.putClientProperty("html.disable", Boolean.TRUE);
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.add(content, BorderLayout.CENTER);
        bindGlobalSearch();
        workspace.add(footer(), BorderLayout.PAGE_END);
        add(sidebar, BorderLayout.LINE_START);
        add(workspace, BorderLayout.CENTER);
        navigate(Page.DASHBOARD);
        reminderTimer = new javax.swing.Timer(60_000, event -> refreshDay());
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

    private void fitDesktop() {
        Rectangle screen = getGraphicsConfiguration().getBounds();
        var inset = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        int width = screen.width - inset.left - inset.right;
        int height = screen.height - inset.top - inset.bottom;
        // Window coordinates are logical pixels, so high DPI displays can have less usable space.
        int minimumWidth = Math.min(850, width);
        int minimumHeight = Math.min(560, height);
        setMinimumSize(new Dimension(minimumWidth, minimumHeight));
        setSize(Math.max(minimumWidth, Math.min(1440, width - 36)),
                Math.max(minimumHeight, Math.min(900, height - 36)));
        setLocationRelativeTo(null);
    }

    private JPanel footer() {
        JPanel footer = ViewStyle.surface(new BorderLayout());
        JPanel row = new JPanel(new BorderLayout(ViewStyle.CONTROL_GAP, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(5, ViewStyle.PAGE_PADDING, 5, 12));
        row.add(feedback, BorderLayout.CENTER);
        reminderButton.setName("remindersButton");
        reminderButton.setVisible(false);
        reminderText.setEditable(false);
        reminderText.setWrapStyleWord(true);
        reminderText.setLineWrap(true);
        reminderButton.addActionListener(event -> javax.swing.JOptionPane.showMessageDialog(this,
                new javax.swing.JScrollPane(reminderText), "Task reminders", javax.swing.JOptionPane.INFORMATION_MESSAGE));
        row.add(reminderButton, BorderLayout.LINE_END);
        footer.add(new JSeparator(), BorderLayout.PAGE_START);
        footer.add(row, BorderLayout.CENTER);
        return footer;
    }

    private void bindGlobalSearch() {
        String action = "openGlobalSearch";
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK), action);
        getRootPane().getActionMap().put(action, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                navigate(Page.SEARCH);
            }
        });
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
        if (closing) {
            return;
        }
        activePage = page;
        sidebar.showPage(page);
        pages.show(content, page.name());
        if (page == Page.SEARCH) {
            SwingUtilities.invokeLater(search::focusQuery);
        }
        if (page == Page.ANALYTICS) refreshAnalytics(analytics.range());
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
            case OPEN_TASKS -> navigate(Page.TASKS);
            case OPEN_CALENDAR -> navigate(Page.CALENDAR);
        }
    }

    private void selectTheme(Theme selected) {
        if (selected != theme) {
            change(() -> controller.changeTheme(selected), "Theme saved.", null);
        }
    }

    private void showRecords(StudyFlowController.Records records) {
        displayedDate = records.dashboard().date();
        analytics.setReferenceDate(displayedDate);
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
        workspaces.showRecords(records.subjects(), records.workspaces(), records.notes(), records.allTasks());
        notes.showRecords(records.subjects(), records.workspaces(), records.notes());
        taskSubjects = records.subjects();
        calendar.showTasks(records.allTasks(), taskSubjects, records.linkedCalendarTasks());
        kanban.showTasks(records.allTasks(), taskSubjects);
        focus.showData(records.allTasks(), taskSubjects, records.settings(), records.completedSessions());
        attendance.showData(records.attendance());
        materials.showData(records.materials(), taskSubjects, records.materialsWarning());
        materials.setRemoteAvailable(records.remoteAi());
        integrations.showData(records.allTasks(), taskSubjects, records.googleConfigured(), records.googleConnected(),
                records.linkedCalendarTasks(), records.remoteAi(), records.playlists(), records.integrationsWarning());
        integrations.setConnecting(false);
        if (activePage == Page.ANALYTICS) refreshAnalytics(analytics.range());
    }

    private void refreshAnalytics(AnalyticsPanel.Range range) {
        if (closing || range == null) return;
        long request = ++analyticsRequest;
        analytics.showLoading();
        controller.readAnalytics(range.start(), range.end(), report -> {
            if (!closing && request == analyticsRequest) analytics.showReport(report);
        }, message -> {
            if (!closing && request == analyticsRequest) analytics.showError(message);
        });
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
        change(command, success, afterSave, this::showFailure);
    }

    private void change(Runnable command, String success, Runnable afterSave, java.util.function.Consumer<String> failure) {
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
        }, failure);
    }

    private void changeGoogle(Runnable command, String success) {
        change(command, success, null, message -> {
            if (!closing) {
                // A batch may have linked some events, or lost authorization, before its first failure.
                controller.load(taskQuery, current -> {
                    if (!closing) {
                        showRecords(current);
                        handleGoogleFailure(message);
                    }
                }, ignored -> handleGoogleFailure(message));
            }
        });
    }

    private void handleGoogleFailure(String message) {
        if (closing) return;
        integrations.setConnecting(false);
        if ("Google sign-in was cancelled.".equals(message)) {
            setBusy(false);
            showFeedback(message, false);
        } else showFailure(message);
    }

    private void showFailure(String message) {
        if (!closing) {
            settings.showTheme(theme);
            settings.showNotifications(notificationsEnabled);
            tasks.failEdit(message);
            subjects.failEdit(message);
            workspaces.failEdit(message);
            notes.failEdit(message);
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
        if (cancelPdfImport != null) cancelPdfImport.set(true);
        controller.cancelGoogleConnection();
        focus.stop();
        setBusy(true);
        showFeedback("Closing…", false);
    }

    public boolean confirmClose() {
        boolean drafts = tasks.hasUnsavedEdit() || subjects.hasUnsavedChanges()
                || workspaces.hasUnsavedChanges() || notes.hasUnsavedChanges();
        boolean interval = focus.hasUnfinishedInterval();
        if (!drafts && !interval) {
            return true;
        }
        String message = (drafts ? "Unsaved edits will be discarded." : "")
                + (interval ? (drafts ? "\n" : "") + "The current timer or unsaved focus session will be lost. "
                + "Keep StudyFlow open to finish or retry saving." : "");
        String[] choices = {"Keep StudyFlow open", "Close StudyFlow"};
        return javax.swing.JOptionPane.showOptionDialog(this, message, "Close StudyFlow?",
                javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE,
                null, choices, choices[0]) == 1;
    }

    private void focusSaved(boolean saved) {
        focus.finishSave(saved);
    }

    private void finishPdfImport() {
        cancelPdfImport = null;
        materials.finishImport();
    }

    private void refreshDay() {
        if (busy || closing) {
            return;
        }
        if (controller.currentDate().equals(displayedDate)) {
            refreshReminders();
            return;
        }
        setBusy(true);
        controller.load(taskQuery, records -> {
            if (!closing) {
                reminderText.setText("");
                showRecords(records);
                setBusy(false);
                refreshReminders();
            }
        }, this::showFailure);
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

    private void openSubjectEditor(String id) {
        if (!busy && !closing) {
            navigate(Page.SUBJECTS);
            subjects.openEdit(id);
        }
    }

    private void openCalendarIntegrations() {
        navigate(Page.SETTINGS);
        settings.openIntegrations();
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
                showFeedback(data.materialsWarning() == null ? " " : data.materialsWarning(),
                        data.materialsWarning() != null);
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
        @Override public void connect() {
            if (!busy && !closing) {
                integrations.setConnecting(true);
                changeGoogle(controller::connectGoogle, "Google Calendar connected.");
                showFeedback("Complete Google sign-in in your browser to finish connecting.", false);
            }
        }
        @Override public void cancelConnect() {
            if (controller.cancelGoogleConnection()) {
                integrations.setConnecting(false);
                showFeedback("Cancelling Google sign-in…", false);
            }
        }
        @Override public void disconnect() { change(controller::disconnectGoogle, "Google Calendar disconnected.", null); }
        @Override public void testGoogle() { changeGoogle(controller::testGoogle, "Google Calendar connection verified."); }
        @Override public void openGoogleSetup() { change(controller::openGoogleSetup, "Setup guide opened.", null); }
        @Override public void openGoogleCalendar() { change(controller::openGoogleCalendar, "Google Calendar opened.", null); }
        @Override public void editSubject(String id) { openSubjectEditor(id); }
        @Override public void sync(List<String> ids) {
            changeGoogle(() -> controller.syncGoogle(ids), "Selected deadlines synced.");
        }
        @Override public void removeEvent(String id) {
            changeGoogle(() -> controller.removeEvent(id), "Calendar event removed.");
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
        public void openWorkspaceItem(AcademicPanel.LinkedRecord kind, String id) {
            switch (kind) {
                case TASK -> openTask(id);
                case SUBJECT -> { navigate(Page.SUBJECTS); subjects.selectRecord(id); }
                case NOTE -> { navigate(Page.NOTES); notes.selectRecord(id); }
            }
        }

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
