package com.studyflow.controller;

import com.studyflow.config.StudyFlowContext;
import com.studyflow.controller.StudyFlowController.Records;
import com.studyflow.controller.StudyFlowController.TaskOrder;
import com.studyflow.controller.StudyFlowController.TaskQuery;
import com.studyflow.factory.TaskDraft;
import com.studyflow.model.Assignment;
import com.studyflow.model.Priority;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.model.TaskType;
import com.studyflow.model.Theme;
import com.studyflow.service.TaskFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class StudyFlowControllerTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @TempDir
    Path profile;

    @Test
    void dashboardReflectsRealDataAndRefreshesAfterCompletion() throws Exception {
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            Task due = context.taskManager().quickAdd("Due today", null, TODAY, Priority.MEDIUM);
            Task late = context.taskManager().quickAdd("Overdue", null, TODAY.minusDays(1), Priority.HIGH);
            Task assignment = context.taskManager().create(new TaskDraft("Report", "", null, TODAY.plusDays(7),
                    Priority.MEDIUM, new TaskDraft.AssignmentDetails("Online", null)));
            context.taskManager().quickAdd("Later", null, TODAY.plusDays(8), Priority.LOW);
            StudyFlowController controller = new StudyFlowController(context, Runnable::run, CLOCK);

            var dashboard = load(controller).dashboard();
            assertEquals(TODAY, dashboard.date());
            assertEquals(4, dashboard.total());
            assertEquals(0, dashboard.completed());
            assertEquals(4, dashboard.pending());
            assertEquals(1, dashboard.overdue());
            assertEquals(1, dashboard.upcomingAssignments());
            assertEquals(List.of(due.getId()), ids(dashboard.today()));
            assertEquals(List.of(assignment.getId()), ids(dashboard.upcoming()));
            assertEquals(late.getId(), dashboard.smartPriority().getFirst().getId());

            var refreshed = change(controller, () -> controller.changeStatus(due.getId(), TaskStatus.COMPLETED))
                    .dashboard();
            assertEquals(1, refreshed.completed());
            assertEquals(3, refreshed.pending());
            assertTrue(refreshed.today().isEmpty());
            assertFalse(ids(refreshed.smartPriority()).contains(due.getId()));
        }
    }

    @Test
    void queriesCombineSearchFiltersAndOrderAndDeliverOnTheEdt() throws Exception {
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            Subject subject = context.subjectManager().create("Physics", "PHY", "", List.of("mechanics"));
            var manager = context.taskManager();
            Task zebra = manager.quickAdd("Zebra lab", subject.getId(), TODAY.minusDays(2), Priority.HIGH);
            Task alpha = manager.quickAdd("Alpha lab", subject.getId(), TODAY.minusDays(1), Priority.HIGH);
            manager.quickAdd("Future lab", subject.getId(), TODAY.plusDays(1), Priority.HIGH);
            manager.quickAdd("Unlinked lab", null, TODAY.minusDays(1), Priority.HIGH);
            manager.quickAdd("Lower priority lab", subject.getId(), TODAY.minusDays(1), Priority.LOW);
            StudyFlowController controller = new StudyFlowController(context, Runnable::run, CLOCK);
            TaskQuery query = new TaskQuery("lab phys", new TaskFilter(subject.getId(), Priority.HIGH,
                    TaskStatus.TODO, TaskType.GENERAL, TODAY.minusDays(2), TODAY, true), TaskOrder.TITLE);
            CompletableFuture<List<Task>> result = new CompletableFuture<>();

            controller.query(query, records -> {
                if (SwingUtilities.isEventDispatchThread()) {
                    result.complete(records);
                } else {
                    result.completeExceptionally(new AssertionError("Callbacks belong on the EDT."));
                }
            }, failure(result));

            assertEquals(List.of(alpha.getId(), zebra.getId()), ids(result.get(5, TimeUnit.SECONDS)));
        }
    }

    @Test
    void savesSubtypeEditsAndThemeAcrossRestart() throws Exception {
        String id;
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            StudyFlowController controller = new StudyFlowController(context, Runnable::run, CLOCK);
            controller.createTask(new TaskDraft("Essay", "", null, null, null,
                    new TaskDraft.AssignmentDetails("Online", null)));
            id = context.taskManager().findAll().getFirst().getId();
            TaskDraft edit = new TaskDraft("Updated essay", "Outline first", null, TODAY, Priority.HIGH,
                    new TaskDraft.AssignmentDetails("In person", "draft.txt"));
            change(controller, () -> controller.updateTask(id, edit));
            assertEquals(Theme.DARK, change(controller, () -> controller.changeTheme(Theme.DARK)).theme());
        }
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            Assignment loaded = assertInstanceOf(Assignment.class, context.taskManager().findById(id).orElseThrow());
            assertEquals("Updated essay", loaded.getTitle());
            assertEquals("In person", loaded.getSubmissionType());
            assertEquals("draft.txt", loaded.getAttachmentPath());
            assertEquals(Priority.HIGH, loaded.getPriority());
            assertEquals(Theme.DARK, context.settings().getTheme());
        }
    }

    @Test
    void rejectedWorkspaceEditKeepsFieldsAndMembershipUnchanged() throws IOException {
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            Subject subject = context.subjectManager().create("Physics", "PHY", "", List.of());
            var workspace = context.workspaceManager().create("Term", "Original", List.of(subject.getId()));

            assertThrows(IllegalArgumentException.class, () ->
                    context.workspaceManager().update(workspace.getId(), "Changed", "", List.of("missing")));

            var unchanged = context.workspaceManager().findById(workspace.getId()).orElseThrow();
            assertEquals("Term", unchanged.getName());
            assertEquals("Original", unchanged.getDescription());
            assertEquals(List.of(subject.getId()), unchanged.getSubjectIds());
        }
    }

    @Test
    void damageInOptionalStoresDoesNotChangeCoreSaveOrSearchOutcome() throws Exception {
        Path links = profile.resolve("integrations/links.json");
        Path materials = profile.resolve("materials/materials.json");
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            Files.writeString(links, "{bad links");
            Files.writeString(materials, "{bad materials");
            StudyFlowController controller = new StudyFlowController(context, Runnable::run, CLOCK);
            Records initial = load(controller);
            assertNotNull(initial.integrationsWarning());
            assertNotNull(initial.materialsWarning());
            assertTrue(initial.playlists().isEmpty());

            Records after = change(controller, () -> controller.createTask(new TaskDraft("Graph practice", "",
                    null, TODAY, Priority.HIGH, new TaskDraft.General())));
            assertEquals(1, after.dashboard().total());

            var search = new CompletableFuture<StudyFlowController.SearchData>();
            controller.searchAll("Graph", search::complete, failure(search));
            assertEquals(1, search.get(5, TimeUnit.SECONDS).records().tasks().size());
            assertNotNull(search.join().materialsWarning());
            assertEquals("{bad links", Files.readString(links));
            assertEquals("{bad materials", Files.readString(materials));
        }
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            assertEquals("Graph practice", context.taskManager().findAll().getFirst().getTitle());
        }
    }

    private static List<String> ids(List<Task> tasks) {
        return tasks.stream().map(Task::getId).toList();
    }

    private static Records load(StudyFlowController controller) throws Exception {
        CompletableFuture<Records> result = new CompletableFuture<>();
        controller.load(TaskQuery.initial(), result::complete, failure(result));
        return result.get(5, TimeUnit.SECONDS);
    }

    private static Records change(StudyFlowController controller, Runnable action) throws Exception {
        CompletableFuture<Records> result = new CompletableFuture<>();
        controller.change(action, TaskQuery.initial(), result::complete, failure(result));
        return result.get(5, TimeUnit.SECONDS);
    }

    private static Consumer<String> failure(CompletableFuture<?> result) {
        return message -> result.completeExceptionally(new AssertionError(message));
    }
}
