package com.studyflow.view.dashboard;

import com.formdev.flatlaf.FlatLaf;
import com.studyflow.model.Priority;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/** Displays every task in a dashboard section, with a bounded scroll area for longer lists. */
final class DashboardTaskList extends JPanel {
    private static final DateTimeFormatter DUE_DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    private static final int ROW_HEIGHT = 75;

    private final Rows rows = new Rows();
    private final JScrollPane scroll;
    private final String emptyMessage;
    private final int rowsVisible;
    private final Consumer<String> completeTask;
    private final List<JButton> completeButtons = new ArrayList<>();
    private boolean busy;

    DashboardTaskList(String heading, String componentName, String emptyMessage, int rowsVisible,
                      Consumer<String> completeTask) {
        super(new BorderLayout());
        this.emptyMessage = emptyMessage;
        this.rowsVisible = rowsVisible;
        this.completeTask = completeTask;
        setName(componentName);
        getAccessibleContext().setAccessibleName(heading);
        setOpaque(false);

        JPanel card = ViewStyle.card(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(14, 15, 13, 15));
        card.add(ViewStyle.subheading(heading), BorderLayout.PAGE_START);
        scroll = ViewStyle.scroll(rows);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        card.add(scroll, BorderLayout.CENTER);
        add(card);
        showTasks(List.of(), Map.of(), LocalDate.now());
    }

    void showTasks(List<Task> tasks, Map<String, String> subjects, LocalDate date) {
        rows.removeAll();
        completeButtons.clear();
        if (tasks.isEmpty()) {
            JLabel empty = ViewStyle.muted(emptyMessage);
            empty.setBorder(BorderFactory.createEmptyBorder(4, 0, 9, 0));
            rows.add(empty, constraints(0));
        } else {
            for (int index = 0; index < tasks.size(); index++) {
                Task task = tasks.get(index);
                String subjectId = task.getSubjectId();
                String subject = subjectId == null ? "No subject" : subjects.getOrDefault(subjectId, "No subject");
                rows.add(row(task, subject, date, index == 0),
                        constraints(index));
            }
        }
        scroll.setPreferredSize(new Dimension(240, tasks.isEmpty() ? 35
                : ROW_HEIGHT * Math.min(tasks.size(), rowsVisible)));
        rows.revalidate();
        rows.repaint();
        revalidate();
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        completeButtons.forEach(button -> button.setEnabled(!busy));
    }

    private JComponent row(Task task, String subject, LocalDate date, boolean first) {
        JPanel row = new JPanel(new BorderLayout(9, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(240, ROW_HEIGHT));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(first ? 0 : 1, 0, 0, 0, ViewStyle.color("border")),
                BorderFactory.createEmptyBorder(7, 1, 6, 3)));

        JLabel kind = new JLabel(switch (task.getType()) {
            case ASSIGNMENT -> DashboardIcon.NOTE;
            case EXAM -> DashboardIcon.ATTENDANCE;
            case STUDY_SESSION -> DashboardIcon.CLOCK;
            case GENERAL -> DashboardIcon.TASKS;
        }, SwingConstants.CENTER);
        kind.setForeground(ViewStyle.color("accent"));
        kind.setPreferredSize(new Dimension(20, 20));
        row.add(kind, BorderLayout.LINE_START);

        JPanel text = new JPanel(new BorderLayout(0, 2));
        text.setOpaque(false);
        JLabel title = plainLabel(task.getTitle());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setToolTipText("Task: " + task.getTitle());
        text.add(title, BorderLayout.PAGE_START);

        String due = task.getDueDate() == null ? "No deadline" : DUE_DATE.format(task.getDueDate());
        JLabel context = ViewStyle.muted("");
        context.putClientProperty("html.disable", Boolean.TRUE);
        context.setText(subject + "  ·  " + due);
        context.setFont(context.getFont().deriveFont(11f));
        context.setToolTipText("Subject: " + subject + " · " + due);
        text.add(context, BorderLayout.CENTER);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEADING, 5, 0));
        chips.setOpaque(false);
        JLabel priority = plainLabel(priorityLabel(task.getPriority()));
        if (task.getPriority() == Priority.HIGH) {
            styleChip(priority, FlatLaf.isLafDark() ? new Color(0x432C36) : new Color(0xFDE9EA),
                    ViewStyle.color("danger"));
        } else {
            styleChip(priority, ViewStyle.color("accentSoft"), ViewStyle.color("accent"));
        }
        chips.add(priority);
        JLabel status = plainLabel(statusLabel(task.getStatus()));
        styleChip(status, ViewStyle.color("background"), ViewStyle.color("muted"));
        chips.add(status);
        if (task.isOverdue(date)) {
            JLabel overdue = plainLabel("Overdue");
            styleChip(overdue, FlatLaf.isLafDark() ? new Color(0x432C36) : new Color(0xFDE9EA),
                    ViewStyle.color("danger"));
            chips.add(overdue);
        }
        text.add(chips, BorderLayout.PAGE_END);
        row.add(text, BorderLayout.CENTER);

        if (completeTask != null && task.getStatus() != TaskStatus.COMPLETED) {
            JPanel buttonArea = new JPanel(new GridBagLayout());
            buttonArea.setOpaque(false);
            JButton complete = new JButton("Complete");
            complete.setName("dashboardComplete");
            complete.setToolTipText("Complete " + task.getTitle());
            complete.getAccessibleContext().setAccessibleDescription("Complete " + task.getTitle());
            complete.setEnabled(!busy);
            complete.addActionListener(event -> completeTask.accept(task.getId()));
            completeButtons.add(complete);
            buttonArea.add(complete);
            row.add(buttonArea, BorderLayout.LINE_END);
        }
        return row;
    }

    private static JLabel plainLabel(String text) {
        JLabel label = new JLabel();
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setText(text);
        return label;
    }

    private static void styleChip(JLabel chip, Color background, Color foreground) {
        chip.setFont(chip.getFont().deriveFont(Font.BOLD, 10f));
        chip.setOpaque(true);
        chip.setBackground(background);
        chip.setForeground(foreground);
        chip.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
    }

    private static String priorityLabel(Priority priority) {
        return switch (priority) {
            case HIGH -> "High";
            case MEDIUM -> "Medium";
            case LOW -> "Low";
        };
    }

    private static String statusLabel(TaskStatus status) {
        return switch (status) {
            case TODO -> "To do";
            case IN_PROGRESS -> "In progress";
            case COMPLETED -> "Completed";
        };
    }

    private static GridBagConstraints constraints(int index) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = index;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        return constraints;
    }

    private static final class Rows extends JPanel implements Scrollable {
        Rows() {
            super(new GridBagLayout());
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ROW_HEIGHT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(ROW_HEIGHT, visibleRect.height - ROW_HEIGHT);
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
