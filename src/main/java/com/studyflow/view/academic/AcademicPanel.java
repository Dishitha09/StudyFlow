package com.studyflow.view.academic;

import com.studyflow.interfaces.Identifiable;
import com.studyflow.model.Note;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.Workspace;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** List and editor for one kind of academic record. Mutations are delegated to the application. */
public final class AcademicPanel extends JPanel {
    public enum Kind {
        SUBJECTS("Subjects", "subject"),
        WORKSPACES("Workspaces", "workspace"),
        NOTES("Notes", "note");

        final String heading;
        final String singular;

        Kind(String heading, String singular) {
            this.heading = heading;
            this.singular = singular;
        }
    }

    public interface Actions {
        void createSubject(String name, String code, String description, List<String> tags);
        void updateSubject(String id, String name, String code, String description, List<String> tags);
        void deleteSubject(String id);
        void createWorkspace(String name, String description, List<String> subjectIds);
        void updateWorkspace(String id, String name, String description, List<String> subjectIds);
        void deleteWorkspace(String id);
        void createNote(String title, String content, String subjectId, List<String> tags);
        void updateNote(String id, String title, String content, String subjectId, List<String> tags);
        void deleteNote(String id);
        void openWorkspaceItem(LinkedRecord kind, String id);
    }

    public enum LinkedRecord { SUBJECT, TASK, NOTE }

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm");
    private static final String DETAIL = "detail";
    private static final String EDITOR = "editor";

    private final Kind kind;
    private final Actions actions;
    private final DefaultListModel<RecordRow> rows = new DefaultListModel<>();
    private final JList<RecordRow> list = new JList<>(rows);
    private final JLabel count = ViewStyle.muted("");
    private final JLabel detailTitle = ViewStyle.subheading("");
    private final JTextArea details = new JTextArea();
    private final JButton add;
    private final JButton edit = new JButton("Edit");
    private final JButton delete = new JButton("Delete");
    private final JButton openSubject = new JButton("Open subject");
    private final JTextField noteSearch = new JTextField();
    private final CardLayout detailCards = new CardLayout();
    private final JPanel detailArea = new JPanel(detailCards);
    private final JPanel editorArea = new JPanel(new BorderLayout());
    private final WorkspaceContentsPanel workspaceContents;
    private List<Subject> subjects = List.of();
    private List<Task> tasks = List.of();
    private List<Note> notes = List.of();
    private List<RecordRow> allRows = List.of();
    private AcademicFormPanel form;
    private boolean busy;

