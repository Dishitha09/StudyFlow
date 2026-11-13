package com.studyflow.service;

import com.studyflow.factory.TaskDraft;
import com.studyflow.interfaces.Storage;
import com.studyflow.model.Assignment;
import com.studyflow.model.Exam;
import com.studyflow.model.GeneralTask;
import com.studyflow.model.Priority;
import com.studyflow.model.Recurrence;
import com.studyflow.model.StudySession;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.model.TaskType;
import com.studyflow.repository.MemoryStorage;
import com.studyflow.strategy.SortByCreationDate;
import com.studyflow.strategy.SortByDeadline;
import com.studyflow.strategy.SortByPriority;
import com.studyflow.strategy.SortByTitle;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class TaskManagerTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private final Subject physics = new Subject("physics", "Applied Physics", "PHY",
            "Mechanics", List.of("laboratory"));
    private final Subject maths = new Subject("maths", "Mathematics", "MAT", "", List.of());
    private final Map<String, Subject> subjects = Map.of(physics.getId(), physics, maths.getId(), maths);
    private final TaskManager manager = newManager(new MemoryStorage<>(List.of()));

    @Test
    void quickAddUsesTheDefaultAndSupportsEditingStatusAndDeletion() {
        Task created = manager.quickAdd("  Write report  ", physics.getId(), TODAY, null);
        assertInstanceOf(GeneralTask.class, created);
        assertEquals("Write report", created.getTitle());
        assertEquals(Priority.MEDIUM, created.getPriority());
        assertEquals(TODAY, created.getCreatedDate());
        assertEquals(TaskStatus.TODO, created.getStatus());
        assertEquals(created.getId(), manager.findById(created.getId()).orElseThrow().getId());

        manager.update(created.getId(), task -> task.setDescription("Include results"));
        manager.setPriority(created.getId(), Priority.HIGH);
        manager.assignSubject(created.getId(), maths.getId());
        manager.updateStatus(created.getId(), TaskStatus.IN_PROGRESS);
        manager.markCompleted(created.getId());

        Task edited = manager.findById(created.getId()).orElseThrow();
        assertEquals("Include results", edited.getDescription());
        assertEquals(Priority.HIGH, edited.getPriority());
        assertEquals(maths.getId(), edited.getSubjectId());
        assertEquals(TaskStatus.COMPLETED, edited.getStatus());
        assertEquals(List.of("Write report"), titles(manager.findBySubject(maths.getId())));
        assertTrue(manager.findBySubject(physics.getId()).isEmpty());
        manager.delete(created.getId());
        assertTrue(manager.findById(created.getId()).isEmpty());
        assertTrue(manager.findAll().isEmpty());
        assertThrows(TaskNotFoundException.class, () -> manager.delete(created.getId()));
    }

    @Test
    void factoryCreatesTheRequestedTaskWithSubtypeFields() {
        Assignment assignment = assertInstanceOf(Assignment.class, manager.create(new TaskDraft(
                "Submit lab", "Use the rubric", null, TODAY, Priority.HIGH,
                new TaskDraft.AssignmentDetails("Portal", "lab.pdf"))));
        Exam exam = assertInstanceOf(Exam.class, manager.create(new TaskDraft("Midterm", "", null,
                null, Priority.MEDIUM, new TaskDraft.ExamDetails("Written", "Prepared"))));
        StudySession study = assertInstanceOf(StudySession.class, manager.create(new TaskDraft(
                "Read notes", "", null, null, Priority.LOW,
                new TaskDraft.StudySessionDetails(Duration.ofMinutes(30), null))));
        GeneralTask general = assertInstanceOf(GeneralTask.class, manager.create(draft("Plan week")));

        assertEquals("Portal", assignment.getSubmissionType());
        assertEquals("lab.pdf", assignment.getAttachmentPath());
        assertEquals("Written", exam.getExamType());
        assertEquals("Prepared", exam.getPreparationStatus());
        assertEquals(Duration.ofMinutes(30), study.getPlannedDuration());
        assertNull(study.getActualDuration());
        assertEquals(TaskType.GENERAL, general.getType());
        assertEquals(4, manager.findAll().stream().map(Task::getId).distinct().count());
    }

    @Test
    void searchUsesLiveSubjectNamesAndTagsAndMatchesTokensAcrossFields() {
        Task task = manager.quickAdd("Optics homework", physics.getId(), TODAY, Priority.MEDIUM);
        manager.update(task.getId(), edited -> edited.setDescription("Revise refraction"));
        manager.quickAdd("Linear algebra", maths.getId(), null, Priority.LOW);

        assertEquals(List.of("Optics homework"), titles(manager.search("  OPT   refra PHYS lab  ")));
        assertTrue(manager.search("optics nonexistent").isEmpty());
        assertTrue(manager.search("  ").isEmpty());
        physics.setName("Quantum Studies");
        assertTrue(manager.search("physics").isEmpty());
        assertEquals(List.of("Optics homework"), titles(manager.search("quantum")));
    }

    @Test
    void combinesFiltersAndHonorsInclusiveDeadlineBounds() {
        manager.quickAdd("At start", physics.getId(), TODAY.minusDays(3), Priority.HIGH);
        manager.quickAdd("At end", physics.getId(), TODAY.minusDays(1), Priority.HIGH);
        manager.quickAdd("Future", physics.getId(), TODAY, Priority.HIGH);
        manager.quickAdd("Wrong subject", maths.getId(), TODAY.minusDays(1), Priority.HIGH);
        manager.quickAdd("Wrong priority", physics.getId(), TODAY.minusDays(1), Priority.LOW);
        manager.quickAdd("Undated", physics.getId(), null, Priority.HIGH);
        Task completed = manager.quickAdd("Completed", physics.getId(), TODAY.minusDays(1), Priority.HIGH);
        manager.markCompleted(completed.getId());
        manager.create(new TaskDraft("Different type", "", physics.getId(), TODAY.minusDays(1),
                Priority.HIGH, new TaskDraft.AssignmentDetails("Portal", null)));

        TaskFilter overdue = new TaskFilter(physics.getId(), Priority.HIGH, TaskStatus.TODO,
                TaskType.GENERAL, TODAY.minusDays(3), TODAY.minusDays(1), true);
        assertEquals(List.of("At start", "At end"), titles(manager.filter(overdue)));
        assertThrows(IllegalArgumentException.class, () -> manager.filter(new TaskFilter(null,
                null, null, null, TODAY.plusDays(1), TODAY, null)));
    }

    @Test
    void sortsThroughAllStrategiesWithoutChangingStoredOrder() {
        manager.add(task("z", "Zulu", TODAY.minusDays(1), TODAY.plusDays(3), Priority.LOW));
        manager.add(task("a", "alpha", TODAY.minusDays(2), null, Priority.HIGH));
        manager.add(task("b", "Bravo", TODAY, TODAY, Priority.MEDIUM));

        assertEquals(List.of("Bravo", "Zulu", "alpha"), titles(manager.sort(new SortByDeadline())));
        assertEquals(List.of("alpha", "Bravo", "Zulu"), titles(manager.sort(new SortByPriority())));
        assertEquals(List.of("alpha", "Bravo", "Zulu"), titles(manager.sort(new SortByTitle())));
        assertEquals(List.of("alpha", "Zulu", "Bravo"), titles(manager.sort(new SortByCreationDate())));
        assertEquals(List.of("Zulu", "alpha", "Bravo"), titles(manager.findAll()));
    }

    @Test
    void deadlineViewsAndSmartPriorityRespectUrgencyAndCompletion() {
        Task overdue = manager.quickAdd("Overdue", null, TODAY.minusDays(3), Priority.LOW);
        manager.quickAdd("Today low", null, TODAY, Priority.LOW);
        manager.quickAdd("Today high", null, TODAY, Priority.HIGH);
        manager.quickAdd("Tomorrow", null, TODAY.plusDays(1), Priority.LOW);
        manager.quickAdd("Day seven", null, TODAY.plusDays(7), Priority.HIGH);
        manager.quickAdd("Day eight", null, TODAY.plusDays(8), Priority.HIGH);
        manager.quickAdd("Undated", null, null, Priority.HIGH);
        Task finished = manager.quickAdd("Finished", null, TODAY, Priority.HIGH);
        manager.markCompleted(finished.getId());

        assertEquals(2, manager.today().size());
        assertEquals(List.of("Today high", "Today low"), titles(manager.today()).stream().sorted().toList());
        assertEquals(List.of("Day seven", "Tomorrow"), titles(manager.upcoming()).stream().sorted().toList());
        assertEquals(List.of("Overdue", "Today high", "Today low", "Day seven", "Tomorrow", "Day eight", "Undated"),
                titles(manager.smartPriority()));
        assertTrue(manager.findById(overdue.getId()).orElseThrow().isOverdue(TODAY));
    }

    @Test
    void kanbanIncludesEachStatusGroupEvenWhenEmpty() {
        Task todo = manager.quickAdd("To do", null, null, null);
        Task progress = manager.quickAdd("Started", null, null, null);
        manager.updateStatus(progress.getId(), TaskStatus.IN_PROGRESS);
        assertEquals(List.of("To do"), titles(manager.kanban().get(TaskStatus.TODO)));
        assertEquals(List.of("Started"), titles(manager.kanban().get(TaskStatus.IN_PROGRESS)));
        assertTrue(manager.kanban().get(TaskStatus.COMPLETED).isEmpty());
        manager.markCompleted(todo.getId());
        assertEquals(List.of("To do"), titles(manager.kanban().get(TaskStatus.COMPLETED)));
    }

    @ParameterizedTest
    @CsvSource({
            "DAILY,2026-11-12,2026-11-13",
            "WEEKLY,2026-11-01,2026-11-08",
            "MONTHLY,2026-01-31,2026-02-28"
    })
    void recurrenceAdvancesFromTheDueDateOnlyOnce(Recurrence recurrence, LocalDate due, LocalDate next) {
        Storage<List<Task>> storage = new MemoryStorage<>(List.of());
        TaskManager recurring = newManager(storage);
        Task first = recurring.quickAdd("Review", null, due, Priority.HIGH);
        recurring.setRecurrence(first.getId(), recurrence);
        recurring.markCompleted(first.getId());
        assertEquals(2, recurring.findAll().size());
        Task successor = recurring.findAll().stream().filter(task -> !task.getId().equals(first.getId()))
                .findFirst().orElseThrow();
        assertEquals(next, successor.getDueDate());
        assertEquals(TODAY, successor.getCreatedDate());
        assertEquals(TaskStatus.TODO, successor.getStatus());
        assertEquals(Priority.HIGH, successor.getPriority());

        TaskManager reopenedStore = newManager(storage);
        reopenedStore.delete(successor.getId());
        reopenedStore.updateStatus(first.getId(), TaskStatus.TODO);
        reopenedStore.markCompleted(first.getId());
        assertEquals(1, reopenedStore.findAll().size());
    }

    @Test
    void recurringSuccessorGetsFreshSubtasksAndResetsCompletionSpecificFields() {
        Task initial = manager.create(new TaskDraft("Practice", "", physics.getId(), TODAY,
                Priority.HIGH, new TaskDraft.StudySessionDetails(Duration.ofMinutes(20), Duration.ofMinutes(25))));
        Task child = manager.createSubtask(initial.getId(), new TaskDraft("Self-test", "", physics.getId(), null,
                Priority.LOW, new TaskDraft.ExamDetails("Oral", "Prepared")));
        manager.updateSubtaskStatus(initial.getId(), child.getId(), TaskStatus.COMPLETED);
        manager.setRecurrence(initial.getId(), Recurrence.DAILY);
        manager.markCompleted(initial.getId());

        StudySession next = assertInstanceOf(StudySession.class, manager.findAll().stream()
                .filter(task -> !task.getId().equals(initial.getId())).findFirst().orElseThrow());
        assertEquals(Duration.ofMinutes(20), next.getPlannedDuration());
        assertNull(next.getActualDuration());
        Exam nextChild = assertInstanceOf(Exam.class, manager.findSubtasks(next.getId()).getFirst());
        assertNotEquals(child.getId(), nextChild.getId());
        assertEquals(TaskStatus.TODO, nextChild.getStatus());
        assertEquals("Oral", nextChild.getExamType());
        assertEquals(Exam.DEFAULT_PREPARATION_STATUS, nextChild.getPreparationStatus());
    }

    @Test
    void managesNestedSubtasksThroughTheirRoot() {
        Task root = manager.quickAdd("Report", null, null, null);
        Task child = manager.createSubtask(root.getId(), draft("Research"));
        Task nested = manager.createSubtask(root.getId(), child.getId(), draft("Read sources"));
        manager.assignSubtaskSubject(root.getId(), nested.getId(), physics.getId());
        manager.updateSubtaskStatus(root.getId(), nested.getId(), TaskStatus.COMPLETED);
        Task stored = manager.findSubtasks(root.getId(), child.getId()).getFirst();

        assertEquals(nested.getId(), stored.getId());
        assertEquals(TaskStatus.COMPLETED, stored.getStatus());
        assertEquals(physics.getId(), stored.getSubjectId());
        assertEquals(List.of("Read sources"), titles(manager.findBySubject(physics.getId())));
        assertTrue(manager.findById(nested.getId()).isEmpty());
        assertTrue(manager.search("Read sources").isEmpty());
        manager.removeSubtask(root.getId(), nested.getId());
        assertTrue(manager.findSubtasks(root.getId(), child.getId()).isEmpty());
        assertTrue(manager.findBySubject(physics.getId()).isEmpty());
    }

    @Test
    void rejectsInvalidMutationsAndDuplicateIdsAcrossTaskTrees() {
        Task valid = manager.quickAdd("Valid", physics.getId(), null, null);
        Task child = manager.createSubtask(valid.getId(), draft("Owned child"));
        assertThrows(DuplicateTaskException.class, () -> manager.add(task(valid.getId(), "Duplicate",
                TODAY, null, Priority.HIGH)));
        assertThrows(DuplicateTaskException.class, () -> manager.add(task(child.getId(), "Duplicate child",
                TODAY, null, Priority.HIGH)));
        assertThrows(IllegalArgumentException.class, () -> manager.quickAdd(" ", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> manager.assignSubject(valid.getId(), "missing"));
        assertThrows(IllegalArgumentException.class, () -> manager.setPriority(valid.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> manager.setRecurrence(valid.getId(), Recurrence.DAILY));
        manager.markCompleted(valid.getId());
        assertThrows(IllegalStateException.class, () -> manager.updateStatus(valid.getId(), TaskStatus.IN_PROGRESS));
        assertEquals(1, manager.findAll().size());
        assertEquals(physics.getId(), manager.findById(valid.getId()).orElseThrow().getSubjectId());
        assertEquals(TaskStatus.COMPLETED, manager.findById(valid.getId()).orElseThrow().getStatus());
    }

    @Test
    void detachedReadsAndFailedStorageWriteCannotChangeCommittedState() {
        class FailingStorage implements Storage<List<Task>> {
            private List<Task> snapshot = List.of();
            private boolean failWrites;

            @Override
            public void save(List<Task> data) {
                if (failWrites) {
                    throw new IllegalStateException("Write failed");
                }
                snapshot = data;
            }

            @Override
            public List<Task> load() {
                return snapshot;
            }
        }

        FailingStorage storage = new FailingStorage();
        TaskManager checked = newManager(storage);
        Task created = checked.quickAdd("Original", null, TODAY, null);
        checked.setRecurrence(created.getId(), Recurrence.DAILY);
        created.setTitle("Outside edit");
        checked.findAll().getFirst().setTitle("Another outside edit");
        storage.failWrites = true;

        assertThrows(IllegalStateException.class, () -> checked.markCompleted(created.getId()));
        Task retained = checked.findById(created.getId()).orElseThrow();
        assertEquals("Original", retained.getTitle());
        assertEquals(TaskStatus.TODO, retained.getStatus());
        assertEquals(1, checked.findAll().size());

        storage.failWrites = false;
        checked.markCompleted(created.getId());
        assertEquals(2, checked.findAll().size());
    }

    private TaskManager newManager(Storage<List<Task>> storage) {
        return new TaskManager(CLOCK, Priority.MEDIUM, id -> Optional.ofNullable(subjects.get(id)), storage);
    }

    private static TaskDraft draft(String title) {
        return new TaskDraft(title, "", null, null, null, new TaskDraft.General());
    }

    private static Task task(String id, String title, LocalDate createdDate, LocalDate dueDate, Priority priority) {
        return new GeneralTask(id, title, "", null, createdDate, dueDate, priority, TaskStatus.TODO);
    }

    private static List<String> titles(List<Task> tasks) {
        return tasks.stream().map(Task::getTitle).toList();
    }
}
