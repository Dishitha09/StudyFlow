package com.studyflow.repository;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.studyflow.model.Assignment;
import com.studyflow.model.Exam;
import com.studyflow.model.GeneralTask;
import com.studyflow.model.Priority;
import com.studyflow.model.Recurrence;
import com.studyflow.model.StudySession;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.utils.ValidationUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persists the task forest and a fixed set of concrete task details. */
public final class TaskRepository extends JsonCollectionRepository<Task, TaskRepository.TaskRecord> {
    public TaskRepository(FileStorage storage) {
        super(storage, Path.of("tasks", "tasks.json"), TaskRecord.class, TaskRecord::from, TaskRecord::toDomain);
    }

    @Override
    void validateCollection(List<Task> tasks) {
        Set<String> ids = new HashSet<>();
        for (Task task : tasks) {
            validateTree(task, ids, true);
        }
    }

    private static void validateTree(Task task, Set<String> ids, boolean root) {
        if (!ids.add(task.getId())) {
            throw new IllegalArgumentException("Duplicate task ID: " + task.getId());
        }
        if (!root && (task.getRecurrence() != Recurrence.NONE || task.hasAdvancedRecurrence())) {
            throw new IllegalArgumentException("Only root tasks can recur: " + task.getId());
        }
        for (Task child : task.getSubtasks()) {
            validateTree(child, ids, false);
        }
    }

    record TaskRecord(String id, String title, String description, String subjectId, LocalDate createdDate,
                      LocalDate dueDate, Priority priority, TaskStatus status, Recurrence recurrence,
                      Boolean recurrenceAdvanced, List<TaskRecord> subtasks, TaskDetails details) {
        static TaskRecord from(Task task) {
            return new TaskRecord(task.getId(), task.getTitle(), task.getDescription(), task.getSubjectId(),
                    task.getCreatedDate(), task.getDueDate(), task.getPriority(), task.getStatus(),
                    task.getRecurrence(), task.hasAdvancedRecurrence(),
                    task.getSubtasks().stream().map(TaskRecord::from).toList(), TaskDetails.from(task));
        }

        Task toDomain() {
            Task task = ValidationUtils.required(details, "Task type and details").create(this);
            task.setRecurrence(recurrence);
            if (ValidationUtils.required(recurrenceAdvanced, "Task recurrence advancement")) {
                task.markRecurrenceAdvanced();
            }
            for (TaskRecord child : ValidationUtils.required(subtasks, "Task subtasks")) {
                task.addSubtask(ValidationUtils.required(child, "Subtask").toDomain());
            }
            return task;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = GeneralDetails.class, name = "GENERAL"),
            @JsonSubTypes.Type(value = AssignmentDetails.class, name = "ASSIGNMENT"),
            @JsonSubTypes.Type(value = ExamDetails.class, name = "EXAM"),
            @JsonSubTypes.Type(value = StudySessionDetails.class, name = "STUDY_SESSION")
    })
    sealed interface TaskDetails permits GeneralDetails, AssignmentDetails, ExamDetails, StudySessionDetails {
        Task create(TaskRecord record);

        static TaskDetails from(Task task) {
            return switch (task) {
                case GeneralTask ignored -> new GeneralDetails();
                case Assignment item -> new AssignmentDetails(item.getSubmissionType(), item.getAttachmentPath());
                case Exam item -> new ExamDetails(item.getExamType(), item.getPreparationStatus());
                case StudySession item -> new StudySessionDetails(item.getPlannedDuration(), item.getActualDuration());
                default -> throw new IllegalArgumentException("Unknown task class: " + task.getClass().getName());
            };
        }
    }

    record GeneralDetails() implements TaskDetails {
        @Override
        public Task create(TaskRecord record) {
            return new GeneralTask(record.id(), record.title(), record.description(), record.subjectId(),
                    record.createdDate(), record.dueDate(), record.priority(), record.status());
        }
    }

    record AssignmentDetails(String submissionType, String attachmentPath) implements TaskDetails {
        @Override
        public Task create(TaskRecord record) {
            return new Assignment(record.id(), record.title(), record.description(), record.subjectId(),
                    record.createdDate(), record.dueDate(), record.priority(), record.status(),
                    submissionType, attachmentPath);
        }
    }

    record ExamDetails(String examType, String preparationStatus) implements TaskDetails {
        @Override
        public Task create(TaskRecord record) {
            return new Exam(record.id(), record.title(), record.description(), record.subjectId(),
                    record.createdDate(), record.dueDate(), record.priority(), record.status(), examType, preparationStatus);
        }
    }

    record StudySessionDetails(Duration plannedDuration, Duration actualDuration) implements TaskDetails {
        @Override
        public Task create(TaskRecord record) {
            return new StudySession(record.id(), record.title(), record.description(), record.subjectId(),
                    record.createdDate(), record.dueDate(), record.priority(), record.status(),
                    plannedDuration, actualDuration);
        }
    }
}
