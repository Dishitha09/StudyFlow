package com.studyflow.view.dashboard;

import com.studyflow.controller.StudyFlowController.DashboardData;
import com.studyflow.model.Subject;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/** Presents the controller's current academic overview and dispatches dashboard actions. */
public final class DashboardPanel extends JPanel {
    public enum Action {
        ADD_TASK, ADD_SUBJECT, ADD_NOTE, START_FOCUS, OPEN_ATTENDANCE, OPEN_TASKS, OPEN_CALENDAR
    }

    private final JLabel date = ViewStyle.muted("");
    private final JLabel greeting = ViewStyle.heading("Welcome to StudyFlow");
    private final JLabel summary = ViewStyle.muted("Loading your study plan…");
    private final SummaryCard dueToday = new SummaryCard("Due today", "dashboardDueToday", DashboardIcon.TASKS);
    private final SummaryCard completed = new SummaryCard("Completed", "dashboardCompleted", DashboardIcon.CHECK);
    private final SummaryCard pending = new SummaryCard("Pending", "dashboardPending", DashboardIcon.CLOCK);
    private final SummaryCard overdue = new SummaryCard("Overdue", "dashboardOverdue", DashboardIcon.ALERT);
    private final SummaryCard nextSevenDays = new SummaryCard("Next 7 days", "dashboardNextSevenDays",
            DashboardIcon.CALENDAR);
    private final JPanel statistics = new JPanel(new GridLayout(0, 5, 10, 10));
    private final JPanel actions = new JPanel(new GridLayout(0, 4, 8, 8));
    private final JPanel sections = new JPanel(new GridBagLayout());
    private final DashboardTaskList today;
    private final DashboardTaskList upcoming;
    private final DashboardTaskList priority;
    private final List<JButton> mutationShortcuts;
    private final Body body = new Body();

    private boolean wide = true;
    private int statColumns = 5;
    private int actionColumns = 4;

    public DashboardPanel(Consumer<Action> navigate, Consumer<String> completeTask, Consumer<String> openTask) {
        super(new BorderLayout());
        Objects.requireNonNull(navigate, "navigate");
        Objects.requireNonNull(completeTask, "completeTask");
        Objects.requireNonNull(openTask, "openTask");
        setName("dashboardPanel");

        today = new DashboardTaskList("Today's Plan", "dashboardToday", "Nothing is due today.",
                "Add a task", () -> navigate.accept(Action.ADD_TASK), 3, openTask, completeTask);
        upcoming = new DashboardTaskList("Upcoming deadlines", "dashboardUpcoming",
                "No deadlines in the next seven days.", "Open calendar", () -> navigate.accept(Action.OPEN_CALENDAR),
                3, openTask, null);
        priority = new DashboardTaskList("Smart Priority", "dashboardSmartPriority",
                "No pending tasks to prioritize.", "View tasks", () -> navigate.accept(Action.OPEN_TASKS),
                2, openTask, null);

        JButton addTask = ViewStyle.primary(action("Add task", "dashboardAddTask", DashboardIcon.PLUS,
                navigate, Action.ADD_TASK));
        JPanel hero = createWelcome(addTask);
        statistics.setOpaque(false);
        for (SummaryCard card : List.of(dueToday, completed, overdue, nextSevenDays, pending)) {
            statistics.add(card);
        }

        actions.setOpaque(false);
        JButton addSubject = action("Add subject", "dashboardAddSubject", DashboardIcon.BOOK, navigate,
                Action.ADD_SUBJECT);
        JButton addNote = action("Add note", "dashboardAddNote", DashboardIcon.NOTE, navigate, Action.ADD_NOTE);
        mutationShortcuts = List.of(addTask, addSubject, addNote);
        actions.add(addSubject);
        actions.add(addNote);
        actions.add(action("Start focus session", "dashboardStartFocus", DashboardIcon.CLOCK, navigate,
                Action.START_FOCUS));
        actions.add(action("Open attendance", "dashboardOpenAttendance", DashboardIcon.ATTENDANCE, navigate,
                Action.OPEN_ATTENDANCE));

        sections.setOpaque(false);
        arrangeTaskSections(true);

        addRow(body, hero, 0, 12);
        addRow(body, statistics, 1, 10);
        addRow(body, actions, 2, 14);
        addRow(body, sections, 3, 14);
        addRow(body, priority, 4, 0);

        JScrollPane scroll = ViewStyle.scroll(body);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().addChangeListener(event -> updateLayout(scroll.getViewport().getExtentSize().width));
        add(scroll, BorderLayout.CENTER);
    }

