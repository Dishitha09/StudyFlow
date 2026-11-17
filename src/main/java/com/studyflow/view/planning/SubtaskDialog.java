package com.studyflow.view.planning;

import com.studyflow.model.Recurrence;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.view.FormDialogs;
import java.awt.Component;
import java.awt.GridLayout;
import java.time.DateTimeException;
import java.time.LocalDate;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
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
        JTextField title = new JTextField(25);
        title.setName("subtaskTitle");
        JTextField deadline = new JTextField(25);
        deadline.setName("subtaskDeadline");
        deadline.putClientProperty("JTextField.placeholderText", "YYYY-MM-DD");
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 5));
        addField(fields, "Title", title);
        addField(fields, "Deadline (optional, YYYY-MM-DD)", deadline);
        return FormDialogs.show(owner, "Add subtask", fields, title, "Add",
                () -> !title.getText().isEmpty() || !deadline.getText().isEmpty(), () -> {
            if (title.getText().isBlank()) {
                title.requestFocusInWindow();
                throw new IllegalArgumentException("Enter a title.");
            }
            try {
                return new Add(title.getText(), deadline.getText().isBlank() ? null
                        : LocalDate.parse(deadline.getText().strip()));
            } catch (DateTimeException invalid) {
                deadline.requestFocusInWindow();
                throw new IllegalArgumentException("Use YYYY-MM-DD for the deadline.", invalid);
            }
        });
    }

    private static Edit update(Component owner, Task task) {
        JComboBox<Choice> children = new JComboBox<>();
        children.setName("subtaskSelection");
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        children.setRenderer(renderer);
        addChildren(children, task);
        if (children.getItemCount() == 0) {
            JOptionPane.showMessageDialog(owner, "This task has no subtasks.");
            return null;
        }
        JComboBox<String> action = new JComboBox<>(new String[]{"Complete", "To do", "In progress", "Delete"});
        action.setName("subtaskAction");
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 5));
        addField(fields, "Subtask", children);
        addField(fields, "Action", action);
        return FormDialogs.show(owner, "Update subtask", fields, children, "Apply",
                () -> children.getSelectedIndex() != 0 || action.getSelectedIndex() != 0, () -> {
            String id = children.getItemAt(children.getSelectedIndex()).task().getId();
            return switch (action.getSelectedIndex()) {
                case 0 -> new Status(id, TaskStatus.COMPLETED);
                case 1 -> new Status(id, TaskStatus.TODO);
                case 2 -> new Status(id, TaskStatus.IN_PROGRESS);
                default -> JOptionPane.showConfirmDialog(fields, "Delete this subtask and its children?",
                        "Delete subtask", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION
                        ? new Remove(id) : null;
            };
        });
    }

    private static void addField(JPanel fields, String text, JComponent input) {
        JLabel label = new JLabel(text);
        label.setLabelFor(input);
        fields.add(label);
        fields.add(input);
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
            return task.getTitle() + " · " + PlanningLabels.status(task.getStatus())
                    + (task.getDueDate() == null ? "" : " · " + task.getDueDate());
        }
    }

    public sealed interface Edit permits Repeat, Add, Status, Remove { }
    public record Repeat(Recurrence value) implements Edit { }
    public record Add(String title, LocalDate due) implements Edit { }
    public record Status(String id, TaskStatus status) implements Edit { }
    public record Remove(String id) implements Edit { }
}
