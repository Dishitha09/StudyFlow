package com.studyflow.view;

import com.studyflow.controller.StudyFlowController;
import com.studyflow.model.Note;
import com.studyflow.model.Priority;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.Workspace;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/** Minimal academic record forms and lists for the local persistence workflow. */
public final class VerificationPanel extends JPanel {
    private static final SubjectChoice NO_SUBJECT = new SubjectChoice(null, "None");
    private static final String DEFAULT_PRIORITY = "Default priority";

    private final JLabel status = named(new JLabel("Loading records…"), "workspaceStatus");
    private final JTextField taskTitle = field("taskTitle");
    private final JComboBox<SubjectChoice> taskSubject = subjectPicker("taskSubject");
    private final JTextField taskDueDate = field("taskDueDate");
    private final JComboBox<Object> taskPriority = named(new JComboBox<>(new Object[]{
            DEFAULT_PRIORITY, Priority.HIGH, Priority.MEDIUM, Priority.LOW}), "taskPriority");
    private final JTextField subjectName = field("subjectName");
    private final JTextField subjectCode = field("subjectCode");
    private final JTextField workspaceName = field("workspaceName");
    private final JComboBox<SubjectChoice> workspaceSubject = subjectPicker("workspaceSubject");
    private final JTextField noteTitle = field("noteTitle");
    private final JTextArea noteContent = named(textArea(4), "noteContent");
    private final JComboBox<SubjectChoice> noteSubject = subjectPicker("noteSubject");

    private final RecordTab taskTab = new RecordTab("task", "Add task");
    private final RecordTab subjectTab = new RecordTab("subject", "Add subject");
    private final RecordTab workspaceTab = new RecordTab("workspace", "Add workspace");
    private final RecordTab noteTab = new RecordTab("note", "Add note");
    private final List<RecordTab> recordTabs = List.of(taskTab, subjectTab, workspaceTab, noteTab);

    private final StudyFlowController controller;
    private boolean busy;
    private boolean closing;

    public VerificationPanel(StudyFlowController controller) {
        super(new BorderLayout(0, 12));
        this.controller = controller;
        setBorder(BorderFactory.createEmptyBorder(20, 20, 14, 20));

        JTabbedPane tabs = named(new JTabbedPane(), "workspaceTabs");
        tabs.addTab("Tasks", configureTasks());
        tabs.addTab("Subjects", configureSubjects());
        tabs.addTab("Workspaces", configureWorkspaces());
        tabs.addTab("Notes", configureNotes());
        add(tabs, BorderLayout.CENTER);
        add(status, BorderLayout.PAGE_END);

        setBusy(true);
        controller.load(records -> showRecords(records, "Ready"), this::showError);
    }

    /** Stops new commands while the application drains accepted work. */
    public void setClosing() {
        closing = true;
        status.setText("Closing…");
        setBusy(true);
    }

    private RecordTab configureTasks() {
        taskTab.addField("Title", taskTitle);
        taskTab.addField("Subject", taskSubject);
        taskTab.addField("Deadline (YYYY-MM-DD)", taskDueDate);
        taskTab.addField("Priority", taskPriority);
        taskTab.onSubmit(event -> {
            String title = taskTitle.getText();
            String subjectId = selectedSubject(taskSubject);
            String deadline = taskDueDate.getText();
            Object choice = taskPriority.getSelectedItem();
            Priority priority = choice instanceof Priority selected ? selected : null;
            save((success, failure) -> controller.createTask(title, subjectId, deadline, priority, success, failure),
                    () -> {
                        taskTitle.setText("");
                        taskDueDate.setText("");
                        taskSubject.setSelectedIndex(0);
                        taskPriority.setSelectedIndex(0);
                    });
        });
        return taskTab;
    }

    private RecordTab configureSubjects() {
        subjectTab.addField("Name", subjectName);
        subjectTab.addField("Subject code", subjectCode);
        subjectTab.onSubmit(event -> {
            String name = subjectName.getText();
            String code = subjectCode.getText();
            save((success, failure) -> controller.createSubject(name, code, success, failure), () -> {
                subjectName.setText("");
                subjectCode.setText("");
            });
        });
        return subjectTab;
    }

