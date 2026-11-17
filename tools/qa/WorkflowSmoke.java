import com.studyflow.config.AppConfig;
import com.studyflow.config.StudyFlowContext;
import com.studyflow.controller.StudyFlowController;
import com.studyflow.model.Assignment;
import com.studyflow.model.TaskStatus;
import com.studyflow.model.TaskType;
import com.studyflow.service.MaterialLibrary;
import com.studyflow.service.StudyAssistant;
import com.studyflow.view.MainFrame;
import com.studyflow.view.ViewStyle;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Desktop workflow probe. All writes go to temporary synthetic profiles. */
class WorkflowSmoke {
    private static final Clock CLOCK = Clock.systemDefaultZone();
    private static final LocalDate TODAY = LocalDate.now(CLOCK);

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("WorkflowSmoke requires a graphical Java desktop.");
        }
        assignmentJourney(Files.createTempDirectory("studyflow-task-qa-"));
        workspaceJourney(Files.createTempDirectory("studyflow-workspace-qa-"));
        trackingJourney(Files.createTempDirectory("studyflow-tracking-qa-"));
        System.out.println("Three separate desktop create/edit/reopen scenarios passed.");
        Path screenshots = args.length == 1 ? Files.createDirectories(Path.of(args[0])) : null;
        studyJourney(Files.createTempDirectory("studyflow-material-qa-"), screenshots);
        System.out.println("PDF upload, exact page search, offline revision, and History reopen passed.");
    }

    private static void assignmentJourney(Path profile) throws Exception {
        String taskId;
        try (Session app = new Session(profile)) {
            String subjectId = app.addSubject("Fixture Algorithms", "QA201");
            app.act(() -> app.click("navTasks"));
            SwingUtilities.invokeLater(() -> app.click("taskAdd"));
            app.act(() -> {
                app.text("taskFormTitle", "Trace a graph search");
                app.combo("taskFormType").setSelectedItem(TaskType.ASSIGNMENT);
                app.combo("taskFormSubject").setSelectedIndex(1);
                app.text("taskFormDeadline", TODAY.toString());
                app.text("taskFormSubmission", "Online");
                app.click("taskFormSave");
            });
            var item = app.context.taskManager().findAll().getFirst();
            require(item instanceof Assignment && subjectId.equals(item.getSubjectId()), "Assignment and subject");
            taskId = item.getId();
            app.act(() -> {
                app.click("navDashboard");
                app.visible("dashboardComplete");
                app.click("navCalendar");
                app.visible("calendarEvent-" + taskId);
                app.click("navKanban");
                app.visible("kanbanColumn-TODO");
                require(textPresent(app.frame, "Trace a graph search"), "Kanban task was present");
                app.click("navSearch");
                app.text("globalSearchQuery", "Trace a graph search");
                app.click("globalSearchSubmit");
            });
            app.act(() -> {
                require(((JList<?>) app.visible("globalSearchResults")).getModel().getSize() == 1, "Search found task");
                app.click("globalSearchOpen");
            });
            app.act(() -> {
                app.visible("taskTable");
                app.click("navDashboard");
                app.click("dashboardComplete");
            });
            require(app.context.taskManager().findById(taskId).orElseThrow().getStatus() == TaskStatus.COMPLETED,
                    "Dashboard completion persisted");
            app.act(() -> {
                app.click("navAnalytics");
                Component old = find(app.frame, "analyticsTotals");
                if (old instanceof JTextArea area) {
                    require(area.getText().contains("1 / 1"), "Analytics reflected completion");
                } else {
                    app.visible("analyticsPanel");
                    require(textPresent(app.frame, "1 of 1"), "Analytics reflected completion");
                }
            });
        }
        try (Session reopened = new Session(profile)) {
            require(reopened.context.taskManager().findById(taskId).orElseThrow().getStatus()
                    == TaskStatus.COMPLETED, "Assignment reopened completed");
        }
    }

    private static void workspaceJourney(Path profile) throws Exception {
        try (Session app = new Session(profile)) {
            app.addSubject("Fixture Studio", "QA305");
            app.act(() -> {
                app.click("navWorkspaces");
                app.click("workspaceAdd");
                app.text("workspaceName", "QA Spring studio");
                Container membership = (Container) app.visible("workspaceSubjects");
                JCheckBox choice = firstCheckBox(membership);
                require(choice != null, "Workspace subject choice found");
                choice.doClick(0);
                app.click("workspaceSave");
            });
            app.act(() -> {
                app.click("navNotes");
                app.click("noteAdd");
                app.text("noteTitle", "Harbor sketches");
                app.text("noteContent", "Draft the elevation before the group review.");
                app.combo("noteSubject").setSelectedIndex(1);
                app.click("noteSave");
            });
            app.act(() -> {
                app.click("navSearch");
                app.text("globalSearchQuery", "Harbor sketches");
                app.click("globalSearchSubmit");
            });
            app.act(() -> app.click("globalSearchOpen"));
            app.act(() -> {
                app.visible("notePage");
                app.click("noteEdit");
                app.text("noteContent", "Draft the elevation and add a scale.");
                app.click("noteSave");
            });
        }
        try (Session reopened = new Session(profile)) {
            var note = reopened.context.noteManager().findAll().getFirst();
            var workspace = reopened.context.workspaceManager().findAll().getFirst();
            require("Harbor sketches".equals(note.getTitle()) && note.getContent().contains("add a scale")
                    && workspace.getSubjectIds().equals(List.of(note.getSubjectId())), "Workspace and note reopened");
        }
    }

    private static void trackingJourney(Path profile) throws Exception {
        String subject;
        try (Session app = new Session(profile)) {
            subject = app.addSubject("Fixture seminar", "QA105");
            app.act(() -> {
                app.click("navAttendance");
                app.text("attendanceDate", TODAY.toString());
                app.click("attendanceAddAbsent");
            });
            var service = app.context.attendanceService();
            app.act(() -> ((JTable) app.visible("attendanceHistory")).setRowSelectionInterval(0, 0));
            SwingUtilities.invokeLater(() -> app.click("attendanceCorrect"));
            app.act(() -> {
                app.combo("attendanceCorrectionOutcome").setSelectedIndex(0);
                app.text("attendanceCorrectionDate", "not a date");
                app.submitDialog("attendanceCorrectionDate");
                require(((JLabel) app.visible("dialogValidation")).getText().contains("YYYY-MM-DD"),
                        "Bad correction stayed open for fixing");
                app.text("attendanceCorrectionDate", TODAY.toString());
                app.submitDialog("attendanceCorrectionDate");
            });
            SwingUtilities.invokeLater(() -> app.click("attendanceTargetButton"));
            app.act(() -> {
                ((JSpinner) app.visible("attendanceTargetValue")).setValue(80);
                app.submitDialog("attendanceTargetValue");
            });
            var corrected = service.snapshot().subjects().getFirst();
            require(corrected.target() == 80 && service.snapshot().attended() == 1, "Attendance corrected and target set");
            app.act(() -> {
                app.click("navFocus");
                app.combo("focusSubject").setSelectedIndex(1);
                app.click("focusStart");
                app.click("focusPause");
                app.click("focusStart");
                app.click("focusFinish");
            });
            var focus = app.context.focusRepository().load();
            require(focus.size() == 1 && subject.equals(focus.getFirst().subjectId()), "Focus saved once");
            app.act(() -> app.click("navAnalytics"));
            app.act(() -> {
                Component older = find(app.frame, "analyticsTotals");
                if (older instanceof JTextArea area) {
                    require(area.getText().contains("Focus sessions      1"), "Analytics reflected focus");
                } else {
                    require(textPresent(app.frame, "1 session"), "Analytics reflected focus");
                }
            });
        }
        try (Session reopened = new Session(profile)) {
            require(reopened.context.attendanceService().snapshot().attended() == 1
                    && reopened.context.attendanceService().snapshot().subjects().getFirst().target() == 80,
                    "Attendance and target reopened");
            require(reopened.context.focusRepository().load().size() == 1, "Focus reopened");
        }
    }

    private static void studyJourney(Path profile, Path screenshots) throws Exception {
        Path original = Files.createTempDirectory("studyflow-pdf-source-").resolve("algorithms-handout.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (String line : List.of("Oscillations, potential and kinetic energy.",
                    "Graph traversals use adjacent vertices. Breadth first search uses a queue.")) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(40, 740);
                    content.showText(line);
                    content.endText();
                }
            }
            doc.save(original.toFile());
        }
        try (Session app = new Session(profile)) {
            app.act(() -> app.click("navMaterials"));
            SwingUtilities.invokeLater(() -> app.click("materialUpload"));
            app.act(() -> {
                var chooser = (JFileChooser) app.visible("materialPdfChooser");
                chooser.setSelectedFile(original.toFile());
                chooser.approveSelection();
            });
            app.act(() -> {
                app.text("materialUploadTitle", "Algorithms handout");
                app.submitDialog("materialUploadTitle");
            });
            require(app.context.materialLibrary().findAll().size() == 1, "File imported in the desktop");
            app.act(() -> {
                app.text("materialSearchQuery", "graph traversals");
                app.click("materialSearch");
            });
            app.act(() -> {
                var results = (JList<?>) app.visible("pdfPassageMatches");
                require(results.getSelectedValue() instanceof MaterialLibrary.Passage passage && passage.page() == 2,
                        "Search selected the controlled PDF's page 2");
                require(((JTextArea) app.visible("pdfPassagePreview")).getText().contains("Breadth first"),
                        "Preview shows the original matching text");
                require(app.visible("pdfPassageOpen").isEnabled(), "Source action is available");
            });
            if (screenshots != null) app.capture(screenshots.resolve("pdf-text-matches.png"));
            app.act(() -> {
                ((JTabbedPane) app.visible("materialTabs")).setSelectedIndex(1);
                app.text("materialTopic", "graph traversals");
                app.combo("materialMode").setSelectedItem(StudyAssistant.Mode.REVISION_NOTES);
                app.click("materialGenerate");
            });
            app.act(() -> { }); // Prepare and local execute are separate worker jobs.
            app.act(() -> {
                var sources = (JList<?>) app.visible("materialSources");
                require(sources.getModel().getElementAt(0) instanceof MaterialLibrary.Passage passage
                        && passage.page() == 2, "Assistant preserved the actual source page");
                require(!((JTextArea) app.visible("materialAnswer")).getText().isBlank(), "Local revision output");
            });
            if (screenshots != null) app.capture(screenshots.resolve("study-assistant-local.png"));
        }
        try (Session reopened = new Session(profile)) {
            reopened.act(() -> {
                reopened.click("navMaterials");
                ((JTabbedPane) reopened.visible("materialTabs")).setSelectedIndex(2);
            });
            reopened.act(() -> require(((JList<?>) reopened.visible("materialHistoryList")).getModel().getSize() == 1,
                    "Saved assistant history reopened"));
            if (screenshots != null) reopened.capture(screenshots.resolve("study-history-reopened.png"));
        }
    }

    private static void require(boolean condition, String action) {
        if (!condition) throw new AssertionError(action);
    }

    private static Component find(Component view, String name) {
        if (name.equals(view.getName()) && view.isShowing()) return view;
        if (view instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = find(child, name);
                if (found != null) return found;
            }
        }
        if (view instanceof Window window) {
            for (Window child : window.getOwnedWindows()) {
                Component found = find(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean textPresent(Component view, String text) {
        if (!view.isShowing()) return false;
        if (view instanceof JLabel label && label.getText() != null && label.getText().contains(text)) return true;
        if (view instanceof AbstractButton button && button.getText().contains(text)) return true;
        if (view instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (textPresent(child, text)) return true;
            }
        }
        return false;
    }

    private static JCheckBox firstCheckBox(Container panel) {
        for (Component child : panel.getComponents()) {
            if (child instanceof JCheckBox choice) return choice;
            if (child instanceof Container container) {
                JCheckBox choice = firstCheckBox(container);
                if (choice != null) return choice;
            }
        }
        return null;
    }

    private static final class Session implements AutoCloseable {
        private final StudyFlowContext context;
        private final ExecutorService worker = Executors.newSingleThreadExecutor();
        private final StudyFlowController controller;
        private MainFrame frame;

        Session(Path profile) throws Exception {
            context = StudyFlowContext.open(profile, CLOCK);
            var settings = context.settings();
            settings.setNotificationsEnabled(false);
            context.saveSettings(settings);
            controller = new StudyFlowController(context, worker, CLOCK);
            act(() -> {
                ViewStyle.applyTheme(settings.getTheme());
                frame = new MainFrame(new AppConfig("StudyFlow", AppConfig.VERSION, profile, settings.getTheme()),
                        controller, settings.getTheme());
                frame.setVisible(true);
            });
        }

        void act(Runnable action) throws Exception {
            SwingUtilities.invokeAndWait(action);
            // A modal's handler resumes only after the secondary event loop unwinds.
            SwingUtilities.invokeAndWait(() -> { });
            worker.submit(() -> { }).get(15, TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(() -> { });
        }

        String addSubject(String name, String code) throws Exception {
            act(() -> {
                click("navSubjects");
                click("subjectAdd");
                text("subjectName", name);
                text("subjectCode", code);
                click("subjectSave");
            });
            return context.subjectManager().findAll().getFirst().getId();
        }

        Component visible(String name) {
            Component found = find(frame, name);
            if (found == null) throw new AssertionError("Control was not visible: " + name);
            return found;
        }

        void click(String name) {
            var button = (AbstractButton) visible(name);
            require(button.isEnabled(), name + " was disabled");
            button.doClick(0);
        }

        void text(String name, String value) {
            ((JTextComponent) visible(name)).setText(value);
        }

        JComboBox<?> combo(String name) { return (JComboBox<?>) visible(name); }

        void submitDialog(String field) {
            var dialog = (JDialog) SwingUtilities.getWindowAncestor(visible(field));
            dialog.getRootPane().getDefaultButton().doClick(0);
        }

        void capture(Path destination) throws Exception {
            var image = new java.util.concurrent.atomic.AtomicReference<BufferedImage>();
            act(() -> {
                var page = frame.getContentPane();
                var bitmap = new BufferedImage(page.getWidth(), page.getHeight(), BufferedImage.TYPE_INT_RGB);
                var g = bitmap.createGraphics();
                try { page.printAll(g); } finally { g.dispose(); }
                image.set(bitmap);
            });
            ImageIO.write(image.get(), "png", destination.toFile());
        }

        @Override public void close() throws Exception {
            SwingUtilities.invokeAndWait(frame::setClosing);
            worker.shutdown();
            require(worker.awaitTermination(15, TimeUnit.SECONDS), "Application worker shut down");
            SwingUtilities.invokeAndWait(frame::dispose);
            context.close();
        }
    }
}
