package com.studyflow.view.planning;

import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;

/** Presents a task with visible urgency and the existing keyboard-accessible move control. */
final class KanbanCard extends JPanel {
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    private final JButton move = new JButton("Move ▾");

    KanbanCard(Task task, String subjectName, LocalDate today, BiConsumer<String, TaskStatus> moveTask,
               Consumer<String> openTask, boolean busy) {
        super(new BorderLayout());
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel card = ViewStyle.card(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        add(card);

        JPanel tags = transparent(new BorderLayout(4, 0));
        KanbanTone priorityTone = switch (task.getPriority()) {
            case HIGH -> KanbanTone.URGENT;
            case MEDIUM -> KanbanTone.MEDIUM;
            case LOW -> KanbanTone.TODO;
        };
        tags.add(KanbanTone.TODO.pill(PlanningLabels.type(task.getType())), BorderLayout.LINE_START);
        JLabel priority = priorityTone.pill(PlanningLabels.priority(task.getPriority()));
        priority.setToolTipText(PlanningLabels.priority(task.getPriority()) + " priority");
        tags.add(priority, BorderLayout.LINE_END);
        card.add(tags, BorderLayout.PAGE_START);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JButton title = new JButton();
        title.putClientProperty("html.disable", Boolean.TRUE);
        title.putClientProperty("JButton.buttonType", "borderless");
        title.putClientProperty("FlatLaf.style", "arc: 8; margin: 3, 0, 3, 0");
        title.setName("kanbanTask-" + task.getId());
        title.setText(task.getTitle());
        title.setToolTipText("Open task: " + task.getTitle());
        title.setHorizontalAlignment(SwingConstants.LEADING);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, title.getPreferredSize().height));
        title.addActionListener(event -> openTask.accept(task.getId()));
        content.add(title);
        JLabel subject = ViewStyle.muted("");
        subject.putClientProperty("html.disable", Boolean.TRUE);
        subject.setText(subjectName);
        subject.setToolTipText("Subject: " + subjectName);
        subject.setAlignmentX(LEFT_ALIGNMENT);
        content.add(subject);
        card.add(content, BorderLayout.CENTER);

        JPanel footer = transparent(new BorderLayout(0, 6));
        JPanel due = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        due.setOpaque(false);
        due.add(deadline(task, today));
        footer.add(due, BorderLayout.PAGE_START);
        configureMove(task, moveTask, busy);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 0, 0));
        actions.setOpaque(false);
        actions.add(move);
        footer.add(actions, BorderLayout.PAGE_END);
        card.add(footer, BorderLayout.PAGE_END);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
    }

    void setBusy(boolean busy) {
        move.setEnabled(!busy);
    }

    private void configureMove(Task task, BiConsumer<String, TaskStatus> moveTask, boolean busy) {
        move.setName("kanbanMove-" + task.getId());
        move.putClientProperty("FlatLaf.style", "arc: 8; margin: 4, 7, 4, 7");
        move.getAccessibleContext().setAccessibleDescription("Change status for " + task.getTitle());
        move.setToolTipText("Change status");
        move.setEnabled(!busy);
        move.addActionListener(event -> {
            JPopupMenu choices = new JPopupMenu();
            for (TaskStatus destination : TaskStatus.values()) {
                if (destination == task.getStatus()
                        || task.getStatus() == TaskStatus.COMPLETED && destination == TaskStatus.IN_PROGRESS) {
                    continue;
                }
                JMenuItem item = new JMenuItem(PlanningLabels.status(destination));
                item.setName("kanbanDestination-" + destination);
                item.addActionListener(selection -> {
                    if (move.isEnabled()) {
                        moveTask.accept(task.getId(), destination);
                    }
                });
                choices.add(item);
            }
            choices.show(move, 0, move.getHeight());
        });
    }

    private static JLabel deadline(Task task, LocalDate today) {
        if (task.getDueDate() == null) {
            return ViewStyle.muted("No deadline");
        }
        LocalDate due = task.getDueDate();
        boolean overdue = task.isOverdue(today);
        String label = overdue ? "Overdue · " + SHORT_DATE.format(due)
                : due.equals(today) ? "Due today" : "Due " + SHORT_DATE.format(due);
        JLabel deadline = overdue ? KanbanTone.URGENT.pill(label) : ViewStyle.muted(label);
        deadline.setName("kanbanDeadline-" + task.getId());
        deadline.setToolTipText("Deadline: " + LONG_DATE.format(due));
        return deadline;
    }

    private static JPanel transparent(BorderLayout layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }
}
