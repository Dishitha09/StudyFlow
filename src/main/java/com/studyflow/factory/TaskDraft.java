package com.studyflow.factory;

import com.studyflow.model.Priority;
import com.studyflow.model.TaskType;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

/** Creation data for a new task, including the fields specific to its type. */
public record TaskDraft(String title, String description, String subjectId, LocalDate dueDate,
                        Priority priority, Details details) {
    public TaskDraft {
        Objects.requireNonNull(details, "Task details");
    }

    public sealed interface Details permits General, AssignmentDetails, ExamDetails, StudySessionDetails {
        TaskType type();
    }

    public record General() implements Details {
        @Override
        public TaskType type() {
            return TaskType.GENERAL;
        }
    }

    public record AssignmentDetails(String submissionType, String attachmentPath) implements Details {
        @Override
        public TaskType type() {
            return TaskType.ASSIGNMENT;
        }
    }

    public record ExamDetails(String examType, String preparationStatus) implements Details {
        @Override
        public TaskType type() {
            return TaskType.EXAM;
        }
    }

    public record StudySessionDetails(Duration plannedDuration, Duration actualDuration) implements Details {
        @Override
        public TaskType type() {
            return TaskType.STUDY_SESSION;
        }
    }
}
