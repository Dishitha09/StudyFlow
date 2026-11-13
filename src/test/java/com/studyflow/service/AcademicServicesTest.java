package com.studyflow.service;

import com.studyflow.factory.TaskDraft;
import com.studyflow.interfaces.Storage;
import com.studyflow.model.Note;
import com.studyflow.model.Priority;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.Workspace;
import com.studyflow.repository.MemoryStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AcademicServicesTest {
    private final TestClock clock = new TestClock();
    private final Storage<List<Subject>> subjectStorage = new MemoryStorage<>(List.of());
    private final Storage<List<Workspace>> workspaceStorage = new MemoryStorage<>(List.of());
    private final Storage<List<Note>> noteStorage = new MemoryStorage<>(List.of());
    private final Function<String, Optional<Subject>> subjectLookup = id -> subjectStorage.load().stream()
            .filter(subject -> subject.getId().equals(id)).findFirst();
    private final TaskManager tasks = new TaskManager(clock, Priority.MEDIUM, subjectLookup,
            new MemoryStorage<>(List.of()));
    private final SubjectManager subjects = new SubjectManager(subjectStorage, tasks, noteStorage::load,
            workspaceStorage::load);
    private final WorkspaceManager workspaces = new WorkspaceManager(workspaceStorage, subjectLookup, clock);
    private final NoteManager notes = new NoteManager(clock, subjectLookup, noteStorage);
    private final SearchService search = new SearchService(tasks, subjects, notes);

    @Test
    void managesSubjectMetadataAndSearchWithoutExposingStoredObjects() {
        Subject subject = subjects.create("Physics", "PHY", "Mechanics", List.of("science"));
        assertEquals(subject.getId(), subjects.findById(subject.getId()).orElseThrow().getId());
        assertEquals(List.of("Physics"), subjects.findAll().stream().map(Subject::getName).toList());
        assertEquals(List.of("Physics"), subjects.search("phy SCI").stream().map(Subject::getName).toList());

        subject.setName("Outside edit");
        subjects.update(subject.getId(), "Applied Physics", "PHY101", "Optics", List.of("lab"));
        Subject edited = subjects.findById(subject.getId()).orElseThrow();
        assertEquals("Applied Physics", edited.getName());
        assertEquals("PHY101", edited.getSubjectCode());
        assertEquals("Optics", edited.getDescription());
        assertEquals(List.of("lab"), edited.getTags());
        assertTrue(subjects.search("science").isEmpty());

        subjects.delete(subject.getId());
        assertTrue(subjects.findById(subject.getId()).isEmpty());
    }

    @Test
    void associatesRootsAndDescendantsAndCountsTasksPerSubject() {
        Subject physics = subjects.create("Physics", "PHY", "", List.of());
        Subject maths = subjects.create("Maths", "MTH", "", List.of());
        Task task = tasks.quickAdd("Problem set", null, null, Priority.HIGH);
        Task child = tasks.createSubtask(task.getId(), new TaskDraft("Work question one", "",
                physics.getId(), null, Priority.MEDIUM, new TaskDraft.General()));
        subjects.assignTask(task.getId(), physics.getId());

        assertEquals(List.of(task.getId(), child.getId()),
                subjects.findTasks(physics.getId()).stream().map(Task::getId).toList());
        assertEquals(2, subjects.countTasks(physics.getId()));
        assertEquals(0, subjects.countTasks(maths.getId()));

        subjects.assignTask(task.getId(), maths.getId());
        assertEquals(1, subjects.countTasks(physics.getId()));
        assertEquals(1, subjects.countTasks(maths.getId()));
        subjects.assignTask(task.getId(), null);
        assertEquals(0, subjects.countTasks(maths.getId()));
        assertNull(tasks.findById(task.getId()).orElseThrow().getSubjectId());
    }

    @Test
    void editsWorkspaceAndResolvesOrderedSubjectsWithIndependentLifecycles() {
        Subject physics = subjects.create("Physics", "PHY", "", List.of());
        Subject maths = subjects.create("Maths", "MTH", "", List.of());
        Workspace term = workspaces.create("First term", "Current courses");
        Workspace lab = workspaces.create("Lab", "");
        workspaces.addSubject(term.getId(), maths.getId());
        workspaces.addSubject(term.getId(), physics.getId());
        workspaces.addSubject(lab.getId(), physics.getId());
        assertThrows(IllegalArgumentException.class, () -> workspaces.addSubject(term.getId(), physics.getId()));

        workspaces.update(term.getId(), "Fall term", "New description");
        subjects.update(physics.getId(), "Applied Physics", "PHY", "", List.of());
        assertEquals(List.of("Maths", "Applied Physics"),
                workspaces.findContents(term.getId()).stream().map(Subject::getName).toList());
        Workspace updated = workspaces.findById(term.getId()).orElseThrow();
        assertEquals("Fall term", updated.getName());
        assertEquals("New description", updated.getDescription());
        assertEquals(term.getCreatedAt(), updated.getCreatedAt());
        assertEquals(2, workspaces.findAll().size());

        workspaces.removeSubject(term.getId(), maths.getId());
        assertEquals(List.of(physics.getId()),
                workspaces.findContents(term.getId()).stream().map(Subject::getId).toList());
        workspaces.delete(term.getId());
        assertTrue(workspaces.findById(term.getId()).isEmpty());
        assertEquals(List.of(physics.getId()),
                workspaces.findContents(lab.getId()).stream().map(Subject::getId).toList());
        assertTrue(subjects.findById(maths.getId()).isPresent());
    }

    @Test
    void canCreateWorkspaceWithValidatedInitialMembership() {
        Subject physics = subjects.create("Physics", "PHY", "", List.of());
        Workspace workspace = workspaces.create("Term", "", List.of(physics.getId()));
        assertEquals(List.of(physics.getId()), workspace.getSubjectIds());
        assertThrows(IllegalArgumentException.class, () -> workspaces.create("Broken", "", List.of("missing")));
        assertEquals(List.of(workspace.getId()), workspaces.findAll().stream().map(Workspace::getId).toList());
    }

    @Test
    void editsNotesWithTagsAndTimestampsAndCanClearTheirSubject() {
        Subject physics = subjects.create("Physics", "PHY", "", List.of());
        Note note = notes.create("Lecture", "Ray diagrams", physics.getId(), List.of("week1"));
        LocalDateTime createdAt = LocalDateTime.now(clock);
        assertEquals(createdAt, note.getCreatedAt());
        assertEquals(createdAt, note.getUpdatedAt());
        assertEquals(List.of(note.getId()), notes.findBySubject(physics.getId()).stream().map(Note::getId).toList());

        clock.advanceSeconds(10);
        notes.update(note.getId(), "Optics lecture", "Refraction", physics.getId(), List.of("week1"));
        clock.advanceSeconds(5);
        notes.addTag(note.getId(), "exam");
        Note edited = notes.findById(note.getId()).orElseThrow();
        assertEquals("Refraction", edited.getContent());
        assertEquals(List.of("week1", "exam"), edited.getTags());
        assertEquals(createdAt, edited.getCreatedAt());
        assertEquals(createdAt.plusSeconds(15), edited.getUpdatedAt());
        assertEquals(List.of(note.getId()), notes.search("EXA refr").stream().map(Note::getId).toList());

        notes.assignSubject(note.getId(), null);
        assertTrue(notes.findBySubject(physics.getId()).isEmpty());
        assertNull(notes.findById(note.getId()).orElseThrow().getSubjectId());
        notes.delete(note.getId());
        assertTrue(notes.findAll().isEmpty());
        assertTrue(notes.findById(note.getId()).isEmpty());
    }

    @Test
    void globalSearchCategorizesMatchesAndHandlesBlankOrMissingContent() {
        Subject physics = subjects.create("Applied Physics", "PHY", "", List.of("revision"));
        Task task = tasks.quickAdd("Revision exercises", physics.getId(), null, null);
        Note note = notes.create("Physics lecture", null, physics.getId(), List.of("revision"));
        notes.create("Unrelated", null, null, List.of());

        SearchService.SearchResults results = search.search("  PHYS revis ");
        assertEquals(List.of(task.getId()), results.tasks().stream().map(Task::getId).toList());
        assertEquals(List.of(physics.getId()), results.subjects().stream().map(Subject::getId).toList());
        assertEquals(List.of(note.getId()), results.notes().stream().map(Note::getId).toList());
        for (String emptyQuery : new String[]{null, "", "  ", "\u00a0"}) {
            SearchService.SearchResults blank = search.search(emptyQuery);
            assertTrue(blank.tasks().isEmpty());
            assertTrue(blank.subjects().isEmpty());
            assertTrue(blank.notes().isEmpty());
        }
    }

    @Test
    void blocksSubjectDeletionForEachReferenceAndPreservesRecords() {
        Subject subject = subjects.create("Physics", "PHY", "", List.of());
        Workspace workspace = workspaces.create("Term", "");
        workspaces.addSubject(workspace.getId(), subject.getId());
        Note note = notes.create("Lecture", "", subject.getId(), List.of());
        Task root = tasks.quickAdd("Report", null, null, null);
        Task child = tasks.createSubtask(root.getId(), new TaskDraft("Measure samples", "", subject.getId(), null,
                null, new TaskDraft.General()));

        assertThrows(IllegalStateException.class, () -> subjects.delete(subject.getId()));
        subjects.assignSubtask(root.getId(), child.getId(), null);
        assertThrows(IllegalStateException.class, () -> subjects.delete(subject.getId()));
        notes.assignSubject(note.getId(), null);
        assertThrows(IllegalStateException.class, () -> subjects.delete(subject.getId()));
        workspaces.delete(workspace.getId());
        subjects.delete(subject.getId());

        assertTrue(subjects.findById(subject.getId()).isEmpty());
        assertTrue(notes.findById(note.getId()).isPresent());
        assertEquals(child.getId(), tasks.findSubtasks(root.getId()).getFirst().getId());
    }

    @Test
    void rejectsUnknownSubjectLinksAndKeepsCommittedRelationships() {
        Subject subject = subjects.create("Physics", "PHY", "", List.of());
        Workspace workspace = workspaces.create("Term", "");
        Note note = notes.create("Lecture", "", subject.getId(), List.of());
        Task task = tasks.quickAdd("Report", subject.getId(), null, null);

        assertThrows(IllegalArgumentException.class, () -> workspaces.addSubject(workspace.getId(), "missing"));
        assertThrows(IllegalArgumentException.class, () -> notes.assignSubject(note.getId(), "missing"));
        assertThrows(IllegalArgumentException.class, () -> notes.create("Invalid", "", "missing", List.of()));
        assertThrows(IllegalArgumentException.class, () -> subjects.assignTask(task.getId(), "missing"));
        assertEquals(List.of(), workspaces.findContents(workspace.getId()));
        assertEquals(subject.getId(), notes.findById(note.getId()).orElseThrow().getSubjectId());
        assertEquals(subject.getId(), tasks.findById(task.getId()).orElseThrow().getSubjectId());
    }

    private static final class TestClock extends Clock {
        private Instant instant = Instant.parse("2026-11-13T08:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
