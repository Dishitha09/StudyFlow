package com.studyflow.view.materials;

import com.studyflow.model.StudyMaterial;
import com.studyflow.model.StudyHistoryEntry;
import com.studyflow.model.Subject;
import com.studyflow.service.MaterialLibrary.Passage;
import com.studyflow.service.MaterialLibrary.ImportProgress;
import com.studyflow.service.MaterialLibrary.ImportStage;
import com.studyflow.service.MaterialLibrary.RetrievalResult;
import com.studyflow.service.StudyAssistant;
import com.studyflow.service.StudyAssistant.Difficulty;
import com.studyflow.service.StudyAssistant.Mode;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
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
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileFilter;

/** Presents the study materials library and supports PDF text search and study tools. */
public final class MaterialPanel extends JPanel {
    public interface Actions {
        void upload(Path source, String title, String description, String subjectId);
        void search(String text);
        void open(String id);
        void delete(String id);
        void prepareStudy(StudyAssistant.Request request);
        void runStudy(StudyAssistant.Prepared prepared);
        void loadHistory();
        void clearHistory();
        void deleteHistory(String entryId);
    }

    private static final DateTimeFormatter UPLOADED_DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault());
    private static final int ASSISTANT_TAB = 1;
    private static final int HISTORY_TAB = 2;
    private static final int PASSAGES_TAB = 3;
    private final Actions actions;
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField query = new JTextField();
    private final JButton search = ViewStyle.primary(new JButton("Search"));
    private final JButton upload = new JButton("Upload file");
    private final JButton open = new JButton("Open PDF");
    private final JButton delete = new JButton("Delete");
    private final JButton studySelected = new JButton("Study this PDF");
    private final JComboBox<Scope> librarySubject = new JComboBox<>();
    private final DefaultListModel<StudyMaterial> materialRows = new DefaultListModel<>();
    private final JList<StudyMaterial> library = new JList<>(materialRows);
    private final JLabel libraryStatus = ViewStyle.muted("");
    private final JTextArea materialInfo = readOnlyText();
    private final JTextField topic = new JTextField();
    private final JComboBox<Mode> mode = new JComboBox<>(Mode.values());
    private final JComboBox<Scope> scope = new JComboBox<>();
    private final JCheckBox remote = new JCheckBox("Use configured AI provider");
    private final JCheckBox saveHistory = new JCheckBox("Save in local history", true);
    private final JPanel questionOptions = transparent(new FlowLayout(FlowLayout.LEADING, 6, 0));
    private final JSpinner questionCount = new JSpinner(new SpinnerNumberModel(StudyAssistant.DEFAULT_QUESTION_COUNT,
            1, StudyAssistant.MAX_QUESTIONS, 1));
    private final JComboBox<Difficulty> difficulty = new JComboBox<>(Difficulty.values());
    private final JCheckBox showAnswers = new JCheckBox("Show answers");
    private final JCheckBox showReferences = new JCheckBox("Show source references", true);
    private final JLabel assistantStatus = ViewStyle.muted("");
    private final JButton generate = ViewStyle.primary(new JButton("Run assistant"));
    private final JLabel answerHeading = ViewStyle.subheading("Study output");
    private final JTextArea answerText = readOnlyText();
    private final CardLayout answerCards = new CardLayout();
    private final JPanel answerBody = transparent(answerCards);
    private final QuestionSetPanel questions = new QuestionSetPanel();
    private JPanel evidence;
    private boolean hasQuestions;
    private final DefaultListModel<Passage> sourceRows = new DefaultListModel<>();
    private final JList<Passage> sources = new JList<>(sourceRows);
    private final JTextArea passageText = readOnlyText();
    private final JButton sourceOpen = new JButton("Open source PDF");
    private final StudyHistoryPanel history;
    private final PassageSearchPanel passageSearch;
    private final javax.swing.JProgressBar importProgress = new javax.swing.JProgressBar();
    private final JButton cancelImport = new JButton("Cancel import");
    private final JPanel importState = transparent(new BorderLayout(8, 0));
    private Runnable cancelAction;
    private boolean importCancelled;
    private List<StudyMaterial> materials = List.of();
    private Set<String> pdfIds = Set.of();
    private Set<String> searchablePdfIds = Set.of();
    private List<Subject> subjects = List.of();
    private Map<String, String> subjectNames = Map.of();
    private List<String> matchingIds;
    private boolean busy;
    private boolean remoteAvailable;
    private String storageWarning;
    private String requestedSourceWarning;

    public MaterialPanel(Actions actions) {
        super(new BorderLayout(0, 14));
        this.actions = Objects.requireNonNull(actions, "actions");
        history = new StudyHistoryPanel(this::reuseRequest, actions::clearHistory, actions::deleteHistory,
                actions::open);
        passageSearch = new PassageSearchPanel(actions::open);
        setName("materialPanel");
        setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));
        add(ViewStyle.heading("Study Materials"), BorderLayout.PAGE_START);
        tabs.addTab("Library", libraryTab());
        tabs.addTab("Study Assistant", assistantTab());
        tabs.addTab("History", history);
        tabs.addTab("Text matches", passageSearch);
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
        tabs.setEnabledAt(PASSAGES_TAB, storageWarning == null);
        if (storageWarning != null && (tabs.getSelectedIndex() == ASSISTANT_TAB
                || tabs.getSelectedIndex() == PASSAGES_TAB)) {
            tabs.setSelectedIndex(0);
        }
        this.materials = List.copyOf(materials);
        this.subjects = List.copyOf(subjects);
        pdfIds = materials.stream().filter(StudyMaterial::isPdf).map(StudyMaterial::id).collect(Collectors.toSet());
        searchablePdfIds = materials.stream().filter(StudyMaterial::hasPageSearch).map(StudyMaterial::id)
                .collect(Collectors.toSet());
        subjectNames = subjects.stream().collect(Collectors.toMap(Subject::getId, Subject::getName));
        String selected = selectedId();
        Scope previousScope = (Scope) scope.getSelectedItem();
        scope.removeAllItems();
        scope.addItem(new Scope(null, "All PDFs"));
        for (StudyMaterial material : materials) {
            if (searchablePdfIds.contains(material.id())) {
                scope.addItem(new Scope(material.id(), material.title()));
            }
        }
        selectScope(previousScope == null ? null : previousScope.id());
        updateLibrarySubjects();
        refreshMatches(selected);
        passageSearch.updateLibrary(searchablePdfIds);
        history.setMaterialIds(pdfIds);
        if (java.util.Collections.list(sourceRows.elements()).stream()
                .anyMatch(source -> !searchablePdfIds.contains(source.materialId()))) {
            clearAnswer();
        }
    }

    private void updateLibrarySubjects() {
        Scope selected = (Scope) librarySubject.getSelectedItem();
        var options = new DefaultComboBoxModel<Scope>();
        options.addElement(new Scope(null, "All subjects"));
        options.addElement(new Scope("", "No subject"));
        subjects.stream().sorted(Comparator.comparing(Subject::getName, String.CASE_INSENSITIVE_ORDER))
                .map(subject -> new Scope(subject.getId(), subject.getName() + " (" + subject.getSubjectCode() + ")"))
                .forEach(options::addElement);
        for (int index = 0; selected != null && index < options.getSize(); index++) {
            if (Objects.equals(options.getElementAt(index).id(), selected.id())) {
                options.setSelectedItem(options.getElementAt(index));
                break;
            }
        }
        librarySubject.setModel(options);
    }

    private void refreshMatches(String selected) {
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
    }

    public void showMatches(List<StudyMaterial> matches) {
        matchingIds = matches.stream().map(StudyMaterial::id).toList();
        showMatches(matches, selectedId());
    }

    public void showPdfSearch(String text, List<StudyMaterial> matches, RetrievalResult evidence) {
        showMatches(matches);
        var pdfEvidence = new RetrievalResult(evidence.passages().stream()
                .filter(passage -> searchablePdfIds.contains(passage.materialId())).toList(), evidence.warnings().stream()
                .filter(warning -> pdfIds.contains(warning.materialId())).toList());
        passageSearch.showSearch(text, pdfEvidence);
        boolean matchingAttachment = matches.stream().anyMatch(material -> !material.isPdf() && matchesSubject(material));
        if (!text.isBlank() && !matchingAttachment && (!pdfEvidence.passages().isEmpty()
                || (materialRows.isEmpty() && !pdfEvidence.warnings().isEmpty()))) {
            tabs.setSelectedIndex(PASSAGES_TAB);
        }
    }

    public void beginImport(Runnable cancel) {
        cancelAction = cancel;
        importCancelled = false;
        importState.setVisible(true);
        showImportProgress(new ImportProgress(ImportStage.COPYING, 0, 0));
    }

    public void showImportProgress(ImportProgress value) {
        if (cancelAction == null || importCancelled) return;
        importProgress.setIndeterminate(value.total() == 0);
        importProgress.setMaximum(Math.max(1, value.total()));
        importProgress.setValue(value.completed());
        importProgress.setString(switch (value.stage()) {
            case COPYING -> "Copying file…";
            case EXTRACTING -> "Extracting " + value.completed() + " of " + value.total() + " pages";
            case SAVING -> "Saving material…";
        });
        cancelImport.setEnabled(value.stage() != ImportStage.SAVING);
    }

    public void finishImport() {
        cancelAction = null;
        importProgress.setIndeterminate(false);
        importState.setVisible(false);
    }

    private void showMatches(List<StudyMaterial> matches, String selected) {
        materialRows.clear();
        materialRows.addAll(matches.stream().filter(this::matchesSubject).toList());
        selectRow(selected);
        if (library.isSelectionEmpty() && !materialRows.isEmpty()) {
            library.setSelectedIndex(0);
        }
        libraryStatus.setText(storageWarning != null ? "Library unavailable. The local file was kept for recovery."
                : materialRows.isEmpty() ? (query.getText().isBlank() && librarySubject.getSelectedIndex() <= 0
                ? "No study materials in your library." : "No matching materials.")
                : materialRows.size() + (materialRows.size() == 1 ? " file" : " files"));
        libraryStatus.setToolTipText(storageWarning);
        updateSelection();
    }

    public void selectMaterial(String id) {
        tabs.setSelectedIndex(0);
        matchingIds = null;
        query.setText("");
        librarySubject.setSelectedIndex(0);
        showMatches(materials, id);
        library.requestFocusInWindow();
    }

    private boolean matchesSubject(StudyMaterial material) {
        Scope selected = (Scope) librarySubject.getSelectedItem();
        if (selected == null || selected.id() == null) return true;
        String subjectId = selected.id().isEmpty() ? null : selected.id();
        return Objects.equals(subjectId, material.subjectId());
    }

    public void showAnswer(StudyAssistant.Answer answer) {
        hasQuestions = answer.questions() != null;
        if (hasQuestions) {
            answerHeading.setText("AI-generated questions");
            questions.showSet(answer.questions(), id -> StudyAssistant.sourceLabel(answer.sources(), id));
            answerCards.show(answerBody, "questions");
        } else {
            answerHeading.setText(answer.generated() ? "AI-generated output" : "Local extracts");
            answerText.setText(answer.text());
            answerText.setCaretPosition(0);
            answerCards.show(answerBody, "text");
        }
        sourceRows.clear();
        sourceRows.addAll(answer.sources());
        if (sourceRows.isEmpty()) {
            passageText.setText("No matching passages.");
        } else {
            sources.setSelectedIndex(0);
        }
        updateQuestionVisibility();
        updateControls();
    }

    /** Presents the exact topic and excerpts to be submitted before any provider call. */
    public void reviewPrepared(StudyAssistant.Prepared prepared) {
        if (prepared.requiresConsent()) {
            JTextArea request = readOnlyText();
            request.setName("materialConsentPreview");
            request.getAccessibleContext().setAccessibleName("Exact study input sent to the AI provider");
            request.setText(prepared.prompt());
            request.setCaretPosition(0);
            JPanel confirmation = transparent(new BorderLayout(0, 8));
            confirmation.add(new JLabel("Review the exact request below before sending it to the AI provider."),
                    BorderLayout.PAGE_START);
            var scroll = ViewStyle.scroll(request);
            scroll.setPreferredSize(new Dimension(560, 300));
            confirmation.add(scroll, BorderLayout.CENTER);
            int selection = JOptionPane.showOptionDialog(this, confirmation, "Review external AI request",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
                    new String[]{"Send request", "Cancel"}, "Cancel");
            if (selection != 0) {
                return;
            }
        }
        actions.runStudy(prepared);
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
        updateControls();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        tabs.setEnabledAt(HISTORY_TAB, !busy);
        history.setBusy(busy);
        passageSearch.setBusy(busy);
        updateControls();
    }

    private JPanel libraryTab() {
        JPanel page = card(new BorderLayout(0, 9));
        JPanel controls = transparent(new BorderLayout(6, 0));
        query.setName("materialSearchQuery");
        query.getAccessibleContext().setAccessibleName("Search study materials");
        query.putClientProperty("JTextField.placeholderText", "Search files or PDF text");
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

        JPanel subjectControls = transparent(new FlowLayout(FlowLayout.LEADING, 5, 0));
        JLabel subjectLabel = ViewStyle.muted("Subject");
        subjectLabel.setLabelFor(librarySubject);
        librarySubject.setName("materialSubjectFilter");
        librarySubject.setPrototypeDisplayValue(new Scope(null, "Select a course subject"));
        librarySubject.setRenderer(new InformativeRenderer() {
            @Override
            String display(Object value) {
                return value == null ? "" : value.toString();
            }
        });
        librarySubject.addActionListener(event -> refreshMatches(selectedId()));
        subjectControls.add(subjectLabel);
        subjectControls.add(librarySubject);
        JPanel toolbar = transparent(new BorderLayout(0, 5));
        toolbar.add(controls, BorderLayout.PAGE_START);
        toolbar.add(subjectControls, BorderLayout.PAGE_END);
        page.add(toolbar, BorderLayout.PAGE_START);

        library.setName("materialList");
        library.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        library.getAccessibleContext().setAccessibleName("Uploaded study materials");
        library.setCellRenderer(new InformativeRenderer() {
            @Override
            String display(Object value) {
                StudyMaterial material = (StudyMaterial) value;
                return material == null ? "" : material.title() + " · " + format(material);
            }
        });
        library.addListSelectionListener(event -> updateSelection());
        materialInfo.setName("materialInfo");
        materialInfo.getAccessibleContext().setAccessibleName("Selected study material details");
        JSplitPane layout = split(JSplitPane.HORIZONTAL_SPLIT, ViewStyle.scroll(library),
                ViewStyle.scroll(materialInfo), 0.37);
        page.add(layout, BorderLayout.CENTER);

        JPanel footer = transparent(new BorderLayout(6, 0));
        libraryStatus.setName("materialLibraryStatus");
        footer.add(libraryStatus, BorderLayout.CENTER);
        JPanel actions = transparent(new FlowLayout(FlowLayout.TRAILING, 5, 0));
        studySelected.setName("materialStudySelected");
        studySelected.addActionListener(event -> {
            if (!studySelected.isEnabled()) return;
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
        importProgress.setName("pdfImportProgress");
        importProgress.setStringPainted(true);
        importState.add(importProgress, BorderLayout.CENTER);
        cancelImport.setName("pdfCancelImport");
        cancelImport.addActionListener(event -> {
            if (cancelAction != null && !importCancelled) {
                importCancelled = true;
                importProgress.setString("Cancelling import…");
                cancelImport.setEnabled(false);
                cancelAction.run();
            }
        });
        importState.add(cancelImport, BorderLayout.LINE_END);
        importState.setVisible(false);
        JPanel bottom = transparent(new BorderLayout(0, 5));
        bottom.add(footer, BorderLayout.PAGE_START);
        bottom.add(importState, BorderLayout.CENTER);
        page.add(bottom, BorderLayout.PAGE_END);
        return page;
    }

    private JPanel assistantTab() {
        JPanel page = card(new BorderLayout(0, 9));
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
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
        mode.addActionListener(event -> updateControls());
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
        scope.addActionListener(event -> {
            requestedSourceWarning = null;
            updateControls();
        });
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
        remote.addActionListener(event -> updateControls());
        preferences.add(remote);
        showAnswers.setName("materialShowAnswers");
        showAnswers.addActionListener(event -> updateQuestionVisibility());
        preferences.add(showAnswers);
        showReferences.setName("materialShowReferences");
        showReferences.addActionListener(event -> updateQuestionVisibility());
        preferences.add(showReferences);
        controls.add(preferences);
        controls.add(questionOptions());
        assistantStatus.setName("materialAssistantStatus");
        controls.add(assistantStatus);
        page.add(controls, BorderLayout.PAGE_START);

        JPanel output = transparent(new BorderLayout(0, 5));
        output.add(answerHeading, BorderLayout.PAGE_START);
        answerText.setName("materialAnswer");
        answerText.getAccessibleContext().setAccessibleName("Study output");
        answerBody.add(ViewStyle.scroll(answerText), "text");
        answerBody.add(questions, "questions");
        output.add(answerBody, BorderLayout.CENTER);
        evidence = transparent(new BorderLayout(0, 5));
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
            if (canOpenPassage(selected)) {
                actions.open(selected.materialId());
            }
        });
        evidence.add(sourceOpen, BorderLayout.PAGE_END);
        page.add(split(JSplitPane.HORIZONTAL_SPLIT, output, evidence, 0.52), BorderLayout.CENTER);
        return page;
    }

    private JPanel questionOptions() {
        questionCount.setName("materialQuestionCount");
        questionCount.getAccessibleContext().setAccessibleName("Question count");
        difficulty.setName("materialDifficulty");
        difficulty.setSelectedItem(Difficulty.STANDARD);
        difficulty.setRenderer(new InformativeRenderer() {
            @Override
            String display(Object value) {
                if (!(value instanceof Difficulty selected)) {
                    return "";
                }
                return switch (selected) {
                    case INTRODUCTORY -> "Introductory";
                    case STANDARD -> "Standard";
                    case ADVANCED -> "Advanced";
                };
            }
        });
        JLabel countLabel = new JLabel("Questions");
        countLabel.setLabelFor(questionCount);
        questionOptions.add(countLabel);
        questionOptions.add(questionCount);
        JLabel difficultyLabel = new JLabel("Difficulty");
        difficultyLabel.setLabelFor(difficulty);
        questionOptions.add(difficultyLabel);
        questionOptions.add(difficulty);
        return questionOptions;
    }

    private void upload() {
        if (busy) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setName("materialPdfChooser");
        chooser.setDialogTitle("Choose study material");
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() || StudyMaterial.supportsFileName(file.getName());
            }

            @Override
            public String getDescription() {
                return "Supported study files";
            }
        });
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path source = chooser.getSelectedFile().toPath();
            if (!StudyMaterial.supportsFileName(source.getFileName().toString())) {
                JOptionPane.showMessageDialog(this, "Choose a supported study file.", "Upload study material",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            MaterialUploadForm.Details details = MaterialUploadForm.show(this, source, subjects);
            if (details != null) {
                actions.upload(source, details.title(), details.description(), details.subjectId());
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
        if (!busy && selected != null && JOptionPane.showConfirmDialog(this, selected.isPdf()
                ? "Delete this PDF and its extracted passages?" : "Delete this file from your study materials?",
                "Delete study material",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
            actions.delete(selected.id());
        }
    }

    private void runAssistant() {
        if (!generate.isEnabled()) {
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
        Scope selectedScope = (Scope) scope.getSelectedItem();
        try {
            if (selectedMode.isQuestionMode()) {
                questionCount.commitEdit();
            }
            var request = new StudyAssistant.Request(question, selectedScope == null ? null : selectedScope.id(),
                    selectedMode, selectedMode.isQuestionMode() || (remoteAvailable && remote.isSelected()),
                    saveHistory.isSelected(), (int) questionCount.getValue(), (Difficulty) difficulty.getSelectedItem());
            actions.prepareStudy(request);
        } catch (ParseException invalid) {
            JOptionPane.showMessageDialog(this, "Enter a valid question count.", "Study Assistant",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException invalid) {
            JOptionPane.showMessageDialog(this, invalid.getMessage(), "Study Assistant", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void reuseRequest(StudyHistoryEntry entry) {
        topic.setText(entry.topic());
        mode.setSelectedItem(Mode.valueOf(entry.mode()));
        selectScope(entry.materialId());
        if (entry.materialId() != null && ((Scope) scope.getSelectedItem()).id() == null) {
            requestedSourceWarning = "The original PDF is unavailable for text search. Check the source before running.";
        }
        if (entry.questions() != null) {
            questionCount.setValue(entry.questions().questions().size());
            difficulty.setSelectedItem(entry.difficulty() == null ? Difficulty.STANDARD
                    : Difficulty.valueOf(entry.difficulty()));
        }
        remote.setSelected(false);
        clearAnswer();
        tabs.setSelectedIndex(ASSISTANT_TAB);
        updateControls();
        topic.requestFocusInWindow();
    }

    private void updateSelection() {
        StudyMaterial selected = library.getSelectedValue();
        boolean originalOnly = selected != null && !selected.isPdf();
        open.setText(originalOnly ? "Show file" : "Open PDF");
        open.setToolTipText(originalOnly ? "Show the stored file in File Explorer" : null);
        if (selected == null) {
            materialInfo.setText("");
        } else {
            String subject = selected.subjectId() == null ? "No subject"
                    : subjectNames.getOrDefault(selected.subjectId(), "No subject");
            String warning = selected.indexWarning();
            materialInfo.setText(selected.title() + "\n\nFile: " + selected.fileName() + "\nType: " + format(selected)
                    + "\nSubject: " + subject
                    + "\nUploaded: " + UPLOADED_DATE.format(selected.uploadedAt())
                    + (warning == null || warning.isBlank() ? "" : "\n\n" + warning)
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
        hasQuestions = false;
        answerHeading.setText("Study output");
        answerText.setText("");
        answerCards.show(answerBody, "text");
        sourceRows.clear();
        passageText.setText("");
        updateQuestionVisibility();
    }

    private void updateQuestionVisibility() {
        questions.setVisibility(showAnswers.isSelected(), showReferences.isSelected());
        if (evidence != null) {
            evidence.setVisible(!hasQuestions || showReferences.isSelected());
        }
    }

    private void updateControls() {
        boolean libraryReady = !busy && storageWarning == null;
        String selectedId = selectedId();
        boolean hasMaterial = libraryReady && selectedId != null;
        boolean canSearchPdf = !searchablePdfIds.isEmpty();
        upload.setEnabled(libraryReady);
        query.setEnabled(libraryReady);
        search.setEnabled(libraryReady);
        librarySubject.setEnabled(libraryReady);
        open.setEnabled(hasMaterial);
        delete.setEnabled(hasMaterial);
        studySelected.setEnabled(hasMaterial && searchablePdfIds.contains(selectedId));
        topic.setEnabled(!busy);
        mode.setEnabled(!busy);
        scope.setEnabled(!busy && canSearchPdf);
        Mode selected = (Mode) mode.getSelectedItem();
        boolean questionMode = selected != null && selected.isQuestionMode();
        questionOptions.setVisible(questionMode);
        questionCount.setEnabled(!busy);
        difficulty.setEnabled(!busy);
        showAnswers.setVisible(questionMode || hasQuestions);
        showReferences.setVisible(questionMode || hasQuestions);
        remote.setVisible(remoteAvailable && !questionMode);
        remote.setEnabled(!busy && remoteAvailable && !questionMode);
        String status = !canSearchPdf ? "Add a searchable PDF to run the Study Assistant." : questionMode
                ? (remoteAvailable ? "Question generation uses the configured AI provider."
                : "Configure an AI provider in Settings to generate questions.")
                : (remote.isSelected() && remoteAvailable ? "Review the excerpts before sending an AI request."
                : "Local study uses extracts from your PDFs.");
        assistantStatus.setText(requestedSourceWarning == null ? status : requestedSourceWarning);
        saveHistory.setEnabled(!busy);
        generate.setEnabled(libraryReady && canSearchPdf && (!questionMode || remoteAvailable));
        sourceOpen.setEnabled(canOpenPassage(sources.getSelectedValue()));
    }

    private boolean canOpenPassage(Passage selected) {
        return !busy && selected != null && searchablePdfIds.contains(selected.materialId());
    }

    private static String format(StudyMaterial material) {
        return material.extension().toUpperCase(Locale.ROOT);
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
            case SHORT_ANSWER -> "Short-answer questions";
            case LONG_ANSWER -> "Long-answer questions";
            case MCQ -> "Multiple-choice questions";
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
        var split = new JSplitPane(direction, first, second);
        split.setContinuousLayout(true);
        split.setResizeWeight(proportion);
        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                if (split.getWidth() > 0 && split.getHeight() > 0) {
                    split.setDividerLocation(proportion);
                    split.removeComponentListener(this);
                }
            }
        });
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
