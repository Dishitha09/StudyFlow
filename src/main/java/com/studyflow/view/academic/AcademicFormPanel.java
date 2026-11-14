package com.studyflow.view.academic;

import com.studyflow.interfaces.Identifiable;
import com.studyflow.model.Note;
import com.studyflow.model.Subject;
import com.studyflow.model.Workspace;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Converts academic form input into commands while keeping pending edits available for corrections. */
final class AcademicFormPanel extends JPanel {
    private final AcademicPanel.Kind kind;
    private final AcademicPanel.Actions actions;
    private final String id;
    private final JTextField title = new JTextField();
    private final JTextField code = new JTextField();
    private final JTextArea body = new JTextArea(6, 20);
    private final JTextArea tags = new JTextArea(3, 20);
    private final JComboBox<SubjectChoice> subject = new JComboBox<>();
    private final JPanel membershipChoices = new JPanel();
    private final List<Membership> memberships = new ArrayList<>();
    private final JLabel validation = new JLabel() {
        @Override
        public Color getForeground() {
            return ViewStyle.color("danger");
        }
    };
    private final JButton save = ViewStyle.primary(new JButton("Save"));
    private final JButton cancel = new JButton("Cancel");
    private final JPanel fields = new JPanel(new GridBagLayout());
    private final List<String> initialTags;
    private final List<String> initialMembers;
    private final String initialTitle;
    private final String initialCode;
    private final String initialBody;
    private final String initialSubject;
    private List<SubjectChoice> availableSubjects;
    private boolean busy;
    private int nextRow;

    AcademicFormPanel(AcademicPanel.Kind kind, List<Subject> subjects, Identifiable record,
                      AcademicPanel.Actions actions, Runnable close) {
        super(new BorderLayout());
        this.kind = kind;
        this.actions = actions;
        id = record == null ? null : record.getId();
        initialTitle = switch (record) {
            case Subject value -> value.getName();
            case Workspace value -> value.getName();
            case Note value -> value.getTitle();
            case null -> "";
            default -> throw new IllegalArgumentException("Unsupported academic record.");
        };
        initialCode = record instanceof Subject value ? value.getSubjectCode() : "";
        initialBody = switch (record) {
            case Subject value -> value.getDescription();
            case Workspace value -> value.getDescription();
            case Note value -> value.getContent();
            case null -> "";
            default -> throw new IllegalArgumentException("Unsupported academic record.");
        };
        initialTags = switch (record) {
            case Subject value -> value.getTags();
            case Note value -> value.getTags();
            case null, default -> List.of();
        };
        initialMembers = record instanceof Workspace value ? value.getSubjectIds() : List.of();
        initialSubject = record instanceof Note value ? value.getSubjectId() : null;

        setOpaque(false);
        JPanel card = ViewStyle.card(new BorderLayout(0, 13));
        card.setBorder(BorderFactory.createEmptyBorder(17, 18, 14, 18));
        card.add(ViewStyle.subheading((id == null ? "Add " : "Edit ") + kind.singular), BorderLayout.PAGE_START);
        configureFields();
        showSubjects(subjects);
        JPanel topAligned = new JPanel(new BorderLayout());
        topAligned.setOpaque(false);
        topAligned.add(fields, BorderLayout.PAGE_START);
        JScrollPane fieldScroll = ViewStyle.scroll(topAligned);
        fieldScroll.setOpaque(false);
        fieldScroll.getViewport().setOpaque(false);
        fieldScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        fieldScroll.setPreferredSize(new Dimension(310, 285));
        card.add(fieldScroll, BorderLayout.CENTER);
        card.add(createFooter(close), BorderLayout.PAGE_END);
        add(card, BorderLayout.CENTER);
    }

