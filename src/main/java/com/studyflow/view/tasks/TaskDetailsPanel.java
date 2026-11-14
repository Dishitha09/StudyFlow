package com.studyflow.view.tasks;

import com.studyflow.model.Assignment;
import com.studyflow.model.Exam;
import com.studyflow.model.Priority;
import com.studyflow.model.StudySession;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/** Renders one selected task and exposes its status and priority controls. */
final class TaskDetailsPanel extends JPanel {
    private final JLabel title = TaskPresentation.literalLabel("Select a task");
    private final JTextArea details = new JTextArea();
    private final JComboBox<Priority> priority = new JComboBox<>(Priority.values());
    private final JComboBox<TaskStatus> status = new JComboBox<>(TaskStatus.values());
    private Task selected;
    private boolean updating;
    private boolean busy;

    TaskDetailsPanel(BiConsumer<String, Priority> changePriority, BiConsumer<String, TaskStatus> changeStatus) {
        super(new BorderLayout());
        setOpaque(false);
        JPanel card = ViewStyle.card(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 14, 16));
        add(card);
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        title.setName("taskDetailsTitle");
        card.add(title, BorderLayout.PAGE_START);

        details.setName("taskDetails");
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setOpaque(false);
        details.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 6));
        var scroll = ViewStyle.scroll(details);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        card.add(scroll, BorderLayout.CENTER);

        priority.setName("taskPriority");
        status.setName("taskStatus");
        TaskPresentation.useLabels(priority, TaskPresentation::label);
        TaskPresentation.useLabels(status, TaskPresentation::label);
        priority.addActionListener(event -> {
            if (selected != null && !updating) {
                Priority value = priority.getItemAt(priority.getSelectedIndex());
                resetChoices();
                if (value != selected.getPriority()) {
                    changePriority.accept(selected.getId(), value);
                }
            }
        });
        status.addActionListener(event -> {
            if (selected != null && !updating) {
                TaskStatus value = status.getItemAt(status.getSelectedIndex());
                resetChoices();
                if (value != selected.getStatus()) {
                    changeStatus.accept(selected.getId(), value);
                }
            }
        });
        JPanel controls = new JPanel(new GridLayout(1, 2, 8, 0));
        controls.setOpaque(false);
        controls.add(labeled("Priority", priority));
        controls.add(labeled("Status", status));
        card.add(controls, BorderLayout.PAGE_END);
        setMinimumSize(new Dimension(218, 130));
        setPreferredSize(new Dimension(252, 200));
        showTask(null, "");
    }

    void showTask(Task task, String subject) {
        selected = task;
        title.setText(task == null ? "Select a task" : task.getTitle());
        title.setToolTipText(task == null ? null : task.getTitle());
        details.setText(task == null ? "" : describe(task, subject));
        details.setCaretPosition(0);
        resetChoices();
        updateEnabled();
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        updateEnabled();
    }

    private void updateEnabled() {
        priority.setEnabled(selected != null && !busy);
        status.setEnabled(selected != null && !busy);
    }

    private void resetChoices() {
        updating = true;
        priority.setSelectedItem(selected == null ? null : selected.getPriority());
        status.setSelectedItem(selected == null ? null : selected.getStatus());
        updating = false;
    }

    private static JPanel labeled(String text, JComboBox<?> input) {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setOpaque(false);
        JLabel label = ViewStyle.muted(text);
        label.setLabelFor(input);
        panel.add(label, BorderLayout.PAGE_START);
        panel.add(input, BorderLayout.CENTER);
        return panel;
    }

    private static String describe(Task task, String subject) {
        StringBuilder details = new StringBuilder()
                .append("Type: ").append(TaskPresentation.label(task.getType()))
                .append("\nSubject: ").append(subject)
                .append("\nDeadline: ").append(task.getDueDate() == null ? "None" : task.getDueDate())
                .append("\nCreated: ").append(task.getCreatedDate());
        switch (task) {
            case Assignment assignment -> {
                details.append("\nSubmission: ").append(assignment.getSubmissionType());
                if (assignment.getAttachmentPath() != null) {
                    details.append("\nAttachment: ").append(assignment.getAttachmentPath());
                }
            }
            case Exam exam -> details.append("\nExam: ").append(exam.getExamType())
                    .append("\nPreparation: ").append(exam.getPreparationStatus());
            case StudySession session -> {
                details.append("\nPlanned duration: ").append(session.getPlannedDuration());
                if (session.getActualDuration() != null) {
                    details.append("\nActual duration: ").append(session.getActualDuration());
                }
            }
            default -> { }
        }
        if (!task.getDescription().isBlank()) {
            details.append("\n\n").append(task.getDescription());
        }
        return details.toString();
    }
}
