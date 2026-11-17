package com.studyflow.view.tasks;

import com.studyflow.factory.TaskDraft;
import com.studyflow.model.Assignment;
import com.studyflow.model.Exam;
import com.studyflow.model.Priority;
import com.studyflow.model.StudySession;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskType;
import com.studyflow.view.FormDialogs;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.text.JTextComponent;

/** Task editor that keeps user input available until the controller confirms a successful save. */
final class TaskFormDialog extends JDialog {
    private final JTextField title = field("taskFormTitle");
    private final JComboBox<TaskType> type = combo("taskFormType",
            new TaskType[]{TaskType.GENERAL, TaskType.ASSIGNMENT, TaskType.EXAM, TaskType.STUDY_SESSION});
    private final JComboBox<Choice<String>> subject = new JComboBox<>();
    private final JComboBox<Choice<Priority>> priority = new JComboBox<>();
    private final JTextField deadline = field("taskFormDeadline");
    private final JTextArea description = new JTextArea(3, 24);
    private final JTextField submission = field("taskFormSubmission");
    private final JTextField attachment = field("taskFormAttachment");
    private final JTextField examType = field("taskFormExamType");
    private final JTextField preparation = field("taskFormPreparation");
    private final JTextField planned = field("taskFormPlanned");
    private final JTextField actual = field("taskFormActual");
    private final JButton cancel = new JButton("Cancel");
    private final JButton save = ViewStyle.primary(new JButton("Save task"));
    private final JLabel error = TaskPresentation.literalLabel(" ");
    private final boolean editing;
    private final Consumer<TaskDraft> submit;
    private final DraftState initial;
    private boolean pending;

