package com.studyflow.view.materials;

import com.studyflow.model.StudyHistoryEntry;
import com.studyflow.service.StudyAssistant.Mode;
import com.studyflow.view.ViewStyle;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;

/** Shows locally saved assistant outputs, with request reuse, source links and removal. */
final class StudyHistoryPanel extends JPanel {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault());

    private final DefaultListModel<StudyHistoryEntry> rows = new DefaultListModel<>();
    private final JList<StudyHistoryEntry> list = new JList<>(rows);
    private final JTextArea details = new JTextArea();
    private final JLabel status = ViewStyle.muted("No saved study outputs.");
    private final JButton reuse = new JButton("Use this request");
    private final JButton delete = new JButton("Delete selected");
    private final JButton clear = new JButton("Clear history");
    private final JComboBox<SourceChoice> sourceChoices = new JComboBox<>();
    private final JButton open = new JButton("Open source PDF");
    private Set<String> materialIds = Set.of();
    private boolean busy;
    private boolean loadFailed;

    StudyHistoryPanel(Consumer<StudyHistoryEntry> useRequest, Runnable clearHistory, Consumer<String> deleteEntry,
                      Consumer<String> openMaterial) {
        super(new BorderLayout(0, 10));
        setName("materialHistory");
        setBorder(BorderFactory.createEmptyBorder(13, 14, 13, 14));
        status.setName("materialHistoryStatus");
        add(status, BorderLayout.PAGE_START);

        list.setName("materialHistoryList");
        list.getAccessibleContext().setAccessibleName("Saved study outputs");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new HistoryRenderer());
        list.addListSelectionListener(event -> showSelected());
        details.setName("materialHistoryDetails");
        details.getAccessibleContext().setAccessibleName("Saved study output and sources");
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
        JPanel result = new JPanel(new BorderLayout(0, 6));
        result.setOpaque(false);
        result.add(ViewStyle.scroll(details), BorderLayout.CENTER);
        JPanel sources = new JPanel(new BorderLayout(6, 0));
        sources.setOpaque(false);
        sourceChoices.setName("materialHistorySources");
        sourceChoices.getAccessibleContext().setAccessibleName("Saved PDF reference");
        sourceChoices.setPrototypeDisplayValue(new SourceChoice(null, "PDF source references"));
        sourceChoices.addActionListener(event -> updateControls());
        open.setName("materialHistoryOpenSource");
        open.addActionListener(event -> {
            SourceChoice choice = (SourceChoice) sourceChoices.getSelectedItem();
            if (choice != null && choice.materialId() != null && materialIds.contains(choice.materialId()) && !busy) {
                openMaterial.accept(choice.materialId());
            }
        });
        sources.add(sourceChoices, BorderLayout.CENTER);
        sources.add(open, BorderLayout.LINE_END);
        result.add(sources, BorderLayout.PAGE_END);
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ViewStyle.scroll(list), result);
        split.setResizeWeight(0.34);
        split.setDividerLocation(200);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.TRAILING, 7, 0));
        controls.setOpaque(false);
        reuse.setName("materialHistoryReuse");
        reuse.addActionListener(event -> {
            StudyHistoryEntry selected = list.getSelectedValue();
            if (selected != null && !busy) {
                useRequest.accept(selected);
            }
        });
        clear.setName("materialHistoryClear");
        clear.addActionListener(event -> {
            if (JOptionPane.showConfirmDialog(this, "Remove all saved assistant requests and outputs from this profile?",
                    "Clear study history", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
                    == JOptionPane.OK_OPTION) {
                clearHistory.run();
            }
        });
        delete.setName("materialHistoryDelete");
        delete.addActionListener(event -> {
            StudyHistoryEntry selected = list.getSelectedValue();
            if (!busy && selected != null && JOptionPane.showConfirmDialog(this, "Remove this saved request and output?",
                    "Delete study history entry", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
                    == JOptionPane.OK_OPTION) {
                deleteEntry.accept(selected.entryId());
            }
        });
        controls.add(clear);
        controls.add(delete);
        controls.add(reuse);
        add(controls, BorderLayout.PAGE_END);
        updateControls();
    }

    void showEntries(List<StudyHistoryEntry> entries) {
        loadFailed = false;
        StudyHistoryEntry selected = list.getSelectedValue();
        rows.clear();
        rows.addAll(entries);
        if (!rows.isEmpty()) {
            list.setSelectedValue(selected, true);
            if (list.isSelectionEmpty()) {
                list.setSelectedIndex(0);
            }
        }
        status.setText(rows.isEmpty() ? "No saved study outputs."
                : rows.size() + (rows.size() == 1 ? " saved output" : " saved outputs") + " on this device");
        showSelected();
    }

    void showLoadFailure() {
        loadFailed = true;
        rows.clear();
        status.setText("Could not load study history.");
        showSelected();
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        updateControls();
    }

    void setMaterialIds(Set<String> materialIds) {
        this.materialIds = Set.copyOf(materialIds);
        updateControls();
    }

    private void showSelected() {
        StudyHistoryEntry entry = list.getSelectedValue();
        sourceChoices.removeAllItems();
        if (entry == null) {
            details.setText("");
        } else {
            String scope = entry.materialId() == null ? "All PDFs" : entry.materialTitle();
            for (StudyHistoryEntry.Reference reference : entry.references()) {
                String id = reference.materialId() == null ? entry.materialId() : reference.materialId();
                String label = reference.fileName() + " · Page " + reference.page();
                sourceChoices.addItem(new SourceChoice(id, label));
            }
            String sources = entry.references().stream()
                    .map(reference -> reference.fileName() + " · Page " + reference.page())
                    .collect(Collectors.joining("\n"));
            String topic = entry.topic().isBlank() ? "No topic entered" : entry.topic();
            details.setText(topic + "\n" + MaterialPanel.modeLabel(Mode.valueOf(entry.mode())) + " · " + scope
                    + "\n" + (entry.generated() ? "AI-generated output" : "Local extracts") + "\n\n"
                    + entry.output() + "\n\nSources\n" + sources);
            details.setCaretPosition(0);
        }
        updateControls();
    }

    private void updateControls() {
        reuse.setEnabled(!busy && list.getSelectedValue() != null);
        delete.setEnabled(!busy && list.getSelectedValue() != null);
        clear.setEnabled(!busy && (!rows.isEmpty() || loadFailed));
        sourceChoices.setEnabled(!busy && sourceChoices.getItemCount() != 0);
        SourceChoice choice = (SourceChoice) sourceChoices.getSelectedItem();
        open.setEnabled(!busy && choice != null && choice.materialId() != null && materialIds.contains(choice.materialId()));
    }

    private record SourceChoice(String materialId, String label) {
        @Override public String toString() { return label; }
    }

    private static final class HistoryRenderer extends DefaultListCellRenderer {
        HistoryRenderer() {
            putClientProperty("html.disable", Boolean.TRUE);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object item, int index, boolean selected,
                                                       boolean focused) {
            if (!(item instanceof StudyHistoryEntry entry)) {
                return super.getListCellRendererComponent(list, "", index, selected, focused);
            }
            String label = entry.topic().isBlank() ? MaterialPanel.modeLabel(Mode.valueOf(entry.mode()))
                    : entry.topic();
            String time = TIMESTAMP.format(entry.createdAt());
            super.getListCellRendererComponent(list, time + "   " + label, index, selected, focused);
            setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
            setToolTipText(label);
            return this;
        }
    }
}
