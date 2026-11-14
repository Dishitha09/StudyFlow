package com.studyflow.view.planning;

import com.studyflow.view.ViewStyle;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;

/** Searches the local academic workspace and opens the selected record. */
public final class SearchPanel extends JPanel {
    private final JTextField query = new JTextField();
    private final JButton submit = ViewStyle.primary(new JButton("Search"));
    private final DefaultListModel<Result> rows = new DefaultListModel<>();
    private final JList<Result> results = new JList<>(rows);
    private final JLabel status = ViewStyle.muted("Enter a search term to get started.");
    private final JButton openRecord = new JButton("Open result");
    private final Consumer<Result> open;
    private boolean hasSearched;
    private boolean busy;

    public SearchPanel(Consumer<String> search, Consumer<Result> open) {
        super(new BorderLayout(0, 16));
        Objects.requireNonNull(search, "search");
        this.open = Objects.requireNonNull(open, "open");
        setName("searchPanel");
        setBorder(BorderFactory.createEmptyBorder(26, 28, 24, 28));
        query.setName("globalSearchQuery");
        query.getAccessibleContext().setAccessibleName("Search study records");
        query.putClientProperty("JTextField.placeholderText", "Search tasks, subjects, workspaces and notes");
        submit.setName("globalSearchSubmit");
        results.setName("globalSearchResults");
        results.getAccessibleContext().setAccessibleName("Search results");
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.setCellRenderer(new ResultRenderer());
        openRecord.setName("globalSearchOpen");
        status.setName("globalSearchStatus");

        JPanel searchLine = new JPanel(new BorderLayout(10, 0));
        searchLine.setOpaque(false);
        searchLine.add(query, BorderLayout.CENTER);
        searchLine.add(submit, BorderLayout.LINE_END);
        JPanel header = new JPanel(new BorderLayout(0, 14));
        header.setOpaque(false);
        header.add(ViewStyle.heading("Search"), BorderLayout.PAGE_START);
        header.add(searchLine, BorderLayout.CENTER);
        add(header, BorderLayout.PAGE_START);

        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setOpaque(false);
        footer.add(status, BorderLayout.CENTER);
        footer.add(openRecord, BorderLayout.LINE_END);
        JPanel resultCard = ViewStyle.card(new BorderLayout(0, 12));
        resultCard.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        resultCard.add(ViewStyle.scroll(results), BorderLayout.CENTER);
        resultCard.add(footer, BorderLayout.PAGE_END);
        add(resultCard, BorderLayout.CENTER);

        submit.addActionListener(event -> submitQuery(search));
        query.addActionListener(event -> submitQuery(search));
        openRecord.addActionListener(event -> openSelected());
        results.addListSelectionListener(event -> updateControls());
        results.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openResult");
        results.getActionMap().put("openResult", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                openSelected();
            }
        });
        results.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int index = results.locationToIndex(event.getPoint());
                if (event.getClickCount() == 2 && index >= 0
                        && results.getCellBounds(index, index).contains(event.getPoint())) {
                    openSelected();
                }
            }
        });
        updateControls();
    }

    public void showResults(List<Result> records) {
        rows.clear();
        rows.addAll(records);
        if (!rows.isEmpty()) {
            results.setSelectedIndex(0);
        }
        if (rows.isEmpty()) {
            status.setText(hasSearched ? "No matching records." : "Enter a search term to get started.");
        } else {
            status.setText(rows.size() + (rows.size() == 1 ? " result" : " results"));
        }
        updateControls();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        updateControls();
    }

    public void focusQuery() {
        if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() != query) {
            query.requestFocusInWindow();
        }
    }

    private void submitQuery(Consumer<String> search) {
        if (busy) {
            return;
        }
        String text = query.getText().strip();
        hasSearched = !text.isBlank();
        if (hasSearched) {
            search.accept(text);
        } else {
            showResults(List.of());
        }
    }

    private void openSelected() {
        Result selected = results.getSelectedValue();
        if (!busy && selected != null) {
            open.accept(selected);
        }
    }

    private void updateControls() {
        submit.setEnabled(!busy);
        openRecord.setEnabled(!busy && results.getSelectedValue() != null);
    }

    public record Result(String type, String id, String label, String detail) {
    }

    private static final class ResultRenderer extends DefaultListCellRenderer {
        ResultRenderer() {
            putClientProperty("html.disable", Boolean.TRUE);
            setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 11));
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean selected, boolean focused) {
            Result record = (Result) value;
            String text = record.type() + "  ·  " + record.label();
            if (record.detail() != null && !record.detail().isBlank()) {
                text += "  ·  " + record.detail();
            }
            super.getListCellRendererComponent(list, text, index, selected, focused);
            setToolTipText(text);
            return this;
        }
    }
}