    TaskFormDialog(Window owner, Task task, List<Subject> subjects, Consumer<TaskDraft> submit) {
        super(owner, task == null ? "Add task" : "Edit task", Dialog.ModalityType.DOCUMENT_MODAL);
        editing = task != null;
        this.submit = submit;
        setName("taskFormDialog");
        configureInputs(task, subjects);
        initial = draftState();
        JPanel body = new JPanel(new BorderLayout(0, 18));
        body.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
        body.add(ViewStyle.heading(getTitle()), BorderLayout.PAGE_START);
        body.add(new JScrollPane(form()), BorderLayout.CENTER);
        body.add(footer(), BorderLayout.PAGE_END);
        setContentPane(body);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(440, 400));
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        int workHeight = getGraphicsConfiguration().getBounds().height - screenInsets.top - screenInsets.bottom;
        setSize(580, Math.max(400, Math.min(608, workHeight - 32)));
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(save);
        FormDialogs.bindEscape(getRootPane(), JComponent.WHEN_IN_FOCUSED_WINDOW, this::cancel);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                title.requestFocusInWindow();
                if (editing) {
                    title.selectAll();
                }
            }

            @Override
            public void windowClosing(WindowEvent event) {
                cancel();
            }
        });
    }

    private void configureInputs(Task task, List<Subject> subjects) {
        subject.setName("taskFormSubject");
        subject.addItem(new Choice<>("No subject", null));
        for (Subject item : subjects) {
            subject.addItem(new Choice<>(TaskPresentation.subject(item), item.getId()));
            if (task != null && item.getId().equals(task.getSubjectId())) {
                subject.setSelectedIndex(subject.getItemCount() - 1);
            }
        }
        priority.setName("taskFormPriority");
        if (!editing) {
            priority.addItem(new Choice<>("Default priority", null));
        }
        for (Priority value : Priority.values()) {
            priority.addItem(new Choice<>(TaskPresentation.label(value), value));
            if (task != null && task.getPriority() == value) {
                priority.setSelectedIndex(priority.getItemCount() - 1);
            }
        }
        TaskPresentation.useLabels(subject, Choice::label);
        TaskPresentation.useLabels(priority, Choice::label);
        TaskPresentation.useLabels(type, TaskPresentation::label);
        description.setName("taskFormDescription");
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        deadline.putClientProperty("JTextField.placeholderText", "YYYY-MM-DD");
        preparation.putClientProperty("JTextField.placeholderText", Exam.DEFAULT_PREPARATION_STATUS);
        planned.putClientProperty("JTextField.placeholderText", "e.g. 25 or PT25M");
        actual.putClientProperty("JTextField.placeholderText", "e.g. 30 or PT30M");

        if (task == null) {
            return;
        }
        title.setText(task.getTitle());
        description.setText(task.getDescription());
        type.setSelectedItem(task.getType());
        type.setEnabled(false);
        if (task.getDueDate() != null) {
            deadline.setText(task.getDueDate().toString());
        }
        switch (task) {
            case Assignment assignment -> {
                submission.setText(assignment.getSubmissionType());
                attachment.setText(assignment.getAttachmentPath());
            }
            case Exam exam -> {
                examType.setText(exam.getExamType());
                preparation.setText(exam.getPreparationStatus());
            }
            case StudySession session -> {
                planned.setText(session.getPlannedDuration().toString());
                if (session.getActualDuration() != null) {
                    actual.setText(session.getActualDuration().toString());
                }
            }
            default -> { }
        }
    }

    private JPanel form() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 10));
        addRow(form, 0, "Title", title);
        addRow(form, 1, "Task type", type);
        addRow(form, 2, "Subject", subject);
        addRow(form, 3, "Priority", priority);
        addRow(form, 4, "Deadline (optional)", deadline);
        JScrollPane descriptionScroll = new JScrollPane(description);
        addRow(form, 5, "Description", descriptionScroll, description);

        CardLayout fieldsLayout = new CardLayout();
        JPanel fields = new JPanel(fieldsLayout);
        fields.add(new JPanel(), TaskType.GENERAL.name());
        fields.add(extraFields("Submission method", submission, "Attachment path (optional)", attachment),
                TaskType.ASSIGNMENT.name());
        fields.add(extraFields("Exam type", examType, "Preparation status", preparation), TaskType.EXAM.name());
        fields.add(extraFields("Planned minutes", planned, "Actual minutes (optional)", actual),
                TaskType.STUDY_SESSION.name());
        type.addActionListener(event -> fieldsLayout.show(fields, selectedType().name()));
        fieldsLayout.show(fields, selectedType().name());
        GridBagConstraints fieldCell = cell(0, 6);
        fieldCell.gridwidth = 2;
        fieldCell.weightx = 1;
        fieldCell.fill = GridBagConstraints.HORIZONTAL;
        form.add(fields, fieldCell);

        GridBagConstraints filler = cell(0, 7);
        filler.weighty = 1;
        form.add(new JPanel(), filler);
        return form;
    }

    private JPanel footer() {
        JPanel footer = new JPanel(new GridLayout(0, 1, 0, 5));
        error.setName("taskFormError");
        error.setForeground(ViewStyle.color("danger"));
        footer.add(error);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        cancel.setName("taskFormCancel");
        cancel.addActionListener(event -> cancel());
        save.setName("taskFormSave");
        save.addActionListener(event -> save());
        actions.add(cancel);
        actions.add(save);
        footer.add(actions);
        return footer;
    }

    private void save() {
        if (pending) {
            return;
        }
        try {
            TaskDraft draft = new TaskDraft(required(title, "Title"), description.getText(),
                    selectedValue(subject), TaskPresentation.optionalDate(deadline.getText(), "Deadline"),
                    selectedValue(priority), taskDetails());
            setPending(true);
            FormDialogs.feedback(error, "Saving task…");
            submit.accept(draft);
        } catch (IllegalArgumentException failure) {
            showFailure(failure.getMessage());
        }
    }

    private TaskDraft.Details taskDetails() {
        return switch (selectedType()) {
            case GENERAL -> new TaskDraft.General();
            case ASSIGNMENT -> new TaskDraft.AssignmentDetails(required(submission, "Submission method"),
                    attachment.getText());
            case EXAM -> new TaskDraft.ExamDetails(required(examType, "Exam type"),
                    preparation.getText().isBlank() ? Exam.DEFAULT_PREPARATION_STATUS : preparation.getText());
            case STUDY_SESSION -> new TaskDraft.StudySessionDetails(duration(planned, "Planned minutes", false),
                    duration(actual, "Actual minutes", true));
        };
    }

    private TaskType selectedType() {
        return type.getItemAt(type.getSelectedIndex());
    }

    private void setPending(boolean value) {
        pending = value;
        for (JComponent field : new JComponent[]{title, description, subject, priority, deadline, submission,
                attachment, examType, preparation, planned, actual, cancel, save}) {
            field.setEnabled(!value);
        }
        type.setEnabled(!editing && !value);
    }

    boolean isPending() {
        return pending;
    }

    boolean hasUnsavedChanges() {
        return !initial.equals(draftState());
    }

    private DraftState draftState() {
        List<String> text = List.of(title, deadline, description, submission, attachment, examType, preparation,
                planned, actual).stream().map(JTextComponent::getText).toList();
        return new DraftState(text, selectedType(), selectedValue(subject), selectedValue(priority));
    }

    private void cancel() {
        if (!pending && (!hasUnsavedChanges() || FormDialogs.confirmDiscard(this))) {
            dispose();
        }
    }

    void showFailure(String message) {
        setPending(false);
        FormDialogs.feedback(error, message);
    }

    private static <T> T selectedValue(JComboBox<Choice<T>> combo) {
        return combo.getItemAt(combo.getSelectedIndex()).value();
    }

    private static String required(JTextField field, String name) {
        if (field.getText().isBlank()) {
            field.requestFocusInWindow();
            throw new IllegalArgumentException(name + " is required.");
        }
        return field.getText().strip();
    }

    private static Duration duration(JTextField field, String name, boolean optional) {
        if (optional && field.getText().isBlank()) {
            return null;
        }
        String input = required(field, name);
        Duration value;
        try {
            value = input.startsWith("P") ? Duration.parse(input) : Duration.ofMinutes(Long.parseLong(input));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(name + " must be whole minutes or an ISO duration such as PT25M.",
                    invalid);
        }
        if (value.isNegative() || !optional && value.isZero()) {
            throw new IllegalArgumentException(name + (optional ? " cannot be negative." : " must be positive."));
        }
        return value;
    }

    private static JPanel extraFields(String firstLabel, JTextField first, String secondLabel, JTextField second) {
        JPanel panel = new JPanel(new GridBagLayout());
        addRow(panel, 0, firstLabel, first);
        addRow(panel, 1, secondLabel, second);
        return panel;
    }

    private static void addRow(JPanel panel, int row, String label, JComponent component) {
        addRow(panel, row, label, component, component);
    }

    private static void addRow(JPanel panel, int row, String label, Component component, JComponent input) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setLabelFor(input);
        panel.add(fieldLabel, cell(0, row));
        GridBagConstraints inputCell = cell(1, row);
        inputCell.fill = GridBagConstraints.HORIZONTAL;
        inputCell.weightx = 1;
        panel.add(component, inputCell);
    }

    private static GridBagConstraints cell(int column, int row) {
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = column;
        cell.gridy = row;
        cell.anchor = GridBagConstraints.LINE_START;
        cell.insets = new Insets(4, 4, 4, 8);
        return cell;
    }

    private static JTextField field(String name) {
        JTextField field = new JTextField(22);
        field.setName(name);
        return field;
    }

    private static <T> JComboBox<T> combo(String name, T[] values) {
        JComboBox<T> combo = new JComboBox<>(values);
        combo.setName(name);
        return combo;
    }

    private record Choice<T>(String label, T value) {
    }

    private record DraftState(List<String> text, TaskType type, String subjectId, Priority priority) {
    }
}
