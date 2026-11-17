package com.studyflow.config;

import com.studyflow.factory.TaskDraft;
import com.studyflow.model.Exam;
import com.studyflow.model.Recurrence;
import com.studyflow.model.StudySession;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaskCompletionTimeTest {
    private static final Instant FIRST_COMPLETION = Instant.parse("2026-11-17T10:45:00Z");
    private static final Instant SECOND_COMPLETION = FIRST_COMPLETION.plus(Duration.ofDays(1));

    @TempDir
    Path profile;

    @Test
    void legacyCompletionRemainsUnknownUntilAnActualNewCompletion() throws IOException {
        Path tasks = profile.resolve("tasks/tasks.json");
        Files.createDirectories(tasks.getParent());
        Files.writeString(tasks, """
                [{
                  "id": "legacy",
                  "title": "Older assignment",
                  "description": "",
                  "subjectId": null,
                  "createdDate": "2025-01-03",
                  "dueDate": "2025-01-10",
                  "priority": "MEDIUM",
                  "status": "COMPLETED",
                  "recurrence": "NONE",
                  "recurrenceAdvanced": false,
                  "subtasks": [],
                  "details": {"type": "ASSIGNMENT", "submissionType": "Online", "attachmentPath": null}
                }]
                """);
        try (StudyFlowContext context = StudyFlowContext.open(profile, at(FIRST_COMPLETION))) {
            var manager = context.taskManager();
            assertNull(manager.markCompleted("legacy").getCompletedAt());
            assertNull(manager.update("legacy", task -> task.setTitle("Updated name")).getCompletedAt());
        }
        assertFalse(Files.readString(tasks).contains("\"completedAt\""));

        try (StudyFlowContext context = StudyFlowContext.open(profile, at(SECOND_COMPLETION))) {
            var manager = context.taskManager();
            assertEquals("Updated name", manager.findById("legacy").orElseThrow().getTitle());
            assertNull(manager.findById("legacy").orElseThrow().getCompletedAt());
            manager.updateStatus("legacy", TaskStatus.TODO);
            assertEquals(SECOND_COMPLETION, manager.markCompleted("legacy").getCompletedAt());
        }

        try (StudyFlowContext context = StudyFlowContext.open(profile, at(FIRST_COMPLETION))) {
            assertEquals(SECOND_COMPLETION, context.taskManager().findById("legacy").orElseThrow().getCompletedAt());
        }
    }

    @Test
    void recordsRootAndSubtaskCompletionsAndStartsTheNextOccurrenceWithoutThem() throws IOException {
        String rootId;
        String childId;
        try (StudyFlowContext context = StudyFlowContext.open(profile, at(FIRST_COMPLETION))) {
            var manager = context.taskManager();
            Task root = manager.create(new TaskDraft("Practice", "", null, LocalDate.of(2026, 11, 17), null,
                    new TaskDraft.StudySessionDetails(Duration.ofMinutes(30), null)));
            rootId = root.getId();
            childId = manager.createSubtask(rootId, new TaskDraft("Self-test", "", null, null, null,
                    new TaskDraft.ExamDetails("Oral", "Ready"))).getId();
            manager.setRecurrence(rootId, Recurrence.DAILY);
            assertEquals(FIRST_COMPLETION, manager.updateSubtaskStatus(rootId, childId,
                    TaskStatus.COMPLETED).getCompletedAt());
            assertEquals(FIRST_COMPLETION, manager.markCompleted(rootId).getCompletedAt());
        }

        try (StudyFlowContext context = StudyFlowContext.open(profile, at(SECOND_COMPLETION))) {
            var manager = context.taskManager();
            assertInstanceOf(StudySession.class, manager.findById(rootId).orElseThrow());
            assertInstanceOf(Exam.class, manager.findSubtasks(rootId).getFirst());
            assertEquals(FIRST_COMPLETION, manager.markCompleted(rootId).getCompletedAt());
            assertEquals(FIRST_COMPLETION, manager.updateSubtaskStatus(rootId, childId,
                    TaskStatus.COMPLETED).getCompletedAt());
            Task successor = manager.findAll().stream().filter(task -> !task.getId().equals(rootId))
                    .findFirst().orElseThrow();
            assertEquals(TaskStatus.TODO, successor.getStatus());
            assertNull(successor.getCompletedAt());
            assertNull(manager.findSubtasks(successor.getId()).getFirst().getCompletedAt());
            assertEquals(FIRST_COMPLETION, manager.updateStatus(rootId, TaskStatus.TODO).getCompletedAt());
        }

        try (StudyFlowContext context = StudyFlowContext.open(profile, at(SECOND_COMPLETION))) {
            var manager = context.taskManager();
            Task reopenedTask = manager.findById(rootId).orElseThrow();
            assertEquals(TaskStatus.TODO, reopenedTask.getStatus());
            assertEquals(FIRST_COMPLETION, reopenedTask.getCompletedAt());
            assertEquals(SECOND_COMPLETION, manager.markCompleted(rootId).getCompletedAt());
            assertEquals(2, manager.findAll().size());
        }
    }

    private static Clock at(Instant time) {
        return Clock.fixed(time, ZoneOffset.UTC);
    }
}
