import com.studyflow.config.AppConfig;
import com.studyflow.config.StudyFlowContext;
import com.studyflow.controller.StudyFlowController;
import com.studyflow.factory.TaskDraft;
import com.studyflow.integrations.GoogleCalendar;
import com.studyflow.model.FocusRecord;
import com.studyflow.model.Priority;
import com.studyflow.model.TaskStatus;
import com.studyflow.model.Theme;
import com.studyflow.view.MainFrame;
import com.studyflow.view.ViewStyle;
import com.studyflow.view.navigation.SidebarPanel;
import com.studyflow.view.planning.CalendarPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/** Captures actual Swing pages with synthetic data in a new profile; never opens the user's profile. */
class VisualSmoke {
    private static final List<String> NAVIGATION = List.of("navDashboard", "navTasks", "navCalendar", "navKanban",
            "navSubjects", "navWorkspaces", "navNotes", "navMaterials", "navAttendance", "navFocus", "navAnalytics",
            "navSettings", "navSearch");
    private static final Map<String, String> PAGE_CONTENT = Map.ofEntries(
            Map.entry("navDashboard", "dashboardPanel"), Map.entry("navTasks", "taskTable"),
            Map.entry("navCalendar", "calendarPanel"), Map.entry("navKanban", "kanbanPanel"),
            Map.entry("navSubjects", "subjectPage"), Map.entry("navWorkspaces", "workspacePage"),
            Map.entry("navNotes", "notePage"), Map.entry("navMaterials", "materialPanel"),
            Map.entry("navAttendance", "attendancePanel"), Map.entry("navFocus", "focusPage"),
            Map.entry("navAnalytics", "analyticsTotals"), Map.entry("navSettings", "themeLight"),
            Map.entry("navSearch", "globalSearchQuery"));
    private static final List<String> VISUAL_SAMPLE =
            List.of("navDashboard", "navCalendar", "navKanban", "navAnalytics");

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2 || GraphicsEnvironment.isHeadless()) {
            throw new IllegalArgumentException("Use a desktop: VisualSmoke <folder> [dashboard|analytics|workflow|integrations|calendar-google|dpi|all]");
        }
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        String mode = args.length == 2 ? args[1] : "";
        List<String> capture = switch (mode) {
            case "all" -> NAVIGATION;
            case "dashboard" -> List.of("navDashboard");
            case "analytics" -> List.of("navAnalytics");
            case "workflow" -> List.of("navAttendance", "navFocus");
            case "integrations" -> List.of("navSettings");
            case "calendar-google" -> List.of("navCalendar");
            case "dpi" -> List.of("navDashboard", "navAnalytics");
            default -> VISUAL_SAMPLE;
        };
        Clock clock = Clock.systemDefaultZone();
        for (Theme theme : Theme.values()) {
            run(theme, output, capture, clock, mode);
        }
    }

    private static void run(Theme theme, Path output, List<String> capture, Clock clock, String mode) throws Exception {
        Path profile = Files.createTempDirectory("studyflow-visual-");
        try (StudyFlowContext context = StudyFlowContext.open(profile, clock)) {
            populate(context, clock, theme);
            ExecutorService worker = Executors.newSingleThreadExecutor();
            AtomicReference<MainFrame> window = new AtomicReference<>();
            try {
                SwingUtilities.invokeAndWait(() -> {
                    ViewStyle.applyTheme(theme);
                    var app = new MainFrame(new AppConfig("StudyFlow", AppConfig.VERSION, profile, theme),
                            new StudyFlowController(context, worker, clock), theme);
                    window.set(app);
                    app.setVisible(true);
                });
                MainFrame frame = window.get();
                // Drain initial load and then its EDT publication, without relying on a fixed render delay.
                worker.submit(() -> { }).get(15, TimeUnit.SECONDS);
                SwingUtilities.invokeAndWait(() -> {
                    Component status = find(frame, "workspaceStatus");
                    if (status instanceof JLabel label && !label.getText().isBlank()) {
                        throw new AssertionError("The fixture did not finish loading: " + label.getText());
                    }
                });
                for (String nav : NAVIGATION) {
                    SwingUtilities.invokeAndWait(() -> navigate(frame, nav));
                }
                List<int[]> sizes = mode.equals("dpi") ? List.of(new int[]{1024, 700})
                        : List.of(new int[]{1366, 768}, new int[]{1440, 900}, new int[]{1920, 1080}, new int[]{1024, 700});
                for (int[] size : sizes) {
                    for (String nav : capture) {
                        SwingUtilities.invokeAndWait(() -> {
                            navigate(frame, nav);
                            if (mode.equals("integrations")) {
                                Component panel = find(frame, "integrationsPanel");
                                if (panel == null) throw new AssertionError("Integration controls are missing");
                                ((JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, panel))
                                        .setSelectedComponent(panel);
                            }
                            if (mode.equals("calendar-google")) {
                                var calendar = (CalendarPanel) find(frame, "calendarPanel");
                                LocalDate today = LocalDate.now(clock);
                                calendar.showGoogleEvents(List.of(
                                        new GoogleCalendar.Event("fixture-class", "Data Structures lecture", today,
                                                today.plusDays(1), "8:45 AM – 9:35 AM"),
                                        new GoogleCalendar.Event("fixture-seminar", "Research methods seminar",
                                                today.plusDays(2), today.plusDays(4), "")),
                                        "Google Calendar up to date.");
                                if (!(find(calendar, "calendarGoogleEvent-fixture-class") instanceof JLabel label)
                                        || !"Data Structures lecture".equals(label.getText())) {
                                    throw new AssertionError("Expected a read-only Google event in the selected day.");
                                }
                            }
                            frame.getContentPane().setPreferredSize(new Dimension(size[0], size[1]));
                            frame.pack();
                            frame.validate();
                        });
                        AtomicReference<BufferedImage> image = new AtomicReference<>();
                        worker.submit(() -> { }).get(15, TimeUnit.SECONDS);
                        SwingUtilities.invokeAndWait(() -> image.set(render(frame, size[0], size[1])));
                        if (image.get().getWidth() != size[0] || image.get().getHeight() != size[1]) {
                            throw new AssertionError("Window manager changed the requested client size.");
                        }
                        String name = nav.substring("nav".length()).toLowerCase(Locale.ROOT)
                                + "-" + theme.name().toLowerCase(Locale.ROOT)
                                + "-" + size[0] + "x" + size[1] + ".png";
                        ImageIO.write(image.get(), "png", output.resolve(name).toFile());
                    }
                }
                System.out.printf("%s: %d pages rendered; %d screenshots; graphics DPI scale %.2f%n",
                        theme, NAVIGATION.size(), sizes.size() * capture.size(),
                        frame.getGraphicsConfiguration().getDefaultTransform().getScaleX());
            } finally {
                if (window.get() != null) {
                    SwingUtilities.invokeAndWait(() -> {
                        window.get().setClosing();
                        window.get().dispose();
                    });
                }
                worker.shutdown();
                if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("The test worker did not stop.");
                }
            }
        }
    }

    private static void populate(StudyFlowContext context, Clock clock, Theme theme) {
        var settings = context.settings();
        settings.setTheme(theme);
        settings.setNotificationsEnabled(false);
        context.saveSettings(settings);
        LocalDate today = LocalDate.now(clock);
        var maths = context.subjectManager().create("Discrete Mathematics", "MA201", "", List.of("graphs"));
        var systems = context.subjectManager().create("Computer Systems", "CS240", "", List.of("lab"));
        var writing = context.subjectManager().create("Academic Writing", "HU110", "", List.of("essay"));
        context.workspaceManager().create("Semester 3", "Coursework and revision", List.of(maths.getId(), systems.getId(),
                writing.getId()));
        context.noteManager().create("Graph theory lecture", "Review tree traversals and connected components.",
                maths.getId(), List.of("graphs"));
        var tasks = context.taskManager();
        tasks.quickAdd("Review graph traversal", maths.getId(), today, Priority.HIGH);
        tasks.quickAdd("Outline the comparison essay", writing.getId(), today, Priority.MEDIUM);
        tasks.create(new TaskDraft("Systems lab report", "Add benchmark results", systems.getId(), today.plusDays(2),
                Priority.HIGH, new TaskDraft.AssignmentDetails("Online", null)));
        tasks.create(new TaskDraft("Discrete mathematics quiz", "", maths.getId(), today.plusDays(4),
                Priority.MEDIUM, new TaskDraft.ExamDetails("Quiz", "Not started")));
        tasks.quickAdd("Read architecture chapter", systems.getId(), today.minusDays(2), Priority.MEDIUM);
        var complete = tasks.quickAdd("Proof worksheet", maths.getId(), today.minusDays(1), Priority.LOW);
        tasks.updateStatus(complete.getId(), TaskStatus.COMPLETED);
        var working = tasks.quickAdd("Practice process scheduling", systems.getId(), today.plusDays(1), Priority.MEDIUM);
        tasks.updateStatus(working.getId(), TaskStatus.IN_PROGRESS);
        for (int index = 0; index < 6; index++) {
            context.attendanceService().add(maths.getId(), today.minusDays(index), index != 2);
            context.attendanceService().add(systems.getId(), today.minusDays(index), index % 2 == 0);
        }
        context.attendanceService().setTarget(maths.getId(), 80);
        for (int index = 0; index < 5; index++) {
            context.focusRepository().insert(new FocusRecord("qa-focus-" + index, null,
                    index % 2 == 0 ? maths.getId() : systems.getId(), clock.instant().minus(index, ChronoUnit.DAYS),
                    Duration.ofMinutes(40), Duration.ofMinutes(15 + index * 4)));
        }
    }

    private static void navigate(MainFrame frame, String name) {
        if (!(find(frame, name) instanceof AbstractButton button) || !button.isEnabled()) {
            throw new AssertionError("Navigation is missing or disabled: " + name);
        }
        button.doClick(0);
        if (button instanceof JToggleButton && !button.isSelected()) {
            throw new AssertionError("Navigation did not select " + name);
        }
        Component page = find(frame, PAGE_CONTENT.get(name));
        if (page == null && name.equals("navAnalytics")) {
            page = find(frame, "analyticsPanel");
        }
        if (name.equals("navSettings") && (page == null || !page.isShowing())) {
            page = find(frame, "integrationsPanel");
        }
        if (page == null || !page.isShowing()) {
            throw new AssertionError("Destination did not display its controls: " + name);
        }
    }

    private static BufferedImage render(MainFrame frame, int width, int height) {
        Container content = frame.getContentPane();
        if (!frame.isShowing() || content.getWidth() < 1 || content.getHeight() < 1) {
            throw new AssertionError("Cannot capture an unrendered window.");
        }
        Dimension nativeSize = content.getSize();
        boolean logicalOnly = nativeSize.width != width || nativeSize.height != height;
        SidebarPanel sidebar = (SidebarPanel) find(frame, "sidebar");
        if (sidebar != null) sidebar.fitWidth(width);
        // Windows may clamp a top-level frame to this monitor's work area. Paint the same live
        // page at the requested logical size without changing the user's display settings.
        if (logicalOnly) {
            System.out.printf("Native client %dx%d; painting logical client %dx%d%n",
                    nativeSize.width, nativeSize.height, width, height);
            content.setSize(width, height);
        }
        layout(content);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = image.createGraphics();
        try {
            content.printAll(canvas);
        } finally {
            canvas.dispose();
            if (logicalOnly) {
                if (sidebar != null) sidebar.fitWidth(frame.getWidth());
                content.setSize(nativeSize);
                layout(content);
            }
        }
        return image;
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) {
            if (child instanceof Container container && container.isVisible()) layout(container);
        }
    }

    private static Component find(Component current, String name) {
        if (name.equals(current.getName())) {
            return current;
        }
        if (current instanceof Container parent) {
            for (Component child : parent.getComponents()) {
                Component found = find(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
