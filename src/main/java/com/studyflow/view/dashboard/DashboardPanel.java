package com.studyflow.view.dashboard;

import com.formdev.flatlaf.FlatLaf;
import com.studyflow.controller.StudyFlowController.DashboardData;
import com.studyflow.model.Subject;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
        ADD_TASK, ADD_SUBJECT, ADD_NOTE, START_FOCUS, OPEN_ATTENDANCE
    }

    private final JLabel date = ViewStyle.muted("");
    private final JLabel greeting = ViewStyle.heading("Welcome to StudyFlow");
    private final JLabel summary = ViewStyle.muted("Loading your study plan…");
    private final SummaryCard total = new SummaryCard("Total tasks", "dashboardTotal", DashboardIcon.TASKS);
    private final SummaryCard completed = new SummaryCard("Completed", "dashboardCompleted", DashboardIcon.CHECK);
    private final SummaryCard pending = new SummaryCard("Pending", "dashboardPending", DashboardIcon.CLOCK);
    private final SummaryCard overdue = new SummaryCard("Overdue", "dashboardOverdue", DashboardIcon.ALERT);
    private final SummaryCard assignments = new SummaryCard("Upcoming assignments", "dashboardUpcomingAssignments",
            DashboardIcon.CALENDAR);
    private final JPanel statistics = new JPanel(new GridLayout(0, 5, 12, 12));
    private final JPanel actions = new JPanel(new GridLayout(0, 5, 10, 10));
    private final JPanel sections = new JPanel(new GridBagLayout());
    private final JPanel todayColumn = new JPanel(new BorderLayout());
    private final JPanel planningColumn = new JPanel(new GridBagLayout());
    private final DashboardTaskList today;
    private final DashboardTaskList upcoming;
    private final DashboardTaskList priority;
    private final List<JButton> mutationShortcuts;
    private final Body body = new Body();

    private boolean wide = true;
    private int statColumns = 5;
    private int actionColumns = 5;

    public DashboardPanel(Consumer<Action> navigate, Consumer<String> completeTask) {
        super(new BorderLayout());
        Objects.requireNonNull(navigate, "navigate");
        Objects.requireNonNull(completeTask, "completeTask");
        setName("dashboardPanel");

        today = new DashboardTaskList("Today's tasks", "dashboardToday", "Nothing is due today.", 5, completeTask);
        upcoming = new DashboardTaskList("Upcoming deadlines", "dashboardUpcoming",
                "No deadlines in the next seven days.", 3, null);
        priority = new DashboardTaskList("Smart priority", "dashboardSmartPriority",
                "No pending tasks to prioritize.", 3, null);

        JPanel hero = createWelcome();
        statistics.setOpaque(false);
        for (SummaryCard card : List.of(total, completed, pending, overdue, assignments)) {
            statistics.add(card);
        }

        actions.setOpaque(false);
        JButton addTask = action("Add task", "dashboardAddTask", DashboardIcon.PLUS, navigate, Action.ADD_TASK);
        JButton addSubject = action("Add subject", "dashboardAddSubject", DashboardIcon.BOOK, navigate,
                Action.ADD_SUBJECT);
        JButton addNote = action("Add note", "dashboardAddNote", DashboardIcon.NOTE, navigate, Action.ADD_NOTE);
        mutationShortcuts = List.of(addTask, addSubject, addNote);
        actions.add(ViewStyle.primary(addTask));
        actions.add(addSubject);
        actions.add(addNote);
        actions.add(action("Start focus session", "dashboardStartFocus", DashboardIcon.CLOCK, navigate,
                Action.START_FOCUS));
        actions.add(action("Open attendance", "dashboardOpenAttendance", DashboardIcon.ATTENDANCE, navigate,
                Action.OPEN_ATTENDANCE));

        todayColumn.setOpaque(false);
        todayColumn.add(today, BorderLayout.PAGE_START);
        planningColumn.setOpaque(false);
        addRow(planningColumn, priority, 0, 14);
        addRow(planningColumn, upcoming, 1, 0);
        sections.setOpaque(false);
        arrangeTaskSections(true);

        addRow(body, hero, 0, 16);
        addRow(body, statistics, 1, 14);
        addRow(body, actions, 2, 20);
        addRow(body, sections, 3, 0);
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = 4;
        filler.weighty = 1;
        body.add(new JPanel(), filler);

        JScrollPane scroll = ViewStyle.scroll(body);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().addChangeListener(event -> updateLayout(scroll.getViewport().getExtentSize().width));
        add(scroll, BorderLayout.CENTER);
    }

    public void showData(DashboardData data) {
        date.setText(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(data.date()));
        int hour = LocalTime.now().getHour();
        greeting.setText(hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening");
        summary.setText(data.today().size() + " due today  ·  " + data.overdue() + " overdue  ·  "
                + data.pending() + " pending");
        total.setValue(data.total());
        completed.setValue(data.completed());
        pending.setValue(data.pending());
        overdue.setValue(data.overdue());
        assignments.setValue(data.upcomingAssignments());

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
    }

    private JPanel createWelcome() {
        JPanel hero = new WelcomePanel();
        hero.setLayout(new GridBagLayout());
        hero.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        date.setName("dashboardDate");
        summary.setName("dashboardSummary");
        addRow(hero, date, 0, 5);
        addRow(hero, greeting, 1, 5);
        addRow(hero, summary, 2, 0);
        return hero;
    }

    private static JButton action(String label, String name, DashboardIcon icon, Consumer<Action> navigate,
                                  Action action) {
        JButton button = new JButton(label, icon);
        button.setName(name);
        button.setHorizontalAlignment(SwingConstants.LEADING);
        button.setIconTextGap(8);
        button.setMargin(new Insets(8, 10, 8, 10));
        button.addActionListener(event -> navigate.accept(action));
        return button;
    }

    private void updateLayout(int viewportWidth) {
        if (viewportWidth <= 0) {
            return;
        }
        int width = viewportWidth - body.getInsets().left - body.getInsets().right;
        int nextStatColumns = width >= 750 ? 5 : width >= 440 ? 3 : 2;
        int actionWidth = 0;
        for (Component action : actions.getComponents()) {
            actionWidth = Math.max(actionWidth, action.getPreferredSize().width);
        }
        int nextActionColumns = width >= 5 * actionWidth + 40 ? 5 : width >= 3 * actionWidth + 20 ? 3 : 2;
        boolean nextWide = width >= 720;
        if (statColumns != nextStatColumns) {
            statColumns = nextStatColumns;
            statistics.setLayout(new GridLayout(0, statColumns, 12, 12));
            statistics.revalidate();
        }
        if (actionColumns != nextActionColumns) {
            actionColumns = nextActionColumns;
            actions.setLayout(new GridLayout(0, actionColumns, 10, 10));
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
            cell.fill = GridBagConstraints.HORIZONTAL;
            cell.anchor = GridBagConstraints.PAGE_START;
            cell.insets = new Insets(0, 0, 0, 7);
            sections.add(todayColumn, cell);
            cell.gridx = 1;
            cell.insets = new Insets(0, 7, 0, 0);
            sections.add(planningColumn, cell);
        } else {
            addRow(sections, todayColumn, 0, 14);
            addRow(sections, planningColumn, 1, 0);
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
            setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));
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

    /** Paints the welcome section in colors suited to the active FlatLaf theme. */
    private static final class WelcomePanel extends JPanel {
        WelcomePanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color start = FlatLaf.isLafDark() ? new Color(0x262039) : new Color(0xF1ECFF);
                Color end = FlatLaf.isLafDark() ? new Color(0x222735) : new Color(0xEEF3FF);
                canvas.setPaint(new GradientPaint(0, 0, start, Math.max(1, getWidth()), getHeight(), end));
                canvas.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                canvas.setColor(ViewStyle.color("border"));
                canvas.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
            } finally {
                canvas.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}
