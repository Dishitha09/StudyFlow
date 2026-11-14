package com.studyflow.view.planning;

import com.studyflow.model.Priority;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.model.TaskType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class PlanningLabels {
    private PlanningLabels() {
    }

    static Map<String, String> subjectNames(List<Subject> subjects) {
        return subjects.stream().collect(Collectors.toMap(Subject::getId, Subject::getName));
    }

    static String subject(Map<String, String> names, Task task) {
        String subjectId = task.getSubjectId();
        return subjectId == null ? "No subject" : names.getOrDefault(subjectId, "No subject");
    }

    static String priority(Priority priority) {
        return switch (priority) {
            case HIGH -> "High";
            case MEDIUM -> "Medium";
            case LOW -> "Low";
        };
    }

    static String status(TaskStatus status) {
        return switch (status) {
            case TODO -> "To do";
            case IN_PROGRESS -> "In progress";
            case COMPLETED -> "Completed";
        };
    }

    static String type(TaskType type) {
        return switch (type) {
            case GENERAL -> "Task";
            case ASSIGNMENT -> "Assignment";
            case EXAM -> "Exam";
            case STUDY_SESSION -> "Study session";
        };
    }
}
