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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;

/** Shows locally saved assistant outputs and lets the student reuse or clear them. */
final class StudyHistoryPanel extends JPanel {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault());

    private final DefaultListModel<StudyHistoryEntry> rows = new DefaultListModel<>();
    private final JList<StudyHistoryEntry> list = new JList<>(rows);
    private final JTextArea details = new JTextArea();
    private final JLabel status = ViewStyle.muted("No saved study outputs.");
    private final JButton reuse = new JButton("Use this request");
    private final JButton clear = new JButton("Clear history");
    private boolean busy;
    private boolean loadFailed;

    StudyHistoryPanel(Consumer<StudyHistoryEntry> useRequest, Runnable clearHistory) {
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
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ViewStyle.scroll(list), ViewStyle.scroll(details));
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
        controls.add(clear);
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

    private void showSelected() {
        StudyHistoryEntry entry = list.getSelectedValue();
        if (entry == null) {
            details.setText("");
        } else {
            String scope = entry.materialId() == null ? "All PDFs" : entry.materialTitle();
            String sources = entry.references().stream()
                    .map(reference -> reference.fileName() + " · Page " + reference.page())
                    .collect(Collectors.joining("\n"));
            String topic = entry.topic().isBlank() ? "No topic entered" : entry.topic();
            details.setText(topic + "\n" + MaterialPanel.modeLabel(Mode.valueOf(entry.mode())) + " · " + scope
                    + "\n" + (entry.generated() ? "AI-generated output" : "Local study output") + "\n\n"
                    + entry.output() + "\n\nSources\n" + sources);
            details.setCaretPosition(0);
        }
        updateControls();
    }

    private void updateControls() {
        reuse.setEnabled(!busy && list.getSelectedValue() != null);
        clear.setEnabled(!busy && (!rows.isEmpty() || loadFailed));
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