    private RecordTab configureWorkspaces() {
        workspaceTab.addField("Name", workspaceName);
        workspaceTab.addField("Subject", workspaceSubject);
        workspaceTab.onSubmit(event -> {
            String name = workspaceName.getText();
            String subjectId = selectedSubject(workspaceSubject);
            save((success, failure) -> controller.createWorkspace(name, subjectId, success, failure), () -> {
                workspaceName.setText("");
                workspaceSubject.setSelectedIndex(0);
            });
        });
        return workspaceTab;
    }

    private RecordTab configureNotes() {
        noteTab.addField("Title", noteTitle);
        noteTab.addField("Subject", noteSubject);
        noteTab.addField("Content", new JScrollPane(noteContent), noteContent);
        noteTab.onSubmit(event -> {
            String title = noteTitle.getText();
            String content = noteContent.getText();
            String subjectId = selectedSubject(noteSubject);
            save((success, failure) -> controller.createNote(title, content, subjectId, success, failure), () -> {
                noteTitle.setText("");
                noteContent.setText("");
                noteSubject.setSelectedIndex(0);
            });
        });
        return noteTab;
    }

    private void save(SaveCommand command, Runnable reset) {
        if (busy || closing) {
            return;
        }
        status.setText("Saving…");
        setBusy(true);
        command.execute(records -> {
            if (!closing) {
                reset.run();
                showRecords(records, "Saved");
            }
        }, this::showError);
    }

