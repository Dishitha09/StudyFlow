package com.studyflow.view.planning;

import com.studyflow.model.Priority;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/** Presents tasks by their persisted status and forwards moves to the task controller. */
public final class KanbanPanel extends JPanel {
    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    private final BiConsumer<String, TaskStatus> moveTask;
    private final Consumer<String> openTask;
    private final Map<TaskStatus, Column> columns = new EnumMap<>(TaskStatus.class);
    private final List<JButton> moveButtons = new ArrayList<>();
    private boolean busy;

    public KanbanPanel(BiConsumer<String, TaskStatus> moveTask, Consumer<String> openTask) {
        super(new BorderLayout(0, 14));
        this.moveTask = moveTask;
        this.openTask = openTask;
        setName("kanbanPanel");
        setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));
        add(ViewStyle.heading("Kanban Board"), BorderLayout.PAGE_START);
        JPanel board = new JPanel(new GridLayout(1, 3, 10, 0));
        board.setOpaque(false);
        for (TaskStatus status : TaskStatus.values()) {
            Column column = createColumn(status);
            columns.put(status, column);
            board.add(column.container());
        }
        add(board, BorderLayout.CENTER);
        showTasks(List.of(), List.of());
    }

    public void showTasks(List<Task> tasks, List<Subject> subjects) {
        Map<String, String> names = PlanningLabels.subjectNames(subjects);
        Map<TaskStatus, List<Task>> grouped = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            grouped.put(status, new ArrayList<>());
        }
        tasks.stream().sorted().forEach(task -> grouped.get(task.getStatus()).add(task));
        moveButtons.clear();
        columns.forEach((status, column) -> showColumn(status, column, grouped.get(status), names));
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        moveButtons.forEach(button -> button.setEnabled(!busy));
    }

    private Column createColumn(TaskStatus status) {
        JPanel container = ViewStyle.card(new BorderLayout(0, 9));
        container.setBorder(BorderFactory.createEmptyBorder(11, 9, 11, 9));
        container.setName("kanbanColumn-" + status);
        container.getAccessibleContext().setAccessibleName(PlanningLabels.status(status));
        JLabel heading = ViewStyle.subheading("");
        heading.setBorder(BorderFactory.createEmptyBorder(1, 3, 4, 3));
        container.add(heading, BorderLayout.PAGE_START);
        JPanel cards = new PlanningScrollContent(null);
        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        JScrollPane scroll = ViewStyle.scroll(cards);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        container.add(scroll, BorderLayout.CENTER);
        return new Column(container, heading, cards);
    }

    private void showColumn(TaskStatus status, Column column, List<Task> tasks, Map<String, String> names) {
        column.heading().setText(PlanningLabels.status(status) + "  " + tasks.size());
        JPanel cards = column.cards();
        cards.removeAll();
        if (tasks.isEmpty()) {
            JLabel empty = ViewStyle.muted("No tasks");
            empty.setBorder(BorderFactory.createEmptyBorder(8, 3, 6, 3));
            cards.add(empty);
        } else {
            for (Task task : tasks) {
                cards.add(taskCard(task, names));
                cards.add(Box.createVerticalStrut(7));
            }
        }
        cards.revalidate();
        cards.repaint();
    }

    private JPanel taskCard(Task task, Map<String, String> names) {
        JPanel card = ViewStyle.card(new BorderLayout(0, 6));
        card.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton title = new JButton();
        title.putClientProperty("html.disable", Boolean.TRUE);
        title.setText(task.getTitle());
        title.setName("kanbanTask-" + task.getId());
        title.setToolTipText("Open task: " + task.getTitle());
        title.setHorizontalAlignment(SwingConstants.LEADING);
        title.setMargin(new java.awt.Insets(3, 5, 3, 5));
        title.addActionListener(event -> openTask.accept(task.getId()));
        card.add(title, BorderLayout.PAGE_START);

        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JLabel subject = ViewStyle.muted("");
        subject.putClientProperty("html.disable", Boolean.TRUE);
        String subjectName = PlanningLabels.subject(names, task);
        subject.setText(subjectName);
        subject.setToolTipText("Subject: " + subjectName);
        details.add(subject);
        String due = task.getDueDate() == null ? "No deadline" : DEADLINE.format(task.getDueDate());
        JLabel deadline = ViewStyle.muted(due);
        deadline.setToolTipText("Deadline: " + due);
        details.add(deadline);
        JLabel priority = new JLabel(PlanningLabels.priority(task.getPriority()) + " priority");
        if (task.getPriority() == Priority.HIGH) {
            priority.setForeground(ViewStyle.color("danger"));
        }
        details.add(priority);
        card.add(details, BorderLayout.CENTER);
        card.add(moveButton(task), BorderLayout.PAGE_END);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private JButton moveButton(Task task) {
        JButton move = new JButton("Move to…");
        move.setName("kanbanMove-" + task.getId());
        move.getAccessibleContext().setAccessibleDescription("Change status for " + task.getTitle());
        move.setEnabled(!busy);
        moveButtons.add(move);
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
                    if (!busy) {
                        moveTask.accept(task.getId(), destination);
                    }
                });
                choices.add(item);
            }
            choices.show(move, 0, move.getHeight());
        });
        return move;
    }

    private record Column(JPanel container, JLabel heading, JPanel cards) {
    }
}
