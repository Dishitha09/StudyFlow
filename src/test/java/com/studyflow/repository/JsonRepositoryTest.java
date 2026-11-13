package com.studyflow.repository;

import com.studyflow.model.Assignment;
import com.studyflow.model.GeneralTask;
import com.studyflow.model.Note;
import com.studyflow.model.Priority;
import com.studyflow.model.Recurrence;
import com.studyflow.model.StudySession;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.model.Theme;
import com.studyflow.model.UserSettings;
import com.studyflow.model.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class JsonRepositoryTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 13);
    private static final LocalDateTime CREATED = DAY.atTime(9, 15);

    @TempDir
    Path directory;

    @Test
    void savesLoadsUpdatesAndDeletesPolymorphicTasksAcrossRepositoryInstances() {
        TaskRepository repository = new TaskRepository(new FileStorage(directory));
        Assignment assignment = new Assignment("task", "Lab report", "", "physics", DAY, DAY.plusDays(3),
                Priority.HIGH, TaskStatus.TODO, "Portal", "lab.pdf");
        assignment.setRecurrence(Recurrence.WEEKLY);
        assignment.addSubtask(new StudySession("child", "Review data", "", "physics", DAY, null,
                Priority.LOW, TaskStatus.IN_PROGRESS, Duration.ofMinutes(25), Duration.ofMinutes(10)));
        repository.insert(assignment);
        assertTrue(repository.existsById("task"));
        assertThrows(IllegalArgumentException.class, () -> repository.insert(assignment));

        TaskRepository reopened = new TaskRepository(new FileStorage(directory));
        Assignment loaded = assertInstanceOf(Assignment.class, reopened.findById("task").orElseThrow());
        assertEquals("lab.pdf", loaded.getAttachmentPath());
        assertEquals("physics", loaded.getSubjectId());
        assertEquals(DAY.plusDays(3), loaded.getDueDate());
        assertEquals(Recurrence.WEEKLY, loaded.getRecurrence());
        StudySession child = assertInstanceOf(StudySession.class, loaded.getSubtasks().getFirst());
        assertEquals(Duration.ofMinutes(25), child.getPlannedDuration());
        assertEquals(Duration.ofMinutes(10), child.getActualDuration());
        assertEquals(TaskStatus.IN_PROGRESS, child.getStatus());

        loaded.setTitle("Updated lab report");
        reopened.update(loaded);
        assertEquals(List.of("Updated lab report"), reopened.findAll().stream().map(Task::getTitle).toList());
        assertThrows(NoSuchElementException.class, () -> reopened.update(task("missing", "Missing")));
        assertTrue(reopened.delete("task"));
        assertFalse(reopened.delete("task"));
        assertTrue(new TaskRepository(new FileStorage(directory)).findAll().isEmpty());
    }

    @Test
    void savesSubjectsWorkspacesNotesAndSettingsUsingTheirOwnFiles() {
        FileStorage storage = new FileStorage(directory);
        new SubjectRepository(storage).insert(new Subject("physics", "Physics", "PHY", "", List.of("lab")));
        new WorkspaceRepository(storage).insert(new Workspace("term", "Autumn term", "Current classes",
                List.of("physics"), CREATED));
        new NoteRepository(storage).insert(new Note("note", "Lecture", "λ = d sin θ", "physics",
                CREATED, CREATED.plusMinutes(1), List.of("revision")));
        UserSettings settings = new UserSettings(Theme.DARK, Priority.LOW, 82, false,
                Duration.ofMinutes(30), Duration.ofMinutes(8));
        new SettingsRepository(storage).save(settings);

        FileStorage reopened = new FileStorage(directory);
        assertEquals(List.of("lab"), new SubjectRepository(reopened).findById("physics").orElseThrow().getTags());
        Workspace workspace = new WorkspaceRepository(reopened).findById("term").orElseThrow();
        assertEquals(List.of("physics"), workspace.getSubjectIds());
        assertEquals(CREATED, workspace.getCreatedAt());
        Note note = new NoteRepository(reopened).findById("note").orElseThrow();
        assertEquals("λ = d sin θ", note.getContent());
        assertEquals("physics", note.getSubjectId());
        assertEquals(CREATED.plusMinutes(1), note.getUpdatedAt());
        assertEquals(List.of("revision"), note.getTags());
        UserSettings actual = new SettingsRepository(reopened).load();
        assertEquals(Theme.DARK, actual.getTheme());
        assertEquals(Priority.LOW, actual.getDefaultPriority());
        assertEquals(82, actual.getDefaultAttendanceTarget());
        assertFalse(actual.isNotificationsEnabled());
        assertEquals(Duration.ofMinutes(30), actual.getFocusDuration());
        assertEquals(Duration.ofMinutes(8), actual.getBreakDuration());

        for (String relative : List.of("subjects/subjects.json", "workspaces/workspaces.json",
                "notes/notes.json", "settings/settings.json")) {
            assertTrue(Files.isRegularFile(directory.resolve(relative)), relative);
        }
    }

    @Test
    void missingFilesStartWithEmptyCollectionsAndDefaultSettings() {
        FileStorage storage = new FileStorage(directory.resolve("new-profile"));
        assertTrue(new TaskRepository(storage).load().isEmpty());
        assertTrue(new SubjectRepository(storage).load().isEmpty());
        assertTrue(new WorkspaceRepository(storage).load().isEmpty());
        assertTrue(new NoteRepository(storage).load().isEmpty());
        assertEquals(Theme.LIGHT, new SettingsRepository(storage).load().getTheme());
        assertTrue(Files.isDirectory(storage.dataDirectory()));
        assertTrue(Files.isDirectory(storage.backupDirectory()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{ incomplete", "null", "[] []"})
    void malformedOrEmptySnapshotsAreRejectedWithoutOverwritingThem(String invalid) throws IOException {
        TaskRepository repository = new TaskRepository(new FileStorage(directory));
        repository.insert(task("first", "Original"));
        Path live = directory.resolve("tasks/tasks.json");
        Files.writeString(live, invalid);

        StorageException failure = assertThrows(StorageException.class, repository::load);
        assertEquals(live, failure.getPath());
        assertThrows(StorageException.class, () -> repository.save(List.of(task("new", "Replacement"))));
        assertEquals(invalid, Files.readString(live));
    }

    @Test
    void rejectsBadDatesAndEnumValues() throws IOException {
        TaskRepository repository = new TaskRepository(new FileStorage(directory));
        repository.insert(task("first", "Original"));
        Path live = directory.resolve("tasks/tasks.json");
        String valid = Files.readString(live);
        String invalidDate = valid.replace(DAY.toString(), "2026-02-30");
        assertNotEquals(valid, invalidDate);
        Files.writeString(live, invalidDate);
        assertThrows(StorageException.class, repository::load);

        String invalidPriority = valid.replace("\"HIGH\"", "\"URGENT\"");
        assertNotEquals(valid, invalidPriority);
        Files.writeString(live, invalidPriority);
        assertThrows(StorageException.class, repository::load);
    }

    @Test
    void savesUniquePreviousVersionsAndSkipsUnchangedSnapshots() throws IOException {
        FileStorage storage = new FileStorage(directory);
        TaskRepository repository = new TaskRepository(storage);
        Task task = task("task", "Version one");
        repository.insert(task);
        int initialBackups = backupFiles(storage, "tasks-").size();
        task.setTitle("Version two");
        repository.update(task);
        task.setTitle("Version three");
        repository.update(task);

        List<Path> backups = backupFiles(storage, "tasks-");
        assertEquals(initialBackups + 2, backups.size());
        assertEquals(backups.size(), backups.stream().map(Path::getFileName).distinct().count());
        repository.save(repository.load());
        assertEquals(backups, backupFiles(storage, "tasks-"));
    }

    @Test
    void restoresValidBackupAndPreservesDamagedOriginal() throws IOException {
        FileStorage storage = new FileStorage(directory);
        TaskRepository repository = new TaskRepository(storage);
        Task original = task("task", "Before update");
        repository.insert(original);
        original.setTitle("After update");
        repository.update(original);
        Path backup = backupFiles(storage, "tasks-").getFirst();
        Path live = directory.resolve("tasks/tasks.json");
        Files.writeString(live, "damaged original");

        repository.restore(backup, candidate -> assertEquals("Before update", candidate.getFirst().getTitle()));

        assertEquals("Before update", repository.findById("task").orElseThrow().getTitle());
        assertTrue(Files.exists(backup));
        assertEquals("damaged original", Files.readString(
                backupFiles(storage, "tasks-before-restore-").getFirst()));
    }

    @Test
    void failedBackupCreationLeavesExistingSnapshotUnchanged() throws IOException {
        FileStorage storage = new FileStorage(directory);
        TaskRepository repository = new TaskRepository(storage);
        repository.insert(task("task", "Prior state"));
        Files.delete(storage.backupDirectory());
        Files.writeString(storage.backupDirectory(), "blocked directory");

        Task edited = task("task", "Proposed state");
        assertThrows(StorageException.class, () -> repository.update(edited));
        assertEquals("Prior state", repository.findById("task").orElseThrow().getTitle());
        assertEquals("blocked directory", Files.readString(storage.backupDirectory()));
    }

    private static Task task(String id, String title) {
        return new GeneralTask(id, title, DAY, Priority.HIGH);
    }

    static List<Path> backupFiles(FileStorage storage, String prefix) throws IOException {
        try (var files = Files.list(storage.backupDirectory())) {
            return files.filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
    }
}
