package com.studyflow.view.materials;

import com.studyflow.model.StudyMaterial;
import com.studyflow.model.StudyHistoryEntry;
import com.studyflow.model.Subject;
import com.studyflow.service.MaterialLibrary.Passage;
import com.studyflow.service.StudyAssistant;
import com.studyflow.service.StudyAssistant.Mode;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Presents the local PDF library and the study assistant, with separate answers and source passages. */
public final class MaterialPanel extends JPanel {
    public interface Actions {
        void upload(Path source, String title, String description, String subjectId);
        void search(String text);
        void open(String id);
        void delete(String id);
        void study(String question, String id, Mode mode, boolean remote, boolean saveHistory);
        void loadHistory();
        void clearHistory();
    }

    private static final DateTimeFormatter UPLOADED_DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault());
    private static final int ASSISTANT_TAB = 1;
    private static final int HISTORY_TAB = 2;
    private final Actions actions;
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField query = new JTextField();
    private final JButton search = ViewStyle.primary(new JButton("Search"));
    private final JButton upload = new JButton("Upload PDF");
    private final JButton open = new JButton("Open PDF");
    private final JButton delete = new JButton("Delete");
    private final JButton studySelected = new JButton("Study this PDF");
    private final DefaultListModel<StudyMaterial> materialRows = new DefaultListModel<>();
    private final JList<StudyMaterial> library = new JList<>(materialRows);
    private final JLabel libraryStatus = ViewStyle.muted("");
    private final JTextArea materialInfo = readOnlyText();
    private final JTextField topic = new JTextField();
    private final JComboBox<Mode> mode = new JComboBox<>(Mode.values());
    private final JComboBox<Scope> scope = new JComboBox<>();
    private final JCheckBox remote = new JCheckBox("Use configured AI provider");
    private final JCheckBox saveHistory = new JCheckBox("Save in local history", true);
    private final JButton generate = ViewStyle.primary(new JButton("Run assistant"));
    private final JLabel answerHeading = ViewStyle.subheading("Study output");
    private final JTextArea answerText = readOnlyText();
    private final DefaultListModel<Passage> sourceRows = new DefaultListModel<>();
    private final JList<Passage> sources = new JList<>(sourceRows);
    private final JTextArea passageText = readOnlyText();
    private final JButton sourceOpen = new JButton("Open source PDF");
    private final StudyHistoryPanel history;
    private List<StudyMaterial> materials = List.of();
    private List<Subject> subjects = List.of();
    private Map<String, String> subjectNames = Map.of();
    private List<String> matchingIds;
    private boolean busy;
    private boolean remoteAvailable;
    private String storageWarning;

    public MaterialPanel(Actions actions) {
        super(new BorderLayout(0, 14));
        this.actions = Objects.requireNonNull(actions, "actions");
        history = new StudyHistoryPanel(this::reuseRequest, actions::clearHistory);
        setName("materialPanel");
        setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));
        add(ViewStyle.heading("Study Materials"), BorderLayout.PAGE_START);
        tabs.addTab("Library", libraryTab());
        tabs.addTab("Study Assistant", assistantTab());
        tabs.addTab("History", history);
        tabs.addChangeListener(event -> {
            if (!busy && tabs.getSelectedIndex() == HISTORY_TAB) {
                actions.loadHistory();
            }
        });
        tabs.setName("materialTabs");
        add(tabs, BorderLayout.CENTER);
        showData(List.of(), List.of());
    }

    public void showData(List<StudyMaterial> materials, List<Subject> subjects) {
        showData(materials, subjects, null);
    }

    public void showData(List<StudyMaterial> materials, List<Subject> subjects, String storageWarning) {
        this.storageWarning = storageWarning;
        tabs.setEnabledAt(ASSISTANT_TAB, storageWarning == null);
        if (storageWarning != null && tabs.getSelectedIndex() == ASSISTANT_TAB) {
            tabs.setSelectedIndex(0);
        }
        this.materials = List.copyOf(materials);
        this.subjects = List.copyOf(subjects);
        subjectNames = subjects.stream().collect(Collectors.toMap(Subject::getId, Subject::getName));
        String selected = selectedId();
        Scope previousScope = (Scope) scope.getSelectedItem();
        scope.removeAllItems();
        scope.addItem(new Scope(null, "All PDFs"));
        for (StudyMaterial material : materials) {
            scope.addItem(new Scope(material.id(), material.title()));
        }
        selectScope(previousScope == null ? null : previousScope.id());
        if (matchingIds == null) {
            showMatches(materials, selected);
        } else {
            Map<String, StudyMaterial> current = materials.stream().collect(Collectors.toMap(StudyMaterial::id,
                    material -> material));
            List<StudyMaterial> filtered = new ArrayList<>();
            for (String id : matchingIds) {
                StudyMaterial material = current.get(id);
                if (material != null) {
                    filtered.add(material);
                }
            }
            showMatches(filtered, selected);
        }
        var currentIds = materials.stream().map(StudyMaterial::id).collect(Collectors.toSet());
        if (java.util.Collections.list(sourceRows.elements()).stream()
                .anyMatch(source -> !currentIds.contains(source.materialId()))) {
            clearAnswer();
        }
    }

    public void showMatches(List<StudyMaterial> matches) {
        matchingIds = matches.stream().map(StudyMaterial::id).toList();
        showMatches(matches, selectedId());
    }

    private void showMatches(List<StudyMaterial> matches, String selected) {
        materialRows.clear();
        materialRows.addAll(matches);
        selectRow(selected);
        if (library.isSelectionEmpty() && !materialRows.isEmpty()) {
            library.setSelectedIndex(0);
        }
        libraryStatus.setText(storageWarning != null ? "Library unavailable. The local file was kept for recovery."
                : matches.isEmpty() ? (query.getText().isBlank() ? "No PDFs in your library."
                : "No matching PDFs.") : matches.size() + (matches.size() == 1 ? " PDF" : " PDFs"));
        libraryStatus.setToolTipText(storageWarning);
        updateSelection();
    }

    public void selectMaterial(String id) {
        tabs.setSelectedIndex(0);
        matchingIds = null;
        query.setText("");
        showMatches(materials, id);
        library.requestFocusInWindow();
    }

    public void showAnswer(StudyAssistant.Answer answer) {
        answerHeading.setText(answer.generated() ? "AI-generated output" : "Local study output");
        answerText.setText(answer.text());
        answerText.setCaretPosition(0);
        sourceRows.clear();
        sourceRows.addAll(answer.sources());
        if (sourceRows.isEmpty()) {
            passageText.setText("No matching passages.");
        } else {
            sources.setSelectedIndex(0);
        }
        updateControls();
    }

    public void showHistory(List<StudyHistoryEntry> entries) {
        history.showEntries(entries);
    }

    public void showHistoryFailure() {
        history.showLoadFailure();
    }

    public void setRemoteAvailable(boolean available) {
        remoteAvailable = available;
        if (!available) {
            remote.setSelected(false);
        }
        remote.setVisible(available);
        updateControls();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        tabs.setEnabledAt(HISTORY_TAB, !busy);
        history.setBusy(busy);
        updateControls();
    }

    private JPanel libraryTab() {
        JPanel page = card(new BorderLayout(0, 9));
        JPanel controls = transparent(new BorderLayout(6, 0));
        query.setName("materialSearchQuery");
        query.getAccessibleContext().setAccessibleName("Search study materials");
        query.putClientProperty("JTextField.placeholderText", "Search uploaded PDFs");
        query.addActionListener(event -> searchLibrary());
        controls.add(query, BorderLayout.CENTER);
        JPanel commands = transparent(new FlowLayout(FlowLayout.TRAILING, 5, 0));
        search.setName("materialSearch");
        search.addActionListener(event -> searchLibrary());
        upload.setName("materialUpload");
        upload.addActionListener(event -> upload());
        commands.add(search);
        commands.add(upload);
        controls.add(commands, BorderLayout.LINE_END);
        page.add(controls, BorderLayout.PAGE_START);

        library.setName("materialList");
        library.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        library.getAccessibleContext().setAccessibleName("Uploaded PDFs");
        library.setCellRenderer(new InformativeRenderer() {
            @Override
            String display(Object value) {
                StudyMaterial material = (StudyMaterial) value;
                return material == null ? "" : material.title();
            }
        });
        library.addListSelectionListener(event -> updateSelection());
        materialInfo.setName("materialInfo");
        materialInfo.getAccessibleContext().setAccessibleName("Selected PDF details");
        JSplitPane layout = split(JSplitPane.HORIZONTAL_SPLIT, ViewStyle.scroll(library),
                ViewStyle.scroll(materialInfo), 0.37);
        page.add(layout, BorderLayout.CENTER);

        JPanel footer = transparent(new BorderLayout(6, 0));
        libraryStatus.setName("materialLibraryStatus");
        footer.add(libraryStatus, BorderLayout.CENTER);
        JPanel actions = transparent(new FlowLayout(FlowLayout.TRAILING, 5, 0));
        studySelected.setName("materialStudySelected");
        studySelected.addActionListener(event -> {
            selectScope(selectedId());
            tabs.setSelectedIndex(ASSISTANT_TAB);
            topic.requestFocusInWindow();
        });
        open.setName("materialOpen");
        open.addActionListener(event -> openSelected());
        delete.setName("materialDelete");
        delete.addActionListener(event -> deleteSelected());
        actions.add(studySelected);
        actions.add(open);
        actions.add(delete);
        footer.add(actions, BorderLayout.LINE_END);
        page.add(footer, BorderLayout.PAGE_END);
        return page;
    }

    private JPanel assistantTab() {
        JPanel page = card(new BorderLayout(0, 9));
        JPanel controls = transparent(new GridLayout(0, 1, 0, 6));
        JPanel prompt = transparent(new BorderLayout(7, 0));
        topic.setName("materialTopic");
        topic.putClientProperty("JTextField.placeholderText", "Topic, question or keywords");
        topic.getAccessibleContext().setAccessibleName("Study topic, question or keywords");
        topic.addActionListener(event -> runAssistant());
        prompt.add(topic, BorderLayout.CENTER);
        generate.setName("materialGenerate");
        generate.addActionListener(event -> runAssistant());
        prompt.add(generate, BorderLayout.LINE_END);
        controls.add(prompt);

        JPanel options = transparent(new BorderLayout(6, 0));
        JPanel selectors = transparent(new FlowLayout(FlowLayout.LEADING, 6, 0));
        mode.setName("materialMode");
        mode.setRenderer(new InformativeRenderer() {
            @Override
            String display(Object value) {
                return value == null ? "" : modeLabel((Mode) value);
            }
        });
        JLabel modeLabel = new JLabel("Mode");
        modeLabel.setLabelFor(mode);
        selectors.add(modeLabel);
        selectors.add(mode);
        scope.setName("materialScope");
        scope.setPrototypeDisplayValue(new Scope("", "Find PDF by title"));
        scope.setRenderer(new InformativeRenderer() {
            @Override
            String display(Object value) {
                return value == null ? "" : value.toString();
            }
        });
        JLabel scopeLabel = new JLabel("Source");
        scopeLabel.setLabelFor(scope);
        selectors.add(scopeLabel);
        selectors.add(scope);
        options.add(selectors, BorderLayout.CENTER);
        controls.add(options);
        JPanel preferences = transparent(new FlowLayout(FlowLayout.LEADING, 6, 0));
        saveHistory.setName("materialSaveHistory");
        saveHistory.setToolTipText("Save the request and output in this StudyFlow profile.");
        preferences.add(saveHistory);
        remote.setName("materialUseRemote");
        remote.setVisible(false);
        preferences.add(remote);
        controls.add(preferences);
        page.add(controls, BorderLayout.PAGE_START);

        JPanel output = transparent(new BorderLayout(0, 5));
        output.add(answerHeading, BorderLayout.PAGE_START);
        answerText.setName("materialAnswer");
        answerText.getAccessibleContext().setAccessibleName("Study output");
        output.add(ViewStyle.scroll(answerText), BorderLayout.CENTER);
        JPanel evidence = transparent(new BorderLayout(0, 5));
        evidence.add(ViewStyle.subheading("Source passages"), BorderLayout.PAGE_START);
        sources.setName("materialSources");
        sources.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sources.setCellRenderer(new InformativeRenderer() {
            @Override
            String display(Object value) {
                Passage passage = (Passage) value;
                return passage == null ? "" : "Page " + passage.page() + "  ·  " + passage.fileName();
            }
        });
        sources.getAccessibleContext().setAccessibleName("Matching source passages");
        sources.addListSelectionListener(event -> updatePassage());
        passageText.setName("materialPassage");
        passageText.getAccessibleContext().setAccessibleName("Selected passage text");
        JSplitPane sourceSplit = split(JSplitPane.VERTICAL_SPLIT, ViewStyle.scroll(sources),
                ViewStyle.scroll(passageText), 0.3);
        evidence.add(sourceSplit, BorderLayout.CENTER);
        sourceOpen.setName("materialSourceOpen");
        sourceOpen.addActionListener(event -> {
            Passage selected = sources.getSelectedValue();
            if (!busy && selected != null) {
                actions.open(selected.materialId());
            }
        });
        evidence.add(sourceOpen, BorderLayout.PAGE_END);
        page.add(split(JSplitPane.HORIZONTAL_SPLIT, output, evidence, 0.52), BorderLayout.CENTER);
        return page;
    }

    private void upload() {
        if (busy) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setName("materialPdfChooser");
        chooser.setDialogTitle("Choose PDF");
        chooser.setFileFilter(new FileNameExtensionFilter("PDF documents", "pdf"));
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path source = chooser.getSelectedFile().toPath();
            MaterialUploadForm form = new MaterialUploadForm(source, subjects);
            if (JOptionPane.showConfirmDialog(this, form, "Upload study material", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
                actions.upload(source, form.title(), form.description(), form.subjectId());
            }
        }
    }

    private void searchLibrary() {
        if (busy) {
            return;
        }
        String text = query.getText().strip();
        if (text.isEmpty()) {
            matchingIds = null;
            showMatches(materials, selectedId());
        } else {
            actions.search(text);
        }
    }

    private void openSelected() {
        String id = selectedId();
        if (!busy && id != null) {
            actions.open(id);
        }
    }

    private void deleteSelected() {
        StudyMaterial selected = library.getSelectedValue();
        if (!busy && selected != null && JOptionPane.showConfirmDialog(this,
                "Delete this PDF and its extracted passages?", "Delete study material",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
            actions.delete(selected.id());
        }
    }

    private void runAssistant() {
        if (busy) {
            return;
        }
        String question = topic.getText().strip();
        Mode selectedMode = (Mode) mode.getSelectedItem();
        if (selectedMode == Mode.EXPLAIN && question.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a topic or question to explain.", "Study Assistant",
                    JOptionPane.INFORMATION_MESSAGE);
            topic.requestFocusInWindow();
            return;
        }
        boolean useRemote = remoteAvailable && remote.isSelected();
        if (useRemote && JOptionPane.showConfirmDialog(this,
                "Send your question and matching passages with document and page references to the configured AI provider?",
                "Confirm external AI request", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        Scope selectedScope = (Scope) scope.getSelectedItem();
        actions.study(question, selectedScope == null ? null : selectedScope.id(), selectedMode, useRemote,
                saveHistory.isSelected());
    }

    private void reuseRequest(StudyHistoryEntry entry) {
        topic.setText(entry.topic());
        mode.setSelectedItem(Mode.valueOf(entry.mode()));
        selectScope(entry.materialId());
        remote.setSelected(false);
        clearAnswer();
        tabs.setSelectedIndex(ASSISTANT_TAB);
        topic.requestFocusInWindow();
    }

    private void updateSelection() {
        StudyMaterial selected = library.getSelectedValue();
        if (selected == null) {
            materialInfo.setText("");
        } else {
            String subject = selected.subjectId() == null ? "No subject"
                    : subjectNames.getOrDefault(selected.subjectId(), "No subject");
            materialInfo.setText(selected.title() + "\n\nFile: " + selected.fileName() + "\nSubject: " + subject
                    + "\nUploaded: " + UPLOADED_DATE.format(selected.uploadedAt())
                    + (selected.description().isBlank() ? "" : "\n\n" + selected.description()));
            materialInfo.setCaretPosition(0);
        }
        updateControls();
    }

    private void updatePassage() {
        Passage selected = sources.getSelectedValue();
        passageText.setText(selected == null ? "" : selected.text());
        passageText.setCaretPosition(0);
        updateControls();
    }

    private void clearAnswer() {
        answerHeading.setText("Study output");
        answerText.setText("");
        sourceRows.clear();
        passageText.setText("");
    }

    private void updateControls() {
        boolean libraryReady = !busy && storageWarning == null;
        boolean hasMaterial = libraryReady && selectedId() != null;
        upload.setEnabled(libraryReady);
        query.setEnabled(libraryReady);
        search.setEnabled(libraryReady);
        open.setEnabled(hasMaterial);
        delete.setEnabled(hasMaterial);
        studySelected.setEnabled(hasMaterial);
        topic.setEnabled(!busy);
        mode.setEnabled(!busy);
        scope.setEnabled(!busy);
        remote.setEnabled(!busy && remoteAvailable);
        saveHistory.setEnabled(!busy);
        generate.setEnabled(libraryReady && !materials.isEmpty());
        sourceOpen.setEnabled(!busy && sources.getSelectedValue() != null);
    }

    private String selectedId() {
        StudyMaterial selected = library.getSelectedValue();
        return selected == null ? null : selected.id();
    }

    private void selectScope(String id) {
        scope.setSelectedIndex(0);
        for (int index = 1; id != null && index < scope.getItemCount(); index++) {
            if (id.equals(scope.getItemAt(index).id())) {
                scope.setSelectedIndex(index);
                return;
            }
        }
    }

    private void selectRow(String id) {
        for (int index = 0; id != null && index < materialRows.size(); index++) {
            if (id.equals(materialRows.get(index).id())) {
                library.setSelectedIndex(index);
                library.ensureIndexIsVisible(index);
                return;
            }
        }
    }

    static String modeLabel(Mode mode) {
        return switch (mode) {
            case SHORT_SUMMARY -> "Short summary";
            case DETAILED_SUMMARY -> "Detailed summary";
            case KEY_POINTS -> "Key points";
            case FORMULAS -> "Formula extraction";
            case REVISION_NOTES -> "Revision notes";
            case EXPLAIN -> "Explain a topic";
        };
    }

    private static JTextArea readOnlyText() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
        return area;
    }

    private static JSplitPane split(int direction, JComponent first, JComponent second, double proportion) {
        first.setMinimumSize(new Dimension(0, 0));
        second.setMinimumSize(new Dimension(0, 0));
        int extent = direction == JSplitPane.HORIZONTAL_SPLIT ? 190 : 75;
        var split = new JSplitPane(direction, first, second);
        split.setContinuousLayout(true);
        split.setResizeWeight(proportion);
        split.setDividerLocation(extent);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    private static JPanel card(BorderLayout layout) {
        JPanel panel = ViewStyle.card(layout);
        panel.setBorder(BorderFactory.createEmptyBorder(13, 14, 13, 14));
        return panel;
    }

    private static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private record Scope(String id, String title) {
        @Override
        public String toString() {
            return title;
        }
    }

    private abstract static class InformativeRenderer extends DefaultListCellRenderer {
        InformativeRenderer() {
            putClientProperty("html.disable", Boolean.TRUE);
        }

        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                               boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, display(value), index, selected, focused);
            setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return this;
        }

        abstract String display(Object value);
    }
}