    public void showData(DashboardData data) {
        date.setText(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(data.date()));
        int hour = LocalTime.now().getHour();
        greeting.setText(hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening");
        summary.setText(data.total() == 0 ? "Add a subject or task to start your study plan."
                : data.pending() + " open of " + data.total() + " tasks  ·  "
                + data.upcoming().size() + " deadlines in the next week");
        dueToday.setValue(data.today().size());
        completed.setValue(data.completed());
        pending.setValue(data.pending());
        overdue.setValue(data.overdue());
        nextSevenDays.setValue(data.upcoming().size());

        Map<String, String> subjects = data.subjects().stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName));
        today.showTasks(data.today(), subjects, data.date());
        upcoming.showTasks(data.upcoming(), subjects, data.date());
        priority.showTasks(data.smartPriority(), subjects, data.date());
    }

    /** Disables actions that may write records while a command is in flight. */
    public void setBusy(boolean busy) {
        mutationShortcuts.forEach(button -> button.setEnabled(!busy));
        today.setBusy(busy);
        upcoming.setBusy(busy);
        priority.setBusy(busy);
    }

    private JPanel createWelcome(JButton addTask) {
        JPanel hero = ViewStyle.card(new BorderLayout(12, 0));
        hero.setBorder(BorderFactory.createEmptyBorder(12, 17, 12, 17));
        JPanel copy = new JPanel(new GridBagLayout());
        copy.setOpaque(false);
        date.setName("dashboardDate");
        summary.setName("dashboardSummary");
        addRow(copy, greeting, 0, 3);
        addRow(copy, date, 1, 2);
        addRow(copy, summary, 2, 0);
        hero.add(copy, BorderLayout.CENTER);
        JPanel controls = new JPanel(new GridBagLayout());
        controls.setOpaque(false);
        controls.add(addTask);
        hero.add(controls, BorderLayout.LINE_END);
        return hero;
    }

    private static JButton action(String label, String name, DashboardIcon icon, Consumer<Action> navigate,
                                  Action action) {
        JButton button = new JButton(label, icon);
        button.setName(name);
        button.setHorizontalAlignment(SwingConstants.LEADING);
        button.setIconTextGap(8);
        button.setMargin(new Insets(5, 9, 5, 9));
        button.addActionListener(event -> navigate.accept(action));
        return button;
    }

    private void updateLayout(int viewportWidth) {
        if (viewportWidth <= 0) {
            return;
        }
        int width = viewportWidth - body.getInsets().left - body.getInsets().right;
        int nextStatColumns = width >= 688 ? 5 : width >= 408 ? 3 : 2;
        int actionWidth = 0;
        for (Component action : actions.getComponents()) {
            actionWidth = Math.max(actionWidth, action.getPreferredSize().width);
        }
        int nextActionColumns = width >= 4 * actionWidth + 24 ? 4 : 2;
        boolean nextWide = width >= 650;
        if (statColumns != nextStatColumns) {
            statColumns = nextStatColumns;
            statistics.setLayout(new GridLayout(0, statColumns, 10, 10));
            statistics.revalidate();
        }
        if (actionColumns != nextActionColumns) {
            actionColumns = nextActionColumns;
            actions.setLayout(new GridLayout(0, actionColumns, 8, 8));
            actions.revalidate();
        }
        if (wide != nextWide) {
            wide = nextWide;
            arrangeTaskSections(wide);
        }
    }

    private void arrangeTaskSections(boolean sideBySide) {
        sections.removeAll();
        if (sideBySide) {
            GridBagConstraints cell = new GridBagConstraints();
            cell.weightx = 1;
            cell.fill = GridBagConstraints.BOTH;
            cell.anchor = GridBagConstraints.PAGE_START;
            cell.insets = new Insets(0, 0, 0, 7);
            sections.add(today, cell);
            cell.gridx = 1;
            cell.insets = new Insets(0, 7, 0, 0);
            sections.add(upcoming, cell);
        } else {
            addRow(sections, today, 0, 14);
            addRow(sections, upcoming, 1, 0);
        }
        sections.revalidate();
        sections.repaint();
    }

    private static void addRow(JPanel panel, Component row, int index, int gap) {
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = 0;
        cell.gridy = index;
        cell.weightx = 1;
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.insets = new Insets(0, 0, gap, 0);
        panel.add(row, cell);
    }

    /** Tracks the viewport width while allowing the entire dashboard to scroll vertically. */
    private static final class Body extends JPanel implements Scrollable {
        Body() {
            super(new GridBagLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(18, ViewStyle.PAGE_PADDING, 20, ViewStyle.PAGE_PADDING));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(18, visibleRect.height - 36);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

}