    public AcademicPanel(Kind kind, Actions actions) {
        super(new BorderLayout(0, 20));
        this.kind = Objects.requireNonNull(kind);
        this.actions = Objects.requireNonNull(actions);
        this.add = ViewStyle.primary(new JButton("Add " + kind.singular));
        workspaceContents = kind == Kind.WORKSPACES ? new WorkspaceContentsPanel(actions::openWorkspaceItem) : null;
        setName(kind.singular + "Page");
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(25, 25, 24, 25));
        add(createHeader(), BorderLayout.PAGE_START);
        add(createRecordArea(), BorderLayout.CENTER);
        updateDetails();
    }

    /** Updates detached records while preserving the selected ID and any pending form edits. */
    public void showRecords(List<Subject> subjects, List<Workspace> workspaces, List<Note> notes) {
        showRecords(subjects, workspaces, notes, List.of());
    }

    public void showRecords(List<Subject> subjects, List<Workspace> workspaces, List<Note> notes, List<Task> tasks) {
        this.subjects = List.copyOf(subjects);
        this.tasks = List.copyOf(tasks);
        this.notes = List.copyOf(notes);
        if (form != null) {
            form.showSubjects(subjects);
        }
        Map<String, String> names = subjects.stream().collect(Collectors.toMap(Subject::getId, Subject::getName));
        allRows = switch (kind) {
            case SUBJECTS -> subjects.stream().map(AcademicPanel::subjectRow).toList();
            case WORKSPACES -> workspaces.stream().map(workspace -> workspaceRow(workspace, names)).toList();
            case NOTES -> notes.stream().map(note -> noteRow(note, names)).toList();
        };
        refreshRows();
    }

    private void refreshRows() {
        String query = noteSearch.getText().strip();
        List<RecordRow> refreshed = kind == Kind.NOTES && !query.isEmpty()
                ? allRows.stream().filter(row -> ((Note) row.record()).matchesSearch(query)).toList()
                : allRows;
        RecordRow selected = list.getSelectedValue();
        rows.clear();
        rows.addAll(refreshed);
        if (selected != null) {
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).record().getId().equals(selected.record().getId())) {
                    list.setSelectedIndex(index);
                    break;
                }
            }
        }
        if (list.isSelectionEmpty() && !rows.isEmpty()) {
            list.setSelectedIndex(0);
        }
        String label = refreshed.size() == 1 ? kind.singular : kind.heading.toLowerCase(Locale.ROOT);
        count.setText(refreshed.size() + " " + label + (refreshed.size() < allRows.size() ? " found" : ""));
        updateDetails();
    }

    public void openCreate() {
        openEditor(null);
    }

    public void openEdit(String id) {
        RecordRow row = findRow(id);
        if (row != null && openEditor(row.record())) {
            selectRow(row);
        }
    }

    public void selectRecord(String id) {
        RecordRow row = findRow(id);
        if (row == null || form != null && !form.confirmDiscard()) {
            return;
        }
        if (form != null) {
            closeEditor();
        }
        selectRow(row);
    }

    private void selectRow(RecordRow row) {
        int index = rows.indexOf(row);
        if (kind == Kind.NOTES && index < 0) {
            noteSearch.setText("");
            index = rows.indexOf(row);
        }
        if (index >= 0) {
            list.setSelectedIndex(index);
            list.ensureIndexIsVisible(index);
        }
    }

    private RecordRow findRow(String id) {
        return allRows.stream().filter(row -> row.record().getId().equals(id)).findFirst().orElse(null);
    }

    public boolean hasUnsavedChanges() {
        return form != null && form.hasUnsavedChanges();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        if (form != null) {
            form.setBusy(busy);
        }
        updateControls();
    }

    public boolean failEdit(String message) {
        return form != null && form.failEdit(message);
    }

    /** Closes the editor after the parent confirms that a mutation was saved. */
    public void finishEdit() {
        if (form == null) {
            return;
        }
        String savedId = form.recordId();
        if (savedId == null && !allRows.isEmpty()) {
            savedId = allRows.getLast().record().getId();
        }
        closeEditor();
        if (savedId != null) {
            selectRecord(savedId);
        }
    }

    private void closeEditor() {
        form = null;
        editorArea.removeAll();
        detailCards.show(detailArea, DETAIL);
        editorArea.revalidate();
        updateControls();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        JPanel title = new JPanel(new BorderLayout(0, 4));
        title.setOpaque(false);
        title.add(ViewStyle.heading(kind.heading), BorderLayout.PAGE_START);
        JPanel caption = new JPanel(new BorderLayout(0, 3));
        caption.setOpaque(false);
        if (kind == Kind.NOTES) {
            caption.add(ViewStyle.muted("Keep plain-text study notes. Use Save note to store changes on this device."),
                    BorderLayout.PAGE_START);
        } else if (kind == Kind.WORKSPACES) {
            caption.add(ViewStyle.muted("Group subjects for a term or project and browse their linked tasks and notes."),
                    BorderLayout.PAGE_START);
        }
        caption.add(count, BorderLayout.PAGE_END);
        title.add(caption, BorderLayout.PAGE_END);
        header.add(title, BorderLayout.CENTER);

        JPanel actionsRow = new JPanel(new GridBagLayout());
        actionsRow.setOpaque(false);
        add.setName(kind.singular + "Add");
        add.addActionListener(event -> openCreate());
        actionsRow.add(add);
        header.add(actionsRow, BorderLayout.LINE_END);
        return header;
    }

    private JPanel createRecordArea() {
        list.setName(kind.singular + "List");
        list.setOpaque(false);
        list.setCellRenderer(this::renderRow);
        list.setFixedCellHeight(58);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getAccessibleContext().setAccessibleName(kind.heading);
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateDetails();
            }
        });

        JPanel listing = ViewStyle.card(new BorderLayout(0, 8));
        listing.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        var listScroll = ViewStyle.scroll(list);
        listScroll.setOpaque(false);
        listScroll.getViewport().setOpaque(false);
        listScroll.setPreferredSize(new Dimension(255, 220));
        if (kind == Kind.NOTES) {
            configureNoteSearch();
            listing.add(noteSearch, BorderLayout.PAGE_START);
        }
        listing.add(listScroll, BorderLayout.CENTER);

        detailArea.setOpaque(false);
        detailArea.add(createDetails(), DETAIL);
        editorArea.setOpaque(false);
        detailArea.add(editorArea, EDITOR);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints cell = new GridBagConstraints();
        cell.fill = GridBagConstraints.BOTH;
        cell.weighty = 1;
        cell.weightx = 0.36;
        cell.gridx = 0;
        cell.insets = new Insets(0, 0, 0, 14);
        body.add(listing, cell);
        cell.gridx = 1;
        cell.weightx = 0.64;
        cell.insets = new Insets(0, 0, 0, 0);
        body.add(detailArea, cell);
        return body;
    }

    private JPanel createDetails() {
        JPanel card = ViewStyle.card(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(17, 18, 14, 18));
        card.add(detailTitle, BorderLayout.PAGE_START);

        details.setName(kind.singular + "Details");
        details.getAccessibleContext().setAccessibleName(kind.heading + " details");
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setOpaque(false);
        details.setFont(detailTitle.getFont().deriveFont(Font.PLAIN, 14f));
        details.setBorder(BorderFactory.createEmptyBorder(3, 1, 3, 1));
        var detailScroll = ViewStyle.scroll(details);
        detailScroll.setOpaque(false);
        detailScroll.getViewport().setOpaque(false);
        if (workspaceContents == null) {
            card.add(detailScroll, BorderLayout.CENTER);
        } else {
            detailScroll.setPreferredSize(new Dimension(0, 96));
            JPanel workspaceDetails = new JPanel(new BorderLayout(0, 8));
            workspaceDetails.setOpaque(false);
            workspaceDetails.add(detailScroll, BorderLayout.PAGE_START);
            workspaceDetails.add(workspaceContents, BorderLayout.CENTER);
            card.add(workspaceDetails, BorderLayout.CENTER);
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        buttons.setOpaque(false);
        edit.setName(kind.singular + "Edit");
        delete.setName(kind.singular + "Delete");
        edit.addActionListener(event -> {
            RecordRow selected = list.getSelectedValue();
            if (selected != null) {
                openEditor(selected.record());
            }
        });
        delete.addActionListener(event -> deleteSelected());
        buttons.add(edit);
        buttons.add(delete);
        if (kind == Kind.NOTES) {
            openSubject.setName("noteOpenSubject");
            openSubject.addActionListener(event -> {
                Note selected = selectedNote();
                if (selected != null && selected.getSubjectId() != null) {
                    actions.openWorkspaceItem(LinkedRecord.SUBJECT, selected.getSubjectId());
                }
            });
            buttons.add(openSubject);
        }
        card.add(buttons, BorderLayout.PAGE_END);
        return card;
    }

    private Component renderRow(JList<? extends RecordRow> owner, RecordRow row, int index, boolean selected,
                                boolean focused) {
        JPanel cell = new JPanel(new BorderLayout(0, 3));
        cell.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        cell.setBackground(selected ? owner.getSelectionBackground() : ViewStyle.color("surface"));
        JLabel title = new JLabel(row.title());
        title.putClientProperty("html.disable", true);
        title.setText(row.title());
        title.setFont(owner.getFont().deriveFont(Font.BOLD));
        JLabel subtitle = new JLabel();
        subtitle.putClientProperty("html.disable", true);
        subtitle.setText(row.subtitle());
        subtitle.setForeground(ViewStyle.color("muted"));
        if (selected) {
            Color foreground = owner.getSelectionForeground();
            title.setForeground(foreground);
            subtitle.setForeground(foreground);
        }
        cell.add(title, BorderLayout.PAGE_START);
        cell.add(subtitle, BorderLayout.PAGE_END);
        return cell;
    }

    private boolean openEditor(Identifiable record) {
        if (busy) {
            return false;
        }
        if (form != null && !form.confirmDiscard()) {
            return false;
        }
        form = new AcademicFormPanel(kind, subjects, record, actions, this::closeEditor);
        editorArea.removeAll();
        editorArea.add(form, BorderLayout.CENTER);
        detailCards.show(detailArea, EDITOR);
        editorArea.revalidate();
        form.focusTitle();
        updateControls();
        return true;
    }

    private void configureNoteSearch() {
        noteSearch.setName("noteSearch");
        noteSearch.putClientProperty("JTextField.placeholderText", "Search notes");
        noteSearch.putClientProperty("JTextField.showClearButton", true);
        noteSearch.getAccessibleContext().setAccessibleName("Search note titles, content and tags");
        noteSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refreshRows(); }
            @Override public void removeUpdate(DocumentEvent event) { refreshRows(); }
            @Override public void changedUpdate(DocumentEvent event) { refreshRows(); }
        });
    }

    private void updateDetails() {
        RecordRow selected = list.getSelectedValue();
        detailTitle.putClientProperty("html.disable", true);
        if (selected == null) {
            boolean filtered = kind == Kind.NOTES && !noteSearch.getText().isBlank();
            detailTitle.setText(filtered ? "No matching notes" : "No " + kind.heading.toLowerCase(Locale.ROOT) + " yet");
            details.setText(filtered ? "Try a different title, topic or tag." : emptyDetails());
        } else {
            detailTitle.setText(selected.title());
            details.setText(selected.details());
        }
        if (workspaceContents != null) {
            workspaceContents.show(selected == null ? null : (Workspace) selected.record(), subjects, tasks, notes);
        }
        details.setCaretPosition(0);
        updateControls();
    }

    private void updateControls() {
        boolean selected = list.getSelectedValue() != null;
        add.setEnabled(!busy);
        edit.setEnabled(!busy && selected);
        delete.setEnabled(!busy && selected);
        Note note = selectedNote();
        openSubject.setEnabled(!busy && note != null && note.getSubjectId() != null);
        if (workspaceContents != null) {
            workspaceContents.setBusy(busy);
        }
    }

    private String emptyDetails() {
        return switch (kind) {
            case SUBJECTS -> "Use Add subject to create your first subject.";
            case WORKSPACES -> "Choose Add workspace, give it a name and select the subjects to include.";
            case NOTES -> "Choose Add note to write a note. You can link it to a subject or leave it unassigned.";
        };
    }

    private Note selectedNote() {
        RecordRow selected = list.getSelectedValue();
        return selected != null && selected.record() instanceof Note note ? note : null;
    }

    private void deleteSelected() {
        RecordRow selected = list.getSelectedValue();
        if (selected == null || busy) {
            return;
        }
        String scope = kind == Kind.WORKSPACES ? "\n\nIts subjects, tasks and notes will remain in StudyFlow." : "";
        JTextArea message = new JTextArea("Delete “" + selected.title() + "”?" + scope, 4, 32);
        message.setEditable(false);
        message.setOpaque(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        if (JOptionPane.showConfirmDialog(this, message, "Delete " + kind.singular,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        String id = selected.record().getId();
        switch (kind) {
            case SUBJECTS -> actions.deleteSubject(id);
            case WORKSPACES -> actions.deleteWorkspace(id);
            case NOTES -> actions.deleteNote(id);
        }
    }

    private static RecordRow subjectRow(Subject subject) {
        String details = "Subject code: " + subject.getSubjectCode()
                + optionalSection("Tags: ", String.join(", ", subject.getTags()))
                + optionalSection("", subject.getDescription());
        return new RecordRow(subject, subject.getName(), subject.getSubjectCode(), details);
    }

    private static RecordRow workspaceRow(Workspace workspace, Map<String, String> subjects) {
        List<String> members = workspace.getSubjectIds().stream().map(subjects::get).filter(Objects::nonNull).toList();
        String subtitle = members.size() + (members.size() == 1 ? " subject" : " subjects");
        String details = "Subjects: " + (members.isEmpty() ? "None" : String.join(", ", members))
                + "\nCreated: " + DATE_TIME.format(workspace.getCreatedAt())
                + optionalSection("", workspace.getDescription());
        return new RecordRow(workspace, workspace.getName(), subtitle, details);
    }

    private static RecordRow noteRow(Note note, Map<String, String> subjects) {
        String subject = note.getSubjectId() == null ? "No subject" : subjects.getOrDefault(note.getSubjectId(), "No subject");
        String details = "Subject: " + subject + "\nUpdated: " + DATE_TIME.format(note.getUpdatedAt())
                + optionalSection("Tags: ", String.join(", ", note.getTags()))
                + optionalSection("", note.getContent());
        return new RecordRow(note, note.getTitle(), subject, details);
    }

    private static String optionalSection(String prefix, String value) {
        return value.isBlank() ? "" : "\n\n" + prefix + value;
    }

    private record RecordRow(Identifiable record, String title, String subtitle, String details) {
        @Override
        public String toString() {
            return title;
        }
    }
}
