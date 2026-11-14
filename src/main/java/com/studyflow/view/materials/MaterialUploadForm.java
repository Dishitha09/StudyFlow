package com.studyflow.view.materials;

import com.studyflow.model.Subject;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Collects metadata for a PDF already selected in the file chooser. */
final class MaterialUploadForm extends JPanel {
    private final JTextField title = new JTextField(28);
    private final JTextArea description = new JTextArea(4, 28);
    private final JComboBox<SubjectOption> subject = new JComboBox<>();

    MaterialUploadForm(Path source, List<Subject> subjects) {
        super(new GridBagLayout());
        String fileName = source.getFileName().toString();
        title.setText(fileName.replaceFirst("(?i)\\.pdf$", ""));
        title.setName("materialUploadTitle");
        description.setName("materialUploadDescription");
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        subject.setName("materialUploadSubject");
        subject.addItem(new SubjectOption(null, "No subject"));
        subject.setPrototypeDisplayValue(new SubjectOption("", "Select subject"));
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        subject.setRenderer(renderer);
        for (Subject item : subjects) {
            subject.addItem(new SubjectOption(item.getId(), item.getName()));
        }
        JLabel file = new JLabel();
        file.putClientProperty("html.disable", Boolean.TRUE);
        file.setText(fileName);
        addField("PDF file", file, file, 0);
        addField("Title", title, title, 1);
        addField("Subject", subject, subject, 2);
        addField("Description", new JScrollPane(description), description, 3);
    }

    String title() {
        return title.getText().strip();
    }

    String description() {
        return description.getText().strip();
    }

    String subjectId() {
        return subject.getSelectedItem() instanceof SubjectOption option ? option.id() : null;
    }

    private void addField(String text, JComponent field, JComponent related, int row) {
        JLabel label = new JLabel(text);
        label.setLabelFor(related);
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = 0;
        cell.gridy = row;
        cell.anchor = GridBagConstraints.FIRST_LINE_START;
        cell.insets = new Insets(4, 0, 4, 10);
        add(label, cell);
        cell.gridx = 1;
        cell.weightx = 1;
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.insets = new Insets(3, 0, 3, 0);
        add(field, cell);
    }

    private record SubjectOption(String id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
