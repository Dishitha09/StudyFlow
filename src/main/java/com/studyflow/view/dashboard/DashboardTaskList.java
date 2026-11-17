package com.studyflow.view.dashboard;

import com.studyflow.model.Priority;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Displays a short task preview and expands in the dashboard's own scroll area. */
final class DashboardTaskList extends JPanel {
    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("d MMM");
    private final JPanel rows = transparent(new GridBagLayout());
    private final JPanel footer = transparent(new FlowLayout(FlowLayout.LEADING, 0, 0));
    private final JButton more = quietButton("");
    private final JButton emptyAction;
    private final String emptyMessage;
    private final int previewSize;
    private final Consumer<String> openTask;
    private final Consumer<String> completeTask;
    private final List<JButton> taskButtons = new ArrayList<>();

    private List<Task> tasks = List.of();
    private Map<String, String> subjects = Map.of();
    private LocalDate date = LocalDate.now();
    private boolean expanded;
    private boolean busy;

    DashboardTaskList(String heading, String componentName, String emptyMessage, String emptyActionLabel,
                      Runnable emptyCommand, int previewSize, Consumer<String> openTask,
                      Consumer<String> completeTask) {
        super(new BorderLayout());
        this.emptyMessage = emptyMessage;
        this.previewSize = previewSize;
        this.openTask = openTask;
        this.completeTask = completeTask;
        setName(componentName);
        getAccessibleContext().setAccessibleName(heading);
        setOpaque(false);

        JPanel card = ViewStyle.card(new BorderLayout(0, 4));
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        card.add(ViewStyle.subheading(heading), BorderLayout.PAGE_START);
        JPanel body = transparent(new BorderLayout());
        body.add(rows, BorderLayout.PAGE_START);
        card.add(body, BorderLayout.CENTER);

        more.setName(componentName + "More");
        more.addActionListener(event -> {
            expanded = !expanded;
            render();
        });
        footer.add(more);
        emptyAction = quietButton(emptyActionLabel);
        emptyAction.setName(componentName + "EmptyAction");
        emptyAction.addActionListener(event -> emptyCommand.run());
        footer.add(emptyAction);
        card.add(footer, BorderLayout.PAGE_END);
        add(card, BorderLayout.CENTER);
        render();
    }

    void showTasks(List<Task> tasks, Map<String, String> subjects, LocalDate date) {
        this.tasks = tasks;
        this.subjects = subjects;
        this.date = date;
        if (tasks.size() <= previewSize) {
            expanded = false;
        }
        render();
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        taskButtons.forEach(button -> button.setEnabled(!busy));
        emptyAction.setEnabled(!busy || completeTask == null);
    }

    private void render() {
        rows.removeAll();
        taskButtons.clear();
        if (tasks.isEmpty()) {
            JLabel empty = ViewStyle.muted(emptyMessage);
            empty.setBorder(BorderFactory.createEmptyBorder(5, 0, 4, 0));
            rows.add(empty, constraints(0));
        } else {
            int limit = expanded ? tasks.size() : Math.min(previewSize, tasks.size());
            for (int index = 0; index < limit; index++) {
                rows.add(row(tasks.get(index), index == 0), constraints(index));
            }
        }
        int beyondPreview = tasks.size() - previewSize;
        more.setVisible(beyondPreview > 0);
        more.setText(expanded ? "Show fewer" : "Show " + beyondPreview + " more");
        more.getAccessibleContext().setAccessibleDescription(getAccessibleContext().getAccessibleName());
        emptyAction.setVisible(tasks.isEmpty());
        footer.setVisible(tasks.isEmpty() || beyondPreview > 0);
        rows.revalidate();
        revalidate();
        repaint();
    }

