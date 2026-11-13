package com.studyflow.config;

import com.studyflow.model.Note;
import com.studyflow.model.Priority;
import com.studyflow.model.Recurrence;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.model.Theme;
import com.studyflow.model.UserSettings;
import com.studyflow.model.Workspace;
import com.studyflow.repository.StorageException;
import com.studyflow.service.TaskManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class StudyFlowContextTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @TempDir
    Path dataDirectory;

    @Test
    void reopenedProfileRetainsAllRecordsAndUsesSavedSettings() throws IOException {
        String subjectId;
        String workspaceId;
        String taskId;
        String noteId;
        try (StudyFlowContext first = StudyFlowContext.open(dataDirectory, CLOCK)) {
            UserSettings settings = first.settings();
            settings.setTheme(Theme.DARK);
            settings.setDefaultPriority(Priority.LOW);
            first.saveSettings(settings);
            settings.setDefaultPriority(Priority.HIGH);
            Subject subject = first.subjectManager().create("Physics", "PHY", "", List.of("lab"));
            subjectId = subject.getId();
            Workspace workspace = first.workspaceManager().create("Autumn", "", List.of(subjectId));
            workspaceId = workspace.getId();
            Task task = first.taskManager().quickAdd("Problem set", subjectId, TODAY, null);
            taskId = task.getId();
            assertEquals(Priority.LOW, task.getPriority());
            Note note = first.noteManager().create("Lecture", "Practice the example", subjectId, List.of("class"));
            noteId = note.getId();
        }

        try (StudyFlowContext reopened = StudyFlowContext.open(dataDirectory, CLOCK)) {
            assertEquals(Theme.DARK, reopened.settings().getTheme());
            assertEquals(Priority.LOW, reopened.settings().getDefaultPriority());
            assertEquals(subjectId, reopened.subjectManager().findById(subjectId).orElseThrow().getId());
            assertEquals(List.of(subjectId), reopened.workspaceManager().findContents(workspaceId)
                    .stream().map(Subject::getId).toList());
            assertEquals(subjectId, reopened.taskManager().findById(taskId).orElseThrow().getSubjectId());
            assertEquals(subjectId, reopened.noteManager().findById(noteId).orElseThrow().getSubjectId());
            assertEquals(1, reopened.searchService().search("Physics").tasks().size());
            assertThrows(IllegalStateException.class, () -> reopened.subjectManager().delete(subjectId));
        }
    }

    @Test
    void persistsRecurringCompletionAndPreventsDuplicateSuccessorsAfterRestart() throws IOException {
        String taskId;
        String nextId;
        try (StudyFlowContext first = StudyFlowContext.open(dataDirectory, CLOCK)) {
            Task task = first.taskManager().quickAdd("Weekly review", null, TODAY, Priority.HIGH);
            taskId = task.getId();
            first.taskManager().setRecurrence(taskId, Recurrence.WEEKLY);
            first.taskManager().markCompleted(taskId);
            nextId = first.taskManager().findAll().stream().filter(item -> !item.getId().equals(taskId))
                    .findFirst().orElseThrow().getId();
        }

        try (StudyFlowContext reopened = StudyFlowContext.open(dataDirectory, CLOCK)) {
            Task completed = reopened.taskManager().findById(taskId).orElseThrow();
            assertEquals(TaskStatus.COMPLETED, completed.getStatus());
            assertTrue(completed.hasAdvancedRecurrence());
            assertEquals(TODAY.plusWeeks(1), reopened.taskManager().findById(nextId).orElseThrow().getDueDate());
            reopened.taskManager().delete(nextId);
            reopened.taskManager().updateStatus(taskId, TaskStatus.TODO);
            reopened.taskManager().markCompleted(taskId);
            assertEquals(1, reopened.taskManager().findAll().size());
        }
    }

    @Test
    void rejectsASecondWriterUntilTheFirstProfileCloses() throws IOException {
        try (StudyFlowContext first = StudyFlowContext.open(dataDirectory, CLOCK)) {
            IOException failure = assertThrows(IOException.class, () -> StudyFlowContext.open(dataDirectory, CLOCK));
            assertTrue(failure.getMessage().contains(dataDirectory.toString()));
            assertTrue(first.taskManager().findAll().isEmpty());
        }
        try (StudyFlowContext reopened = StudyFlowContext.open(dataDirectory, CLOCK)) {
            assertTrue(reopened.taskManager().findAll().isEmpty());
        }
    }

    @Test
    void capturedManagerCannotWriteAfterItsProfileCloses() throws IOException {
        TaskManager oldManager;
        try (StudyFlowContext first = StudyFlowContext.open(dataDirectory, CLOCK)) {
            oldManager = first.taskManager();
            oldManager.quickAdd("Original", null, TODAY, null);
        }

        try (StudyFlowContext reopened = StudyFlowContext.open(dataDirectory, CLOCK)) {
            reopened.taskManager().quickAdd("New session", null, TODAY, null);
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> oldManager.quickAdd("Stale write", null, TODAY, null));
            assertTrue(failure.getMessage().contains("file storage is closed"));
            assertEquals(List.of("Original", "New session"),
                    reopened.taskManager().findAll().stream().map(Task::getTitle).toList());
        }
        try (StudyFlowContext reopened = StudyFlowContext.open(dataDirectory, CLOCK)) {
            assertEquals(List.of("Original", "New session"),
                    reopened.taskManager().findAll().stream().map(Task::getTitle).toList());
        }
    }

    @Test
    void preservesCorruptedFileAndReleasesLockSoAnExplicitRestoreCanRecover() throws IOException {
        String taskId;
        try (StudyFlowContext first = StudyFlowContext.open(dataDirectory, CLOCK)) {
            taskId = first.taskManager().quickAdd("Previous", null, TODAY, null).getId();
            first.taskManager().update(taskId, task -> task.setTitle("Latest"));
        }
        Path live = dataDirectory.resolve("tasks/tasks.json");
        Path backup = firstBackup("tasks-");
        Files.writeString(live, "{ broken");

        assertThrows(StorageException.class, () -> StudyFlowContext.open(dataDirectory, CLOCK));
        assertEquals("{ broken", Files.readString(live));
        StudyFlowContext.restore(dataDirectory, "tasks", backup);

        try (StudyFlowContext restored = StudyFlowContext.open(dataDirectory, CLOCK)) {
            assertEquals("Previous", restored.taskManager().findById(taskId).orElseThrow().getTitle());
        }
    }

    @Test
    void rejectsRecoveryThatWouldReintroduceAnInvalidSubjectReference() throws IOException {
        try (StudyFlowContext first = StudyFlowContext.open(dataDirectory, CLOCK)) {
            String subjectId = first.subjectManager().create("Physics", "PHY", "", List.of()).getId();
            String taskId = first.taskManager().quickAdd("Report", subjectId, TODAY, null).getId();
            first.taskManager().assignSubject(taskId, null);
            first.subjectManager().delete(subjectId);
        }
        Path live = dataDirectory.resolve("tasks/tasks.json");
        String current = Files.readString(live);
        Path backup = firstBackup("tasks-");

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> StudyFlowContext.restore(dataDirectory, "tasks", backup));
        assertTrue(messages(failure).contains("Unknown subject ID"));
        assertEquals(current, Files.readString(live));
        assertTrue(Files.isRegularFile(backup));
        try (StudyFlowContext reopened = StudyFlowContext.open(dataDirectory, CLOCK)) {
            assertNull(reopened.taskManager().findAll().getFirst().getSubjectId());
        }
    }

    @Test
    void refusesToOpenWhenAMissingSubjectFileWouldLeaveDanglingReferences() throws IOException {
        try (StudyFlowContext first = StudyFlowContext.open(dataDirectory, CLOCK)) {
            String subjectId = first.subjectManager().create("Physics", "PHY", "", List.of()).getId();
            first.noteManager().create("Physics lecture", "", subjectId, List.of());
        }
        Path subjects = dataDirectory.resolve("subjects/subjects.json");
        Path notes = dataDirectory.resolve("notes/notes.json");
        String noteBytes = Files.readString(notes);
        Files.delete(subjects);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> StudyFlowContext.open(dataDirectory, CLOCK));
        assertTrue(messages(failure).contains("notes.json"));
        assertTrue(messages(failure).contains("Unknown subject ID"));
        assertFalse(Files.exists(subjects));
        assertEquals(noteBytes, Files.readString(notes));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tasks", "subjects", "workspaces", "notes", "settings"})
    void manuallyBacksUpExactBytesOfFirstSavedVersion(String type) throws IOException {
        saveLinkedProfile();
        Path live = dataDirectory.resolve(type).resolve(type + ".json");
        byte[] expected = Files.readAllBytes(live);
        assertTrue(backupFiles().isEmpty());

        Path backup = StudyFlowContext.backup(dataDirectory, type);

        assertEquals(dataDirectory.resolve("backups"), backup.getParent());
        assertTrue(backup.getFileName().toString().startsWith(type + "-manual-"));
        assertTrue(backup.getFileName().toString().endsWith(".json"));
        assertArrayEquals(expected, Files.readAllBytes(backup));
        assertArrayEquals(expected, Files.readAllBytes(live));
        assertEquals(List.of(backup), backupFiles());
    }

    @Test
    void repeatedManualBackupsAreDistinctAndCanBeRestored() throws IOException {
        String taskId;
        try (StudyFlowContext profile = StudyFlowContext.open(dataDirectory, CLOCK)) {
            taskId = profile.taskManager().quickAdd("First version", null, TODAY, null).getId();
        }
        Path first = StudyFlowContext.backup(dataDirectory, "tasks");
        Path second = StudyFlowContext.backup(dataDirectory, "tasks");
        assertNotEquals(first, second);
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        try (StudyFlowContext profile = StudyFlowContext.open(dataDirectory, CLOCK)) {
            profile.taskManager().update(taskId, task -> task.setTitle("Later version"));
        }

        StudyFlowContext.restore(dataDirectory, "tasks", first);

        try (StudyFlowContext profile = StudyFlowContext.open(dataDirectory, CLOCK)) {
            assertEquals("First version", profile.taskManager().findById(taskId).orElseThrow().getTitle());
        }
        assertTrue(Files.isRegularFile(first));
        assertTrue(Files.isRegularFile(second));
    }

    @Test
    void rejectsManualBackupWhenTheSelectedFileHasNeverBeenSaved() throws IOException {
        Path live = dataDirectory.resolve("tasks/tasks.json");

        StorageException failure = assertThrows(StorageException.class,
                () -> StudyFlowContext.backup(dataDirectory, "tasks"));

        assertEquals(live, failure.getPath());
        assertFalse(Files.exists(live));
        assertTrue(backupFiles().isEmpty());
    }

    @Test
    void rejectsManualBackupForACorruptedProfileAndPreservesExistingFiles() throws IOException {
        saveLinkedProfile();
        Path validBackup = StudyFlowContext.backup(dataDirectory, "tasks");
        byte[] backupBytes = Files.readAllBytes(validBackup);
        Path notes = dataDirectory.resolve("notes/notes.json");
        Files.writeString(notes, "{ damaged");

        StorageException failure = assertThrows(StorageException.class,
                () -> StudyFlowContext.backup(dataDirectory, "tasks"));

        assertEquals(notes, failure.getPath());
        assertEquals("{ damaged", Files.readString(notes));
        assertEquals(List.of(validBackup), backupFiles());
        assertArrayEquals(backupBytes, Files.readAllBytes(validBackup));
        assertArrayEquals(backupBytes, Files.readAllBytes(dataDirectory.resolve("tasks/tasks.json")));
    }

    @Test
    void manualBackupRespectsTheProfileLock() throws IOException {
        try (StudyFlowContext profile = StudyFlowContext.open(dataDirectory, CLOCK)) {
            profile.taskManager().quickAdd("Locked task", null, TODAY, null);

            IOException failure = assertThrows(IOException.class,
                    () -> StudyFlowContext.backup(dataDirectory, "tasks"));

            assertTrue(failure.getMessage().contains(dataDirectory.toString()));
            assertTrue(backupFiles().isEmpty());
            assertEquals("Locked task", profile.taskManager().findAll().getFirst().getTitle());
        }
        assertTrue(Files.isRegularFile(StudyFlowContext.backup(dataDirectory, "tasks")));
    }

    private void saveLinkedProfile() throws IOException {
        try (StudyFlowContext profile = StudyFlowContext.open(dataDirectory, CLOCK)) {
            String subjectId = profile.subjectManager().create("Physics", "PHY", "", List.of()).getId();
            profile.workspaceManager().create("Autumn", "", List.of(subjectId));
            profile.taskManager().quickAdd("Problem set", subjectId, TODAY, Priority.HIGH);
            profile.noteManager().create("Lecture", "Practice the example", subjectId, List.of());
            profile.saveSettings(profile.settings());
        }
    }

    private List<Path> backupFiles() throws IOException {
        try (var files = Files.list(dataDirectory.resolve("backups"))) {
            return files.sorted().toList();
        }
    }

    private Path firstBackup(String prefix) throws IOException {
        try (var files = Files.list(dataDirectory.resolve("backups"))) {
            return files.filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> path.toString().endsWith(".json")).sorted().findFirst().orElseThrow();
        }
    }

    private static String messages(Throwable error) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
