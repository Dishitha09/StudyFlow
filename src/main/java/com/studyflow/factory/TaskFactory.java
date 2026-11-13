package com.studyflow.factory;

import com.studyflow.model.Assignment;
import com.studyflow.model.Exam;
import com.studyflow.model.GeneralTask;
import com.studyflow.model.Priority;
import com.studyflow.model.StudySession;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.utils.IdGenerator;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Objects;

/** Builds concrete tasks and prepares a fresh copy for a recurring occurrence. */
public final class TaskFactory {
    private TaskFactory() {
    }

    public static Task create(TaskDraft draft, LocalDate createdDate, Priority defaultPriority) {
        String id = IdGenerator.next();
        Priority priority = draft.priority() == null ? defaultPriority : draft.priority();
        return switch (draft.details()) {
            case TaskDraft.General ignored -> new GeneralTask(id, draft.title(), draft.description(),
                    draft.subjectId(), createdDate, draft.dueDate(), priority, TaskStatus.TODO);
            case TaskDraft.AssignmentDetails details -> new Assignment(id, draft.title(), draft.description(),
                    draft.subjectId(), createdDate, draft.dueDate(), priority, TaskStatus.TODO,
                    details.submissionType(), details.attachmentPath());
            case TaskDraft.ExamDetails details -> new Exam(id, draft.title(), draft.description(),
                    draft.subjectId(), createdDate, draft.dueDate(), priority, TaskStatus.TODO,
                    details.examType(), Objects.requireNonNullElse(
                            details.preparationStatus(), Exam.DEFAULT_PREPARATION_STATUS));
            case TaskDraft.StudySessionDetails details -> new StudySession(id, draft.title(), draft.description(),
                    draft.subjectId(), createdDate, draft.dueDate(), priority, TaskStatus.TODO,
                    details.plannedDuration(), details.actualDuration());
        };
    }

    public static Task nextOccurrence(Task completed, LocalDate createdDate) {
        LocalDate dueDate;
        try {
            dueDate = completed.getRecurrence().nextAfter(completed.getDueDate());
        } catch (DateTimeException error) {
            throw new IllegalArgumentException("The next recurring deadline is outside the supported date range.",
                    error);
        }
        Task successor = copyForOccurrence(completed, createdDate, dueDate);
        successor.setRecurrence(completed.getRecurrence());
        return successor;
    }

    private static Task copyForOccurrence(Task source, LocalDate createdDate, LocalDate dueDate) {
        TaskDraft.Details details = switch (source) {
            case GeneralTask ignored -> new TaskDraft.General();
            case Assignment assignment -> new TaskDraft.AssignmentDetails(
                    assignment.getSubmissionType(), assignment.getAttachmentPath());
            case Exam exam -> new TaskDraft.ExamDetails(exam.getExamType(), Exam.DEFAULT_PREPARATION_STATUS);
            case StudySession session -> new TaskDraft.StudySessionDetails(session.getPlannedDuration(), null);
            default -> throw new IllegalArgumentException("Unsupported task type: " + source.getClass().getName());
        };
        TaskDraft draft = new TaskDraft(source.getTitle(), source.getDescription(), source.getSubjectId(), dueDate,
                source.getPriority(), details);
        Task copy = create(draft, createdDate, source.getPriority());
        for (Task child : source.getSubtasks()) {
            copy.addSubtask(copyForOccurrence(child, createdDate, child.getDueDate()));
        }
        return copy;
    }
}