    private void showRecords(StudyFlowController.Records records, String message) {
        if (closing) {
            return;
        }
        Map<String, String> names = records.subjects().stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName));
        updateChoices(taskSubject, records.subjects());
        updateChoices(workspaceSubject, records.subjects());
        updateChoices(noteSubject, records.subjects());

        taskTab.setRows(records.tasks().stream().map(task -> taskRow(task, names)).toList());
        subjectTab.setRows(records.subjects().stream().map(VerificationPanel::subjectRow).toList());
        workspaceTab.setRows(records.workspaces().stream().map(workspace -> workspaceRow(workspace, names)).toList());
        noteTab.setRows(records.notes().stream().map(note -> noteRow(note, names)).toList());
        status.setText(message);
        setBusy(false);
    }

    private void showError(String message) {
        if (!closing) {
            status.setText(message);
            setBusy(false);
        }
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        recordTabs.forEach(tab -> tab.setFormEnabled(!busy));
    }

    private static RecordRow taskRow(Task task, Map<String, String> names) {
        String details = "Status: " + task.getStatus() + "\nPriority: " + task.getPriority()
                + "\nSubject: " + subjectName(names, task.getSubjectId())
                + "\nDeadline: " + (task.getDueDate() == null ? "None" : task.getDueDate())
                + detailsSuffix(task.getDescription());
        return new RecordRow(task.getId(), task.getTitle(), details);
    }

    private static RecordRow subjectRow(Subject subject) {
        return new RecordRow(subject.getId(), subject.getName(),
                "Subject code: " + subject.getSubjectCode() + detailsSuffix(subject.getDescription()));
    }

    private static RecordRow workspaceRow(Workspace workspace, Map<String, String> names) {
        String members = workspace.getSubjectIds().isEmpty() ? "None" : workspace.getSubjectIds().stream()
                .map(id -> subjectName(names, id)).collect(Collectors.joining(", "));
        return new RecordRow(workspace.getId(), workspace.getName(),
                "Subjects: " + members + detailsSuffix(workspace.getDescription()));
    }

    private static RecordRow noteRow(Note note, Map<String, String> names) {
        return new RecordRow(note.getId(), note.getTitle(),
                "Subject: " + subjectName(names, note.getSubjectId()) + detailsSuffix(note.getContent()));
    }

    private static String detailsSuffix(String text) {
        return text.isBlank() ? "" : "\n\n" + text;
    }

    private static String subjectName(Map<String, String> names, String id) {
        return id == null ? "None" : names.getOrDefault(id, id);
    }

    private static void updateChoices(JComboBox<SubjectChoice> picker, List<Subject> subjects) {
        String previousId = selectedSubject(picker);
        picker.removeAllItems();
        picker.addItem(NO_SUBJECT);
        for (Subject subject : subjects) {
            SubjectChoice choice = new SubjectChoice(subject.getId(),
                    subject.getName() + " (" + subject.getSubjectCode() + ")");
            picker.addItem(choice);
            if (choice.id().equals(previousId)) {
                picker.setSelectedItem(choice);
            }
        }
    }

    private static String selectedSubject(JComboBox<SubjectChoice> picker) {
        Object choice = picker.getSelectedItem();
        return choice instanceof SubjectChoice subject ? subject.id() : null;
    }

    private static JTextField field(String name) {
        return named(new JTextField(18), name);
    }

    private static JTextArea textArea(int rows) {
        JTextArea area = new JTextArea(rows, 18);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static JComboBox<SubjectChoice> subjectPicker(String name) {
        JComboBox<SubjectChoice> picker = named(new JComboBox<>(new SubjectChoice[]{NO_SUBJECT}), name);
        picker.setRenderer(plainTextRenderer());
        return picker;
    }

    private static DefaultListCellRenderer plainTextRenderer() {
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        return renderer;
    }

    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name);
        return component;
    }

    @FunctionalInterface
    private interface SaveCommand {
        void execute(Consumer<StudyFlowController.Records> success, Consumer<String> failure);
    }

    private record SubjectChoice(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record RecordRow(String id, String title, String details) {
        @Override
        public String toString() {
            return title;
        }
    }

    private static final class RecordTab extends JPanel {
        private final JPanel form = new JPanel(new GridBagLayout());
        private final List<JComponent> inputs = new ArrayList<>();
        private final JButton submit;
        private final JList<RecordRow> list;
        private final JTextArea details = textArea(4);
        private int nextRow;

        private RecordTab(String name, String action) {
            super(new BorderLayout(18, 0));
            setBorder(BorderFactory.createEmptyBorder(16, 14, 14, 14));
            submit = named(new JButton(action), name + "Create");
            list = named(new JList<>(), name + "List");
            list.setCellRenderer(plainTextRenderer());
            list.getAccessibleContext().setAccessibleName("Saved records");
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting()) {
                    RecordRow selected = list.getSelectedValue();
                    details.setText(selected == null ? "" : selected.details());
                    details.setCaretPosition(0);
                }
            });
            details.setName(name + "Details");
            details.setEditable(false);
            details.getAccessibleContext().setAccessibleName("Selected record details");

            JPanel formArea = new JPanel(new BorderLayout());
            formArea.setPreferredSize(new Dimension(245, 0));
            formArea.add(form, BorderLayout.PAGE_START);
            add(formArea, BorderLayout.LINE_START);

            JPanel records = new JPanel(new BorderLayout(0, 8));
            JLabel recordsLabel = new JLabel("Saved records");
            recordsLabel.setLabelFor(list);
            records.add(recordsLabel, BorderLayout.PAGE_START);
            records.add(new JScrollPane(list), BorderLayout.CENTER);
            records.add(new JScrollPane(details), BorderLayout.PAGE_END);
            add(records, BorderLayout.CENTER);
        }

        private void addField(String text, JComponent field) {
            addField(text, field, field);
        }

        private void addField(String text, JComponent field, JComponent input) {
            JLabel label = new JLabel(text);
            label.setLabelFor(input);
            addRow(label, 0);
            addRow(field, 8);
            inputs.add(input);
        }

        private void onSubmit(ActionListener listener) {
            addRow(submit, 0);
            submit.addActionListener(listener);
        }

        private void addRow(Component component, int gap) {
            GridBagConstraints cell = new GridBagConstraints();
            cell.gridx = 0;
            cell.gridy = nextRow++;
            cell.weightx = 1;
            cell.fill = GridBagConstraints.HORIZONTAL;
            cell.insets = new Insets(0, 0, gap, 0);
            form.add(component, cell);
        }

        private void setRows(List<RecordRow> rows) {
            RecordRow previous = list.getSelectedValue();
            list.setListData(rows.toArray(RecordRow[]::new));
            if (previous != null) {
                for (int index = 0; index < rows.size(); index++) {
                    if (rows.get(index).id().equals(previous.id())) {
                        list.setSelectedIndex(index);
                        break;
                    }
                }
            }
        }

        private void setFormEnabled(boolean enabled) {
            inputs.forEach(input -> input.setEnabled(enabled));
            submit.setEnabled(enabled);
        }
    }
}
