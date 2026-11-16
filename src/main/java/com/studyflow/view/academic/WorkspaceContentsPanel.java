package com.studyflow.view.academic;

import com.studyflow.model.Note;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.Workspace;
import com.studyflow.view.ViewStyle;
import com.studyflow.view.academic.AcademicPanel.LinkedRecord;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;

/** Browses the records already linked to a workspace's member subjects. */
final class WorkspaceContentsPanel extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    private final LinkedListPane subjects;
    private final LinkedListPane tasks;
    private final LinkedListPane notes;

    WorkspaceContentsPanel(BiConsumer<LinkedRecord, String> open) {
        super(new BorderLayout());
        subjects = new LinkedListPane(LinkedRecord.SUBJECT, open, "Add subjects with Edit workspace.");
        tasks = new LinkedListPane(LinkedRecord.TASK, open, "No tasks are linked to the included subjects.");
        notes = new LinkedListPane(LinkedRecord.NOTE, open, "No notes are linked to the included subjects.");
        setOpaque(false);
        tabs.setName("workspaceContents");
        tabs.addTab("Subjects", subjects);
        tabs.addTab("Tasks", tasks);
        tabs.addTab("Notes", notes);
        add(tabs, BorderLayout.CENTER);
    }

    void show(Workspace workspace, List<Subject> subjectRecords, List<Task> taskRecords, List<Note> noteRecords) {
        setVisible(workspace != null);
        if (workspace == null) {
            return;
        }
        Map<String, Subject> byId = subjectRecords.stream().collect(Collectors.toMap(Subject::getId, v -> v));
        Set<String> members = Set.copyOf(workspace.getSubjectIds());
        List<Link> subjectLinks = workspace.getSubjectIds().stream().map(byId::get).filter(Objects::nonNull)
                .map(subject -> new Link(subject.getId(), subject.getName(), subject.getSubjectCode())).toList();
        List<Link> taskLinks = taskRecords.stream()
                .filter(task -> task.getSubjectId() != null && members.contains(task.getSubjectId()))
                .sorted()
                .map(task -> new Link(task.getId(), task.getTitle(), name(byId, task.getSubjectId())
                        + (task.getDueDate() == null ? "" : " · " + task.getDueDate()))).toList();
        List<Link> noteLinks = noteRecords.stream()
                .filter(note -> note.getSubjectId() != null && members.contains(note.getSubjectId()))
                .map(note -> new Link(note.getId(), note.getTitle(), name(byId, note.getSubjectId()))).toList();
        subjects.showLinks(subjectLinks);
        tasks.showLinks(taskLinks);
        notes.showLinks(noteLinks);
        tabs.setTitleAt(0, "Subjects (" + subjectLinks.size() + ")");
        tabs.setTitleAt(1, "Tasks (" + taskLinks.size() + ")");
        tabs.setTitleAt(2, "Notes (" + noteLinks.size() + ")");
    }

    void setBusy(boolean busy) {
        subjects.setBusy(busy);
        tasks.setBusy(busy);
        notes.setBusy(busy);
    }

    private static String name(Map<String, Subject> subjects, String id) {
        Subject subject = subjects.get(id);
        return subject == null ? "Unknown subject" : subject.getName();
    }

    private record Link(String id, String title, String subtitle) {
        @Override
        public String toString() {
            return title;
        }
    }

    private static final class LinkedListPane extends JPanel {
        private final DefaultListModel<Link> model = new DefaultListModel<>();
        private final JList<Link> list = new JList<>(model);
        private final JLabel empty;
        private final JButton openButton;
        private final LinkedRecord kind;
        private final BiConsumer<LinkedRecord, String> open;
        private boolean busy;

        LinkedListPane(LinkedRecord kind, BiConsumer<LinkedRecord, String> open, String emptyMessage) {
            super(new BorderLayout(0, 6));
            this.kind = kind;
            this.open = open;
            String noun = kind.name().toLowerCase(Locale.ROOT);
            String componentStem = "workspaceLinked" + Character.toUpperCase(noun.charAt(0)) + noun.substring(1);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            empty = ViewStyle.muted(emptyMessage);
            empty.setBorder(BorderFactory.createEmptyBorder(3, 4, 0, 0));
            list.setName(componentStem + "List");
            list.getAccessibleContext().setAccessibleName("Linked " + noun + " records");
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setFixedCellHeight(43);
            list.setCellRenderer(this::renderLink);
            list.addListSelectionListener(event -> updateButton());
            list.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "openLinkedRecord");
            list.getActionMap().put("openLinkedRecord", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    openSelected();
                }
            });
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    int index = list.locationToIndex(event.getPoint());
                    if (event.getClickCount() == 2 && index >= 0
                            && list.getCellBounds(index, index).contains(event.getPoint())) {
                        openSelected();
                    }
                }
            });
            var scroll = ViewStyle.scroll(list);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            list.setOpaque(false);
            openButton = new JButton("Open " + noun);
            openButton.setName(componentStem + "Open");
            openButton.addActionListener(event -> openSelected());
            add(empty, BorderLayout.PAGE_START);
            add(scroll, BorderLayout.CENTER);
            JPanel controls = new JPanel(new BorderLayout());
            controls.setOpaque(false);
            controls.add(openButton, BorderLayout.LINE_START);
            add(controls, BorderLayout.PAGE_END);
            updateButton();
        }

        void showLinks(List<Link> links) {
            Link selected = list.getSelectedValue();
            model.clear();
            model.addAll(links);
            if (selected != null) {
                for (int index = 0; index < links.size(); index++) {
                    if (links.get(index).id().equals(selected.id())) {
                        list.setSelectedIndex(index);
                        break;
                    }
                }
            }
            if (!model.isEmpty() && list.isSelectionEmpty()) {
                list.setSelectedIndex(0);
            }
            empty.setVisible(model.isEmpty());
            updateButton();
        }

        void setBusy(boolean busy) {
            this.busy = busy;
            updateButton();
        }

        private void updateButton() {
            openButton.setEnabled(!busy && !list.isSelectionEmpty());
        }

        private void openSelected() {
            Link selected = list.getSelectedValue();
            if (!busy && selected != null) {
                open.accept(kind, selected.id());
            }
        }

        private Component renderLink(JList<? extends Link> owner, Link link, int row, boolean selected,
                                     boolean focused) {
            JPanel cell = new JPanel(new BorderLayout(0, 2));
            cell.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            cell.setBackground(selected ? owner.getSelectionBackground() : ViewStyle.color("surface"));
            JLabel title = label(link.title(), selected ? owner.getSelectionForeground() : owner.getForeground());
            title.setFont(owner.getFont().deriveFont(Font.BOLD));
            cell.add(title, BorderLayout.NORTH);
            cell.add(label(link.subtitle(), selected ? owner.getSelectionForeground() : ViewStyle.color("muted")),
                    BorderLayout.SOUTH);
            return cell;
        }

        private static JLabel label(String text, java.awt.Color foreground) {
            JLabel label = new JLabel();
            label.putClientProperty("html.disable", true);
            label.setText(text);
            label.setForeground(foreground);
            return label;
        }
    }
}
