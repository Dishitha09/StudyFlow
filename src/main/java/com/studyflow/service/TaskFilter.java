package com.studyflow.service;

import com.studyflow.model.Priority;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.model.TaskType;
import com.studyflow.utils.ValidationUtils;

import java.time.LocalDate;

/** Optional criteria combined with AND. Both ends of a deadline range are inclusive. */
public record TaskFilter(String subjectId, Priority priority, TaskStatus status, TaskType type,
                         LocalDate dueFrom, LocalDate dueThrough, Boolean overdue) {
    public TaskFilter {
        subjectId = ValidationUtils.optionalId(subjectId, "Subject ID");
        if (dueFrom != null && dueThrough != null && dueFrom.isAfter(dueThrough)) {
            throw new IllegalArgumentException("Deadline range start must be on or before its end.");
        }
    }

    boolean matches(Task task, LocalDate today) {
        return (subjectId == null || subjectId.equals(task.getSubjectId()))
                && (priority == null || priority == task.getPriority())
                && (status == null || status == task.getStatus())
                && (type == null || type == task.getType())
                && matchesDeadline(task.getDueDate())
                && (overdue == null || overdue == task.isOverdue(today));
    }

    private boolean matchesDeadline(LocalDate deadline) {
        return (dueFrom == null || deadline != null && !deadline.isBefore(dueFrom))
                && (dueThrough == null || deadline != null && !deadline.isAfter(dueThrough));
    }
}
