package com.studyflow.view.materials;

import com.studyflow.service.MaterialLibrary;
import com.studyflow.service.MaterialLibrary.Passage;
import com.studyflow.service.MaterialLibrary.RetrievalResult;
import com.studyflow.view.ViewStyle;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.*;

/** Presents ranked local PDF hits with document names and original page references. */
final class PassageSearchPanel extends JPanel {
    private final DefaultListModel<Passage> rows = new DefaultListModel<>();
    private final JList<Passage> matches = new JList<>(rows);
    private final JLabel status = ViewStyle.muted("Search from the Library tab to find text in your PDFs.");
    private final JTextArea excerpt = new JTextArea();
    private final JButton open = new JButton("Open source PDF");
    private String query = "";
    private boolean busy;
    private Set<String> currentMaterials = Set.of();

    PassageSearchPanel(Consumer<String> openPdf) {
        super(new BorderLayout(0, 10));
        setName("pdfPassageSearch");
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        status.setName("pdfPassageStatus");
        add(status, BorderLayout.PAGE_START);
        matches.setName("pdfPassageMatches");
        matches.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        matches.getAccessibleContext().setAccessibleName("Ranked PDF passages with page numbers");
        matches.setCellRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                            boolean selected, boolean focused) {
                putClientProperty("html.disable", Boolean.TRUE);
                return super.getListCellRendererComponent(list, value instanceof Passage page
                        ? page.fileName() + " · page " + page.page() : "", index, selected, focused);
            }
        });
        excerpt.setName("pdfPassagePreview");
        excerpt.getAccessibleContext().setAccessibleName("Matched source excerpt");
        excerpt.setEditable(false);
        excerpt.setLineWrap(true);
        excerpt.setWrapStyleWord(true);
        excerpt.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ViewStyle.scroll(matches), ViewStyle.scroll(excerpt));
        split.setResizeWeight(0.38);
        split.setDividerLocation(240);
        split.setMinimumSize(new Dimension(0, 0));
        add(split, BorderLayout.CENTER);
        open.setName("pdfPassageOpen");
        open.addActionListener(event -> {
            Passage passage = matches.getSelectedValue();
            if (passage != null && currentMaterials.contains(passage.materialId())) openPdf.accept(passage.materialId());
        });
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING, 0, 0));
        actions.setOpaque(false);
        actions.add(open);
        add(actions, BorderLayout.PAGE_END);
        matches.addListSelectionListener(event -> updateSelection());
        updateSelection();
    }

    void showSearch(String topic, RetrievalResult result) {
        query = topic;
        rows.clear();
        rows.addAll(result.passages());
        int count = rows.size();
        String alerts = result.warnings().stream().limit(2)
                .map(item -> item.fileName() + ": " + item.message()).collect(java.util.stream.Collectors.joining("\n"));
        status.setText((count == 0 ? "No matched passages." : count + (count == 1 ? " ranked passage." : " ranked passages."))
                + (alerts.isBlank() ? "" : " Some files need attention; see details."));
        status.setToolTipText(alerts.isBlank() ? null : "PDF details: " + alerts);
        if (!rows.isEmpty()) matches.setSelectedIndex(0);
        else if (!alerts.isBlank()) excerpt.setText(alerts);
    }

    void updateLibrary(Set<String> materialIds) {
        currentMaterials = Set.copyOf(materialIds);
        updateSelection();
    }

    void setBusy(boolean value) {
        busy = value;
        updateOpen();
    }

    private void updateSelection() {
        Passage page = matches.getSelectedValue();
        excerpt.setText(page == null ? "" : page.fileName() + " · page " + page.page() + "\n\n"
                + MaterialLibrary.preview(page, query));
        excerpt.setCaretPosition(0);
        updateOpen();
    }

    private void updateOpen() {
        Passage page = matches.getSelectedValue();
        open.setEnabled(!busy && page != null && currentMaterials.contains(page.materialId()));
    }
}