    void focusTitle() {
        SwingUtilities.invokeLater(() -> {
            title.requestFocusInWindow();
            if (id != null) {
                title.selectAll();
            }
        });
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        title.setEnabled(!busy);
        code.setEnabled(!busy);
        body.setEnabled(!busy);
        tags.setEnabled(!busy);
        subject.setEnabled(!busy);
        memberships.forEach(value -> value.checkBox().setEnabled(!busy));
        save.setEnabled(!busy);
        cancel.setEnabled(!busy);
    }

    boolean confirmDiscard() {
        if (!hasEdits()) {
            return true;
        }
        return JOptionPane.showConfirmDialog(this, "Discard your unsaved changes?",
                "Unsaved changes", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    void showSubjects(List<Subject> subjects) {
        if (kind == AcademicPanel.Kind.SUBJECTS) {
            return;
        }
        List<SubjectChoice> next = subjects.stream()
                .map(record -> new SubjectChoice(record.getId(), subjectLabel(record))).toList();
        if (next.equals(availableSubjects)) {
            return;
        }
        if (kind == AcademicPanel.Kind.NOTES) {
            configureSubjectPicker(next, availableSubjects == null ? initialSubject : selectedSubject());
        } else {
            configureMemberships(next, availableSubjects == null ? initialMembers : selectedMembers());
        }
        availableSubjects = next;
    }

    private void configureFields() {
        fields.setOpaque(false);
        title.setName(kind == AcademicPanel.Kind.NOTES ? "noteTitle" : kind.singular + "Name");
        title.setText(initialTitle);
        addField(kind == AcademicPanel.Kind.NOTES ? "Title" : "Name", title, title);
        if (kind == AcademicPanel.Kind.SUBJECTS) {
            code.setName("subjectCode");
            code.setText(initialCode);
            addField("Subject code", code, code);
        }
        if (kind == AcademicPanel.Kind.NOTES) {
            subject.setName("noteSubject");
            DefaultListCellRenderer renderer = new DefaultListCellRenderer();
            renderer.putClientProperty("html.disable", true);
            subject.setRenderer(renderer);
            addField("Subject", subject, subject);
        }
        if (kind == AcademicPanel.Kind.WORKSPACES) {
            membershipChoices.setLayout(new BoxLayout(membershipChoices, BoxLayout.Y_AXIS));
            membershipChoices.setName("workspaceSubjects");
            membershipChoices.getAccessibleContext().setAccessibleName("Included subjects");
            membershipChoices.setOpaque(false);
            addField("Included subjects", membershipChoices, membershipChoices);
        }
        body.setName(kind == AcademicPanel.Kind.NOTES ? "noteContent" : kind.singular + "Description");
        body.setText(initialBody);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        addField(kind == AcademicPanel.Kind.NOTES ? "Content" : "Description", textScroll(body), body);
        if (kind != AcademicPanel.Kind.WORKSPACES) {
            tags.setName(kind.singular + "Tags");
            tags.setText(String.join("\n", initialTags));
            tags.setLineWrap(true);
            addField("Tags (one per line)", textScroll(tags), tags);
        }
    }

    private void configureSubjectPicker(List<SubjectChoice> subjects, String selectedId) {
        subject.removeAllItems();
        subject.addItem(new SubjectChoice(null, "No subject"));
        for (SubjectChoice choice : subjects) {
            subject.addItem(choice);
            if (choice.id().equals(selectedId)) {
                subject.setSelectedItem(choice);
            }
        }
    }

    private void configureMemberships(List<SubjectChoice> subjects, List<String> selectedIds) {
        membershipChoices.removeAll();
        memberships.clear();
        if (subjects.isEmpty()) {
            membershipChoices.add(ViewStyle.muted("Add a subject to include it here."));
        }
        for (SubjectChoice choice : subjects) {
            JCheckBox checkBox = new JCheckBox();
            checkBox.putClientProperty("html.disable", true);
            checkBox.setText(choice.label());
            checkBox.setOpaque(false);
            checkBox.setSelected(selectedIds.contains(choice.id()));
            checkBox.setEnabled(!busy);
            memberships.add(new Membership(choice.id(), checkBox));
            membershipChoices.add(checkBox);
        }
        membershipChoices.revalidate();
        membershipChoices.repaint();
    }

    private JPanel createFooter(Runnable close) {
        JPanel footer = new JPanel(new BorderLayout(0, 7));
        footer.setOpaque(false);
        validation.setName(kind.singular + "Validation");
        footer.add(validation, BorderLayout.PAGE_START);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        buttons.setOpaque(false);
        save.setName(kind.singular + "Save");
        save.addActionListener(event -> save());
        cancel.setName(kind.singular + "Cancel");
        cancel.addActionListener(event -> {
            if (confirmDiscard()) {
                close.run();
            }
        });
        buttons.add(save);
        buttons.add(cancel);
        footer.add(buttons, BorderLayout.LINE_START);
        return footer;
    }

    private void addField(String text, JComponent field, JComponent input) {
        JLabel label = new JLabel(text);
        label.setLabelFor(input);
        addRow(label, 4);
        addRow(field, 12);
    }

    private void addRow(JComponent component, int gap) {
        GridBagConstraints row = new GridBagConstraints();
        row.gridx = 0;
        row.gridy = nextRow++;
        row.weightx = 1;
        row.fill = GridBagConstraints.HORIZONTAL;
        row.insets = new Insets(0, 0, gap, 0);
        fields.add(component, row);
    }

    private void save() {
        if (title.getText().isBlank()) {
            validation.setText(kind == AcademicPanel.Kind.NOTES ? "Enter a title." : "Enter a name.");
            title.requestFocusInWindow();
            return;
        }
        if (kind == AcademicPanel.Kind.SUBJECTS && code.getText().isBlank()) {
            validation.setText("Enter a subject code.");
            code.requestFocusInWindow();
            return;
        }
        validation.setText("");
        String value = title.getText();
        String content = body.getText();
        switch (kind) {
            case SUBJECTS -> {
                if (id == null) {
                    actions.createSubject(value, code.getText(), content, parsedTags());
                } else {
                    actions.updateSubject(id, value, code.getText(), content, parsedTags());
                }
            }
            case WORKSPACES -> {
                if (id == null) {
                    actions.createWorkspace(value, content, selectedMembers());
                } else {
                    actions.updateWorkspace(id, value, content, selectedMembers());
                }
            }
            case NOTES -> {
                if (id == null) {
                    actions.createNote(value, content, selectedSubject(), parsedTags());
                } else {
                    actions.updateNote(id, value, content, selectedSubject(), parsedTags());
                }
            }
        }
    }

    private boolean hasEdits() {
        return !title.getText().equals(initialTitle) || !body.getText().equals(initialBody)
                || !code.getText().equals(initialCode) || !parsedTags().equals(initialTags)
                || !selectedMembers().equals(initialMembers)
                || !Objects.equals(selectedSubject(), initialSubject);
    }

    private List<String> parsedTags() {
        String input = tags.getText();
        if (input.equals(String.join("\n", initialTags))) {
            return initialTags;
        }
        return input.lines().map(String::strip).filter(line -> !line.isEmpty()).distinct().toList();
    }

    private List<String> selectedMembers() {
        List<String> selected = memberships.stream().filter(value -> value.checkBox().isSelected())
                .map(Membership::id).toList();
        var ordered = new LinkedHashSet<>(initialMembers);
        ordered.retainAll(selected);
        ordered.addAll(selected);
        return List.copyOf(ordered);
    }

    private String selectedSubject() {
        Object selected = subject.getSelectedItem();
        return selected instanceof SubjectChoice choice ? choice.id() : null;
    }

    private static String subjectLabel(Subject record) {
        return record.getName() + " (" + record.getSubjectCode() + ")";
    }

    private static JScrollPane textScroll(JTextArea input) {
        return new JScrollPane(input, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    }

    private record Membership(String id, JCheckBox checkBox) {}

    private record SubjectChoice(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
