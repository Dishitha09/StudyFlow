package com.studyflow.view.planning;

import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Displays the current task distribution and forwards status changes to the task controller. */
public final class KanbanPanel extends JPanel {
    private final BiConsumer<String, TaskStatus> moveTask;
    private final Consumer<String> openTask;
    private final KanbanProgress progress = new KanbanProgress();
    private final Map<TaskStatus, Column> columns = new EnumMap<>(TaskStatus.class);
    private final List<KanbanCard> taskCards = new ArrayList<>();
    private boolean busy;

    public KanbanPanel(BiConsumer<String, TaskStatus> moveTask, Consumer<String> openTask) {
        super(new BorderLayout(0, 16));
        this.moveTask = moveTask;
        this.openTask = openTask;
        setName("kanbanPanel");
        setBorder(BorderFactory.createEmptyBorder(24, 26, 20, 26));

        JPanel header = new JPanel(new BorderLayout(0, 12));
        header.setOpaque(false);
        header.add(ViewStyle.heading("Kanban Board"), BorderLayout.PAGE_START);
        header.add(progress, BorderLayout.CENTER);
        add(header, BorderLayout.PAGE_START);

        JPanel board = new JPanel(new GridLayout(1, 3, 12, 0));
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
        LocalDate today = LocalDate.now();
        long overdue = tasks.stream().filter(task -> task.isOverdue(today)).count();
        progress.showCounts(grouped.get(TaskStatus.TODO).size(), grouped.get(TaskStatus.IN_PROGRESS).size(),
                grouped.get(TaskStatus.COMPLETED).size(), overdue);
        taskCards.clear();
        columns.forEach((status, column) -> showColumn(status, column, grouped.get(status), names, today));
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        taskCards.forEach(card -> card.setBusy(busy));
    }

    private static Column createColumn(TaskStatus status) {
        KanbanTone tone = KanbanTone.forStatus(status);
        JPanel container = new JPanel(new BorderLayout(0, 9));
        container.setOpaque(false);
        container.setName("kanbanColumn-" + status);
        container.getAccessibleContext().setAccessibleName(PlanningLabels.status(status));

        JPanel header = ViewStyle.card(new BorderLayout(8, 0));
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JLabel heading = ViewStyle.subheading(PlanningLabels.status(status));
        heading.setIcon(tone.dot());
        heading.setIconTextGap(8);
        header.add(heading, BorderLayout.CENTER);
        JLabel count = tone.pill("0");
        count.setName("kanbanCount-" + status);
        header.add(count, BorderLayout.LINE_END);
        container.add(header, BorderLayout.PAGE_START);

        JPanel cards = new PlanningScrollContent(null);
        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        JScrollPane scroll = ViewStyle.scroll(cards);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        container.add(scroll, BorderLayout.CENTER);
        return new Column(container, count, cards);
    }

    private void showColumn(TaskStatus status, Column column, List<Task> tasks,
                            Map<String, String> names, LocalDate today) {
        column.count().setText(Integer.toString(tasks.size()));
        column.count().getAccessibleContext().setAccessibleName(tasks.size() + " " + PlanningLabels.status(status));
        JPanel cards = column.cards();
        cards.removeAll();
        if (tasks.isEmpty()) {
            JPanel placeholder = ViewStyle.card(new BorderLayout());
            placeholder.setBorder(BorderFactory.createEmptyBorder(16, 13, 16, 13));
            placeholder.setAlignmentX(Component.LEFT_ALIGNMENT);
            placeholder.add(ViewStyle.muted("No tasks in this column"), BorderLayout.CENTER);
            placeholder.setMaximumSize(new Dimension(Integer.MAX_VALUE, placeholder.getPreferredSize().height));
            cards.add(placeholder);
        } else {
            for (Task task : tasks) {
                KanbanCard card = new KanbanCard(task, PlanningLabels.subject(names, task),
                        today, moveTask, openTask, busy);
                taskCards.add(card);
                cards.add(card);
                cards.add(Box.createVerticalStrut(8));
            }
        }
        cards.revalidate();
        cards.repaint();
    }

    private record Column(JPanel container, JLabel count, JPanel cards) {
    }
}
