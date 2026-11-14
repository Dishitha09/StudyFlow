package com.studyflow.controller;

import com.studyflow.config.StudyFlowContext;
import com.studyflow.factory.TaskDraft;
import com.studyflow.model.Assignment;
import com.studyflow.model.Exam;
import com.studyflow.model.Note;
import com.studyflow.model.Priority;
import com.studyflow.model.StudySession;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.model.TaskType;
import com.studyflow.model.Theme;
import com.studyflow.model.Workspace;
import com.studyflow.service.TaskFilter;
import com.studyflow.service.TaskManager;
import com.studyflow.strategy.SortByCreationDate;
import com.studyflow.strategy.SortByDeadline;
import com.studyflow.strategy.SortByPriority;
import com.studyflow.strategy.SortByTitle;
import com.studyflow.strategy.TaskSortStrategy;
import com.studyflow.utils.ValidationUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Adapts desktop actions to the existing services and delivers results on the EDT. */
public final class StudyFlowController {
    private final StudyFlowContext context;
    private final Executor worker;
    private final Clock clock;

    public StudyFlowController(StudyFlowContext context, Executor worker, Clock clock) {
        this.context = context;
        this.worker = worker;
        this.clock = clock;
    }

    public void load(TaskQuery query, Consumer<Records> success, Consumer<String> failure) {
        submit(() -> snapshot(query), success, failure);
    }

    public void query(TaskQuery query, Consumer<List<Task>> success, Consumer<String> failure) {
        submit(() -> taskRows(query), success, failure);
    }

    public void change(Runnable command, TaskQuery query, Consumer<Records> success, Consumer<String> failure) {
        submit(() -> {
            command.run();
            return snapshot(query);
        }, success, failure);
    }

    public void createTask(TaskDraft draft) {
        context.taskManager().create(draft);
    }

    public void updateTask(String id, TaskDraft draft) {
        context.taskManager().update(id, task -> {
            if (task.getType() != draft.details().type()) {
                throw new IllegalArgumentException("An existing task keeps its original type.");
            }
            task.setTitle(draft.title());
            task.setDescription(draft.description());
            task.setSubjectId(draft.subjectId());
            task.setDueDate(draft.dueDate());
            task.setPriority(ValidationUtils.required(draft.priority(), "Task priority"));
            switch (draft.details()) {
                case TaskDraft.General ignored -> { }
                case TaskDraft.AssignmentDetails details -> {
                    Assignment assignment = (Assignment) task;
                    assignment.setSubmissionType(details.submissionType());
                    assignment.setAttachmentPath(details.attachmentPath());
                }
                case TaskDraft.ExamDetails details -> {
                    Exam exam = (Exam) task;
                    exam.setExamType(details.examType());
                    exam.setPreparationStatus(details.preparationStatus());
                }
                case TaskDraft.StudySessionDetails details -> {
                    StudySession session = (StudySession) task;
                    session.setPlannedDuration(details.plannedDuration());
                    session.setActualDuration(details.actualDuration());
                }
            }
        });
    }

    public void deleteTask(String id) {
        context.taskManager().delete(id);
    }

    public void changeStatus(String id, TaskStatus status) {
        context.taskManager().updateStatus(id, status);
    }

    public void changePriority(String id, Priority priority) {
        context.taskManager().setPriority(id, priority);
    }

    public void createSubject(String name, String code, String description, List<String> tags) {
        context.subjectManager().create(name, code, description, tags);
    }

    public void updateSubject(String id, String name, String code, String description, List<String> tags) {
        context.subjectManager().update(id, name, code, description, tags);
    }

    public void deleteSubject(String id) {
        context.subjectManager().delete(id);
    }

    public void createWorkspace(String name, String description, List<String> members) {
        context.workspaceManager().create(name, description, members);
    }

    public void updateWorkspace(String id, String name, String description, List<String> members) {
        context.workspaceManager().update(id, name, description, members);
    }

    public void deleteWorkspace(String id) {
        context.workspaceManager().delete(id);
    }

    public void createNote(String title, String content, String subjectId, List<String> tags) {
        context.noteManager().create(title, content, subjectId, tags);
    }

    public void updateNote(String id, String title, String content, String subjectId, List<String> tags) {
        context.noteManager().update(id, title, content, subjectId, tags);
    }

    public void deleteNote(String id) {
        context.noteManager().delete(id);
    }

    public void changeTheme(Theme theme) {
        var settings = context.settings();
        settings.setTheme(theme);
        context.saveSettings(settings);
    }

    private Records snapshot(TaskQuery query) {
        TaskManager tasks = context.taskManager();
        LocalDate today = LocalDate.now(clock);
        List<Task> all = tasks.findAll();
        List<Subject> subjects = context.subjectManager().findAll();
        List<Task> upcoming = tasks.upcoming();
        int completed = Math.toIntExact(all.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count());
        int overdue = Math.toIntExact(all.stream().filter(task -> task.isOverdue(today)).count());
        int assignments = Math.toIntExact(upcoming.stream().filter(task -> task.getType() == TaskType.ASSIGNMENT).count());
        DashboardData dashboard = new DashboardData(today, all.size(), completed, all.size() - completed, overdue,
                assignments, tasks.today(), upcoming, tasks.smartPriority(), subjects);
        return new Records(dashboard, taskRows(query), subjects, context.workspaceManager().findAll(),
                context.noteManager().findAll(), context.settings().getTheme());
    }

    private List<Task> taskRows(TaskQuery query) {
        TaskManager tasks = context.taskManager();
        List<Task> filtered = tasks.filter(query.filter());
        if (!query.search().isBlank()) {
            Set<String> matches = new HashSet<>();
            tasks.search(query.search()).forEach(task -> matches.add(task.getId()));
            filtered = filtered.stream().filter(task -> matches.contains(task.getId())).toList();
        }
        return filtered.stream().sorted(query.order().strategy).toList();
    }

    private <T> void submit(Supplier<T> work, Consumer<T> success, Consumer<String> failure) {
        try {
            worker.execute(() -> {
                try {
                    T result = work.get();
                    SwingUtilities.invokeLater(() -> success.accept(result));
                } catch (RuntimeException error) {
                    String message = error.getMessage() == null ? "StudyFlow could not complete the request."
                            : error.getMessage();
                    SwingUtilities.invokeLater(() -> failure.accept(message));
                }
            });
        } catch (RejectedExecutionException error) {
            SwingUtilities.invokeLater(() -> failure.accept("StudyFlow is closing."));
        }
    }

    public enum TaskOrder {
        DEADLINE(new SortByDeadline()), PRIORITY(new SortByPriority()),
        TITLE(new SortByTitle()), CREATED(new SortByCreationDate());

        private final TaskSortStrategy strategy;

        TaskOrder(TaskSortStrategy strategy) {
            this.strategy = strategy;
        }
    }

    public record TaskQuery(String search, TaskFilter filter, TaskOrder order) {
        public static TaskQuery initial() {
            return new TaskQuery("", new TaskFilter(null, null, null, null, null, null, null), TaskOrder.DEADLINE);
        }
    }

    public record DashboardData(LocalDate date, int total, int completed, int pending, int overdue,
                                int upcomingAssignments, List<Task> today, List<Task> upcoming,
                                List<Task> smartPriority, List<Subject> subjects) { }

    public record Records(DashboardData dashboard, List<Task> tasks, List<Subject> subjects,
                          List<Workspace> workspaces, List<Note> notes, Theme theme) { }
}
