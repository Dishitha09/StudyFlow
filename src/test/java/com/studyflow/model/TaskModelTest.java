package com.studyflow.model;

import com.studyflow.interfaces.Prioritizable;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskModelTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    @Test
    void taskSubtypesDispatchTheirTypeAndPreserveTheirOwnFields() {
        Assignment assignment = new Assignment("a", "Submit lab", "", "physics", TODAY,
                TODAY.plusDays(2), Priority.HIGH, TaskStatus.TODO, "Portal", "lab.pdf");
        Exam exam = new Exam("e", "Physics midterm", "Revise optics", "physics", TODAY,
                null, Priority.MEDIUM, TaskStatus.IN_PROGRESS, "Written", "Started");
        StudySession session = new StudySession("s", "Practice optics", "", null, TODAY,
                null, Priority.LOW, TaskStatus.TODO, Duration.ofMinutes(30), Duration.ZERO);
        GeneralTask general = task("g", "Plan the week");

        List<Task> tasks = List.of(assignment, exam, session, general);
        assertEquals(List.of(TaskType.ASSIGNMENT, TaskType.EXAM, TaskType.STUDY_SESSION, TaskType.GENERAL),
                tasks.stream().map(Task::getType).toList());
        assertEquals("Portal", assignment.getSubmissionType());
        assertEquals("lab.pdf", assignment.getAttachmentPath());
        assertEquals("Written", exam.getExamType());
        assertEquals("Started", exam.getPreparationStatus());
        assertEquals(Duration.ofMinutes(30), session.getPlannedDuration());
        assertEquals(Duration.ZERO, session.getActualDuration());
        assertFalse(session.exceededPlan());
        session.setActualDuration(Duration.ofMinutes(31));
        assertTrue(session.exceededPlan());

        Prioritizable ranked = assignment;
        assertEquals(Priority.HIGH, ranked.getPriority());
        assertTrue(exam.matchesSearch("OPTICS"));
        assertEquals("physics", assignment.getSubjectId());
        assertEquals(TaskStatus.TODO, general.getStatus());
        assertNull(general.getSubjectId());
    }

    @Test
    void polymorphicCopyKeepsSubtypeDataWithoutSharingMutableFields() {
        Task original = new Assignment("a", "Submit lab", "", null, TODAY,
                null, Priority.HIGH, TaskStatus.TODO, "Portal", "lab.pdf");
        original.addSubtask(task("child", "Finish analysis"));

        Assignment copy = assertInstanceOf(Assignment.class, original.copy());
        assertEquals(original.getId(), copy.getId());
        assertEquals("lab.pdf", copy.getAttachmentPath());
        copy.setAttachmentPath("revised.pdf");
        copy.editSubtask("child", Task::markCompleted);

        assertEquals("lab.pdf", ((Assignment) original).getAttachmentPath());
        assertEquals(TaskStatus.TODO, original.getSubtasks().getFirst().getStatus());
    }

    @Test
    void reportsOverdueAndDaysRemainingUsingTheSuppliedDate() {
        Task task = task("a", "Lab");
        assertTrue(task.getDaysRemaining(TODAY).isEmpty());
        assertFalse(task.isOverdue(TODAY));

        task.setDueDate(TODAY.plusDays(2));
        assertEquals(2, task.getDaysRemaining(TODAY).orElseThrow());
        assertFalse(task.isOverdue(TODAY));
        task.setDueDate(TODAY);
        assertEquals(0, task.getDaysRemaining(TODAY).orElseThrow());
        assertFalse(task.isOverdue(TODAY));
        task.setDueDate(TODAY.minusDays(2));
        assertEquals(-2, task.getDaysRemaining(TODAY).orElseThrow());
        assertTrue(task.isOverdue(TODAY));
        task.markCompleted();
        assertFalse(task.isOverdue(TODAY));
        assertEquals(-2, task.getDaysRemaining(TODAY).orElseThrow());
    }

    @Test
    void supportsReopenAndRejectsMovingStraightFromCompletedToInProgress() {
        Task task = task("a", "Lab");
        task.updateStatus(TaskStatus.IN_PROGRESS);
        task.updateStatus(TaskStatus.TODO);
        task.markCompleted();
        task.markCompleted();
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertThrows(IllegalStateException.class, () -> task.updateStatus(TaskStatus.IN_PROGRESS));
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        task.updateStatus(TaskStatus.TODO);
        task.updateStatus(TaskStatus.IN_PROGRESS);
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
    }

    @Test
    void naturalOrderUsesDeadlineAndDeterministicTies() {
        Task undated = task("0", "Alphabetically first");
        Task late = task("1", "Lab");
        late.setDueDate(TODAY.plusDays(2));
        Task earlyLaterTitle = task("2", "Review");
        earlyLaterTitle.setDueDate(TODAY);
        Task earlyFirstId = task("3", "Plan");
        earlyFirstId.setDueDate(TODAY);
        Task earlyLaterId = task("4", "Plan");
        earlyLaterId.setDueDate(TODAY);

        List<Task> sorted = new ArrayList<>(List.of(undated, late, earlyLaterId,
                earlyLaterTitle, earlyFirstId));
        sorted.sort(null);
        assertEquals(List.of("3", "4", "2", "1", "0"), sorted.stream().map(Task::getId).toList());
    }

    @Test
    void ownsDetachedSubtasksAndCanEditAndRemoveDescendants() {
        Task parent = task("p", "Write report");
        Task child = task("c", "Collect sources");
        child.addSubtask(task("g", "Review a paper"));
        parent.addSubtask(child);

        child.markCompleted();
        assertEquals(TaskStatus.TODO, parent.getSubtasks().getFirst().getStatus());
        Task snapshot = parent.getSubtasks().getFirst();
        snapshot.setTitle("Outside mutation");
        assertEquals("Collect sources", parent.getSubtasks().getFirst().getTitle());

        assertTrue(parent.editSubtask("g", Task::markCompleted));
        Task descendant = parent.getSubtasks().getFirst().getSubtasks().getFirst();
        assertEquals(TaskStatus.COMPLETED, descendant.getStatus());
        assertEquals(TaskStatus.TODO, parent.getStatus());
        assertFalse(parent.editSubtask("missing", Task::markCompleted));
        assertTrue(parent.removeSubtask("g"));
        assertTrue(parent.getSubtasks().getFirst().getSubtasks().isEmpty());
        assertTrue(parent.removeSubtask("c"));
        assertTrue(parent.getSubtasks().isEmpty());
        assertFalse(parent.removeSubtask("c"));
    }

    @Test
    void rejectsDuplicateIdsAndAncestorReferencesWithoutMutatingTheTree() {
        Task parent = task("p", "Parent");
        Task child = task("c", "Child");
        child.addSubtask(task("g", "Grandchild"));
        parent.addSubtask(child);

        assertThrows(IllegalArgumentException.class, () -> parent.addSubtask(parent));
        assertThrows(IllegalArgumentException.class, () -> parent.addSubtask(task("g", "Duplicate")));
        Task introducesAncestor = task("n", "New child");
        introducesAncestor.addSubtask(task("p", "Duplicate ancestor"));
        assertThrows(IllegalArgumentException.class, () -> parent.addSubtask(introducesAncestor));
        assertThrows(IllegalArgumentException.class, () ->
                parent.editSubtask("g", descendant -> descendant.addSubtask(task("p", "Ancestor"))));
        assertEquals(List.of("c"), parent.getSubtasks().stream().map(Task::getId).toList());
        Task original = parent.getSubtasks().getFirst().getSubtasks().getFirst();
        assertEquals("g", original.getId());
        assertTrue(original.getSubtasks().isEmpty());
    }

    @Test
    void validatesRequiredFieldsAndDurations() {
        assertThrows(IllegalArgumentException.class, () -> task("a", "   "));
        assertThrows(IllegalArgumentException.class, () -> task(null, "Lab"));
        assertThrows(IllegalArgumentException.class, () -> new GeneralTask("a", "Lab", null, Priority.LOW));
        assertThrows(IllegalArgumentException.class, () -> new GeneralTask("a", "Lab", TODAY, null));
        assertThrows(IllegalArgumentException.class, () -> new StudySession("s", "Read", "", null, TODAY,
                null, Priority.LOW, TaskStatus.TODO, Duration.ZERO, null));
        assertThrows(IllegalArgumentException.class, () -> new StudySession("s", "Read", "", null, TODAY,
                null, Priority.LOW, TaskStatus.TODO, Duration.ofMinutes(5), Duration.ofSeconds(-1)));
    }

    private static GeneralTask task(String id, String title) {
        return new GeneralTask(id, title, TODAY, Priority.MEDIUM);
    }
}