    private JPanel row(Task task, boolean first) {
        JPanel row = transparent(new BorderLayout(6, 0));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(first ? 0 : 1, 0, 0, 0, ViewStyle.color("border")),
                BorderFactory.createEmptyBorder(4, 0, 4, 0)));
        JLabel kind = new JLabel(switch (task.getType()) {
            case ASSIGNMENT -> DashboardIcon.NOTE;
            case EXAM -> DashboardIcon.ATTENDANCE;
            case STUDY_SESSION -> DashboardIcon.CLOCK;
            case GENERAL -> DashboardIcon.TASKS;
        }, SwingConstants.CENTER);
        kind.setForeground(ViewStyle.color(task.isOverdue(date) ? "danger" : "accent"));
        kind.setPreferredSize(new Dimension(20, 20));
        row.add(kind, BorderLayout.LINE_START);

        JPanel body = transparent(new BorderLayout(4, 0));
        JButton title = quietButton(task.getTitle());
        title.setName(getName() + "Open-" + task.getId());
        title.getAccessibleContext().setAccessibleName("Open task: " + task.getTitle());
        title.setToolTipText("Open task: " + task.getTitle());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.addActionListener(event -> openTask.accept(task.getId()));
        title.setEnabled(!busy);
        taskButtons.add(title);
        JPanel lines = transparent(new BorderLayout(0, 0));
        lines.add(title, BorderLayout.PAGE_START);
        String subjectId = task.getSubjectId();
        String subject = subjectId == null ? "No subject" : subjects.getOrDefault(subjectId, "No subject");
        String deadline = task.getDueDate() == null ? "No deadline" : DEADLINE.format(task.getDueDate());
        String status = statusLabel(task.getStatus());
        JPanel metadata = transparent(new BorderLayout(2, 0));
        metadata.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
        JLabel subjectLabel = label(subject);
        subjectLabel.setToolTipText("Subject: " + subject);
        metadata.add(subjectLabel, BorderLayout.CENTER);
        JLabel deadlineAndStatus = label("·  " + deadline + "  ·  " + status);
        deadlineAndStatus.setToolTipText("Deadline: " + deadline + " | Status: " + status);
        metadata.add(deadlineAndStatus, BorderLayout.LINE_END);
        lines.add(metadata, BorderLayout.CENTER);
        body.add(lines, BorderLayout.CENTER);

        JPanel rowActions = transparent(new GridBagLayout());
        JPanel badges = transparent(new FlowLayout(FlowLayout.TRAILING, 3, 0));
        String priorityRole = task.getPriority() == Priority.HIGH ? "warning" : "accent";
        badges.add(new Badge(priorityLabel(task.getPriority()), priorityRole, priorityRole + "Soft"));
        if (task.isOverdue(date)) {
            badges.add(new Badge("Overdue", "danger", "dangerSoft"));
        }
        rowActions.add(badges);
        if (completeTask != null && task.getStatus() != TaskStatus.COMPLETED) {
            JButton complete = new JButton("Complete");
            complete.setMargin(new Insets(2, 5, 2, 5));
            complete.setName("dashboardComplete");
            complete.setToolTipText("Complete " + task.getTitle());
            complete.getAccessibleContext().setAccessibleName("Complete " + task.getTitle());
            complete.setEnabled(!busy);
            complete.addActionListener(event -> completeTask.accept(task.getId()));
            taskButtons.add(complete);
            rowActions.add(complete);
        }
        body.add(rowActions, BorderLayout.LINE_END);
        row.add(body, BorderLayout.CENTER);
        row.setPreferredSize(new Dimension(250, row.getPreferredSize().height));
        row.setMinimumSize(new Dimension(0, row.getMinimumSize().height));
        return row;
    }

    private static JButton quietButton(String text) {
        JButton button = new JButton();
        button.putClientProperty("html.disable", Boolean.TRUE);
        button.setText(text);
        button.setHorizontalAlignment(SwingConstants.LEADING);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.putClientProperty("FlatLaf.style", "arc: 7; toolbar.margin: 2,3,2,3; toolbar.spacingInsets: 0,0,0,0");
        return button;
    }

    private static JLabel label(String text) {
        JLabel label = ViewStyle.muted("");
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setText(text);
        label.setFont(label.getFont().deriveFont(12f));
        return label;
    }

    private static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
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

    private static final class Badge extends JLabel {
        private final String textRole;
        private final String surfaceRole;

        Badge(String text, String textRole, String surfaceRole) {
            super(text);
            this.textRole = textRole;
            this.surfaceRole = surfaceRole;
            setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            setFont(getFont().deriveFont(Font.BOLD, 12f));
        }

        @Override
        public Color getForeground() {
            return textRole == null ? super.getForeground() : ViewStyle.color(textRole);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                canvas.setColor(ViewStyle.color(surfaceRole));
                canvas.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            } finally {
                canvas.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}
