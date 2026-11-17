package com.studyflow.config;

import com.studyflow.integrations.GoogleCalendar;
import com.studyflow.integrations.LocalIntegrations.Link;
import com.studyflow.model.FocusRecord;
import com.studyflow.model.Note;
import com.studyflow.model.StudyMaterial;
import com.studyflow.repository.FileStorage;
import com.studyflow.repository.IntegrationRepository;
import com.studyflow.repository.MaterialRepository;
import com.studyflow.repository.NoteRepository;
import com.studyflow.repository.StorageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ProfileRecoveryTest {
    private static final Instant TIME = Instant.parse("2026-11-17T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(TIME, ZoneOffset.UTC);
    private static final LocalDate DATE = LocalDate.now(CLOCK);

    @TempDir
    Path profile;

    @ParameterizedTest
    @ValueSource(strings = {"attendance/classes.json", "attendance/targets.json", "materials/materials.json"})
    void subjectRestorePreservesTheLiveProfileWhenAnotherFileReferencesASubject(String relative) throws IOException {
        Path emptySubjects = backupWithoutSubjects();
        String subjectId;
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            subjectId = addSubject(context);
            if (relative.equals("attendance/classes.json")) {
                context.attendanceService().add(subjectId, DATE, true);
            } else if (relative.equals("attendance/targets.json")) {
                context.attendanceService().setTarget(subjectId, 90);
            }
        }
        if (relative.equals("materials/materials.json")) {
            try (FileStorage storage = new FileStorage(profile)) {
                new MaterialRepository(storage).insert(new StudyMaterial("fixture", "Lecture", "lecture.pdf",
                        "", subjectId, TIME));
            }
        }
        Path subjects = profile.resolve("subjects/subjects.json");
        Path related = profile.resolve(relative);
        byte[] subjectsBefore = Files.readAllBytes(subjects);
        byte[] relatedBefore = Files.readAllBytes(related);
        List<Path> backupsBefore = backups();

        StorageException failure = assertThrows(StorageException.class,
                () -> StudyFlowContext.restore(profile, "subjects", emptySubjects));

        assertEquals(related, failure.getPath());
        assertArrayEquals(subjectsBefore, Files.readAllBytes(subjects));
        assertArrayEquals(relatedBefore, Files.readAllBytes(related));
        assertEquals(backupsBefore, backups());
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            assertTrue(context.subjectManager().findById(subjectId).isPresent());
        }
    }

    @Test
    void diagnosesOrphanAttendanceOnOpenAndRecoversByRestoringItsSubject() throws IOException {
        Path emptySubjects = backupWithoutSubjects();
        String subjectId;
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            subjectId = addSubject(context);
            context.attendanceService().add(subjectId, DATE, true);
            context.attendanceService().setTarget(subjectId, 90);
        }
        Path currentSubjects = StudyFlowContext.backup(profile, "subjects");
        Files.copy(emptySubjects, profile.resolve("subjects/subjects.json"), StandardCopyOption.REPLACE_EXISTING);

        StorageException failedOpen = assertThrows(StorageException.class,
                () -> StudyFlowContext.open(profile, CLOCK));
        assertEquals(profile.resolve("attendance/classes.json"), failedOpen.getPath());
        StudyFlowContext.restore(profile, "subjects", currentSubjects);

        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            var records = context.attendanceService().snapshot();
            assertEquals(1, records.total());
            assertEquals(1, records.attended());
            assertEquals(90, records.subjects().getFirst().target());
        }
    }

    @Test
    void canRepairTwoDamagedAttendanceFilesIndependently() throws IOException {
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            String subjectId = addSubject(context);
            context.attendanceService().add(subjectId, DATE, true);
            context.attendanceService().setTarget(subjectId, 85);
        }
        Path classesBackup = StudyFlowContext.backup(profile, "attendance");
        Path targetsBackup = StudyFlowContext.backup(profile, "attendance-targets");
        Files.writeString(profile.resolve("attendance/classes.json"), "invalid classes");
        Files.writeString(profile.resolve("attendance/targets.json"), "invalid targets");

        StudyFlowContext.restore(profile, "attendance", classesBackup);
        StudyFlowContext.restore(profile, "attendance-targets", targetsBackup);

        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            var records = context.attendanceService().snapshot();
            assertEquals(1, records.total());
            assertEquals(85, records.subjects().getFirst().target());
        }
    }

    @Test
    void malformedOptionalMaterialMetadataLeavesCoreProfileUsableButBlocksUnsafeSubjectRestore() throws IOException {
        Path emptySubjects = backupWithoutSubjects();
        Path materials = profile.resolve("materials/materials.json");
        Files.writeString(materials, "{broken material metadata");
        byte[] damagedBytes = Files.readAllBytes(materials);
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            addSubject(context);
            context.taskManager().quickAdd("Study", null, DATE, null);
        }
        byte[] subjects = Files.readAllBytes(profile.resolve("subjects/subjects.json"));

        assertTrue(Files.isRegularFile(StudyFlowContext.backup(profile, "tasks")));
        StorageException failure = assertThrows(StorageException.class,
                () -> StudyFlowContext.restore(profile, "subjects", emptySubjects));

        assertEquals(materials, failure.getPath());
        assertArrayEquals(damagedBytes, Files.readAllBytes(materials));
        assertArrayEquals(subjects, Files.readAllBytes(profile.resolve("subjects/subjects.json")));
    }

    @Test
    void focusHistoryAndCalendarMappingsSurviveRestartAndFileRecovery() throws IOException {
        FocusRecord session;
        Link mapping;
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            String subjectId = addSubject(context);
            String taskId = context.taskManager().quickAdd("Practice", subjectId, DATE, null).getId();
            session = new FocusRecord("focus-1", taskId, subjectId, TIME, Duration.ofMinutes(25), Duration.ofMinutes(18));
            mapping = new Link("google:calendar-fixture:" + taskId, "google", GoogleCalendar.eventId(taskId));
            context.focusRepository().insert(session);
            context.taskManager().delete(taskId);
            context.subjectManager().delete(subjectId);
        }
        try (FileStorage storage = new FileStorage(profile)) {
            new IntegrationRepository(storage).insert(mapping);
        }

        assertHistoricalRecords(session, mapping);
        Path focusBackup = StudyFlowContext.backup(profile, "focus");
        Path integrationsBackup = StudyFlowContext.backup(profile, "integrations");
        Files.writeString(profile.resolve("focus/sessions.json"), "{broken focus history");
        Files.writeString(profile.resolve("integrations/links.json"), "{broken links");
        StudyFlowContext.restore(profile, "focus", focusBackup);
        StudyFlowContext.restore(profile, "integrations", integrationsBackup);

        assertHistoricalRecords(session, mapping);
    }

    @Test
    void concurrentRepositoriesSharingOneStorageDoNotLoseWrites() throws Exception {
        int noteCount = 12;
        List<String> expectedIds = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(CLOCK);
        try (FileStorage storage = new FileStorage(profile); var workers = Executors.newFixedThreadPool(4)) {
            NoteRepository first = new NoteRepository(storage);
            NoteRepository second = new NoteRepository(storage);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> writes = new ArrayList<>();
            for (int index = 0; index < noteCount; index++) {
                String id = "note-" + index;
                expectedIds.add(id);
                NoteRepository writer = index % 2 == 0 ? first : second;
                writes.add(workers.submit(() -> {
                    start.await();
                    writer.insert(new Note(id, id, "", null, now, now, List.of()));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> write : writes) {
                write.get(10, TimeUnit.SECONDS);
            }
        }

        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            assertEquals(expectedIds.stream().sorted().toList(),
                    context.noteManager().findAll().stream().map(Note::getId).sorted().toList());
        }
        assertEquals(noteCount - 1, backups().size());
    }

    private void assertHistoricalRecords(FocusRecord session, Link mapping) throws IOException {
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            assertTrue(context.taskManager().findById(session.taskId()).isEmpty());
            assertTrue(context.subjectManager().findById(session.subjectId()).isEmpty());
            assertEquals(List.of(session), context.focusRepository().load());
        }
        try (FileStorage storage = new FileStorage(profile)) {
            assertEquals(mapping, new IntegrationRepository(storage).findById(mapping.id()).orElseThrow());
        }
    }

    private Path backupWithoutSubjects() throws IOException {
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            context.subjectManager().delete(addSubject(context));
        }
        return StudyFlowContext.backup(profile, "subjects");
    }

    private static String addSubject(StudyFlowContext context) {
        return context.subjectManager().create("Algebra", "MTH", "", List.of()).getId();
    }

    private List<Path> backups() throws IOException {
        try (var files = Files.list(profile.resolve("backups"))) {
            return files.sorted().toList();
        }
    }
}
