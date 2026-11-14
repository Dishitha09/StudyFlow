package com.studyflow.service;

import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.model.TaskType;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Produces at most one reminder per task, category and deadline during each local day. */
public final class ReminderService {
    private final Set<Key> notified = new HashSet<>();
    private LocalDate date;

    public List<String> collect(List<Task> tasks, LocalDate today, boolean enabled) {
        if (!enabled) {
            return List.of();
        }
        if (!today.equals(date)) {
            date = today;
            notified.clear();
        }
        return tasks.stream().filter(task -> task.getStatus() != TaskStatus.COMPLETED)
                .filter(task -> task.getDueDate() != null)
                .map(task -> reminder(task, today)).filter(java.util.Objects::nonNull).toList();
    }

    private String reminder(Task task, LocalDate today) {
        long days = task.getDaysRemaining(today).orElseThrow();
        String category = days < 0 ? "Overdue" : days == 0 ? "Due today"
                : days <= 7 ? (task.getType() == TaskType.EXAM ? "Exam approaching" : "Upcoming") : null;
        if (category == null || !notified.add(new Key(task.getId(), task.getDueDate(), category))) {
            return null;
        }
        return category + " · " + task.getTitle() + " (" + task.getDueDate() + ")";
    }

    private record Key(String id, LocalDate due, String category) { }
}
