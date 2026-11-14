package com.studyflow.view.planning;

import com.studyflow.model.Recurrence;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import java.awt.Component;
import java.awt.GridLayout;
import java.time.LocalDate;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Collects one planning operation so each change uses one existing task transaction. */
public final class SubtaskDialog {
    private SubtaskDialog() { }

    public static Edit show(Component owner, Task task) {
        String choice = (String) JOptionPane.showInputDialog(owner, "Choose an action for " + task.getTitle(),
                "Task planning", JOptionPane.PLAIN_MESSAGE, null,
                new String[]{"Recurrence", "Add subtask", "Update subtask"}, "Recurrence");
        if (choice == null) {
            return null;
        }
        return switch (choice) {
            case "Recurrence" -> {
                Recurrence value = (Recurrence) JOptionPane.showInputDialog(owner, "Repeat on completion",
                        "Recurrence", JOptionPane.PLAIN_MESSAGE, null, Recurrence.values(), task.getRecurrence());
                yield value == null ? null : new Repeat(value);
            }
            case "Add subtask" -> add(owner);
            default -> update(owner, task);
        };
    }

    private static Edit add(Component owner) {
        JTextField title = new JTextField();
        JTextField deadline = new JTextField();
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 5));
        fields.add(new JLabel("Title"));
        fields.add(title);
        fields.add(new JLabel("Deadline (optional, YYYY-MM-DD)"));
        fields.add(deadline);
        while (JOptionPane.showConfirmDialog(owner, fields, "Add subtask", JOptionPane.OK_CANCEL_OPTION)
                == JOptionPane.OK_OPTION) {
            try {
                if (title.getText().isBlank()) {
                    throw new IllegalArgumentException("Enter a title.");
                }
                return new Add(title.getText(), deadline.getText().isBlank() ? null
                        : LocalDate.parse(deadline.getText().strip()));
            } catch (java.time.DateTimeException | IllegalArgumentException error) {
                JOptionPane.showMessageDialog(owner, "Enter a title and use YYYY-MM-DD for the deadline.");
            }
        }
        return null;
    }

    private static Edit update(Component owner, Task task) {
        JComboBox<Choice> children = new JComboBox<>();
        addChildren(children, task);
        if (children.getItemCount() == 0) {
            JOptionPane.showMessageDialog(owner, "This task has no subtasks.");
            return null;
        }
        JComboBox<String> action = new JComboBox<>(new String[]{"Complete", "To do", "In progress", "Delete"});
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 5));
        fields.add(children);
        fields.add(action);
        if (JOptionPane.showConfirmDialog(owner, fields, "Update subtask", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return null;
        }
        String id = children.getItemAt(children.getSelectedIndex()).task().getId();
        return switch (action.getSelectedIndex()) {
            case 0 -> new Status(id, TaskStatus.COMPLETED);
            case 1 -> new Status(id, TaskStatus.TODO);
            case 2 -> new Status(id, TaskStatus.IN_PROGRESS);
            default -> JOptionPane.showConfirmDialog(owner, "Delete this subtask and its children?", "Delete subtask",
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION ? new Remove(id) : null;
        };
    }

    private static void addChildren(JComboBox<Choice> children, Task task) {
        for (Task child : task.getSubtasks()) {
            children.addItem(new Choice(child));
            addChildren(children, child);
        }
    }

    private record Choice(Task task) {
        @Override
        public String toString() {
            return task.getTitle() + " · " + task.getStatus()
                    + (task.getDueDate() == null ? "" : " · " + task.getDueDate());
        }
    }

    public sealed interface Edit permits Repeat, Add, Status, Remove { }
    public record Repeat(Recurrence value) implements Edit { }
    public record Add(String title, LocalDate due) implements Edit { }
    public record Status(String id, TaskStatus status) implements Edit { }
    public record Remove(String id) implements Edit { }
}
