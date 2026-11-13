package com.studyflow.service;

import com.studyflow.factory.TaskDraft;
import com.studyflow.factory.TaskFactory;
import com.studyflow.interfaces.Storage;
import com.studyflow.model.Priority;
import com.studyflow.model.Recurrence;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.strategy.TaskSortStrategy;
import com.studyflow.utils.ValidationUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Manages a forest of academic tasks independently of the interface and storage implementation. */
public final class TaskManager {
    private static final long UPCOMING_DAYS = 7;

    private final Clock clock;
    private final Supplier<Priority> defaultPriority;
    private final Function<String, Optional<Subject>> subjectLookup;
    private final Storage<List<Task>> storage;
    private List<Task> tasks;

    public TaskManager(Clock clock, Priority defaultPriority, Function<String, Optional<Subject>> subjectLookup,
                       Storage<List<Task>> storage) {
        this(clock, () -> defaultPriority, subjectLookup, storage);
    }

    public TaskManager(Clock clock, Supplier<Priority> defaultPriority,
                       Function<String, Optional<Subject>> subjectLookup, Storage<List<Task>> storage) {
        this.clock = ValidationUtils.required(clock, "Clock");
        this.defaultPriority = ValidationUtils.required(defaultPriority, "Default priority supplier");
        this.subjectLookup = ValidationUtils.required(subjectLookup, "Subject lookup");
        this.storage = ValidationUtils.required(storage, "Task storage");
        List<Task> loaded = ValidationUtils.required(storage.load(), "Stored tasks");
        validateForest(loaded);
        this.tasks = copies(loaded);
    }

    public synchronized Task create(TaskDraft draft) {
        return add(TaskFactory.create(draft, LocalDate.now(clock), defaultPriority.get()));
    }

    public synchronized Task quickAdd(String title, String subjectId, LocalDate dueDate, Priority priority) {
        return create(new TaskDraft(title, "", subjectId, dueDate, priority, new TaskDraft.General()));
    }

    public synchronized Task add(Task task) {
        ValidationUtils.required(task, "Task");
        List<Task> candidate = new ArrayList<>(tasks);
        Task added = task.copy();
        candidate.add(added);
        commit(candidate);
        return added.copy();
    }

    /** Looks up a root task. Descendants are addressed through their root. */
    public synchronized Optional<Task> findById(String id) {
        String taskId = ValidationUtils.requiredText(id, "Task ID");
        return tasks.stream().filter(task -> taskId.equals(task.getId())).findFirst().map(Task::copy);
    }

    /** Returns detached root tasks in insertion order. */
    public synchronized List<Task> findAll() {
        return copies(tasks);
    }

    /** Updates task fields on a detached candidate. Status changes use {@link #updateStatus}. */
    public synchronized Task update(String id, Consumer<Task> editor) {
        ValidationUtils.required(editor, "Task edit");
        int index = requireIndex(id);
        List<Task> baseline = tasks;
        Task current = baseline.get(index);
        Task edited = current.copy();
        editor.accept(edited);
        requireCurrentSnapshot(baseline);
        requireUnchangedLifecycle(current.getStatus(), current.hasAdvancedRecurrence(), edited);
        replace(index, edited);
        return edited.copy();
    }

    public synchronized void delete(String id) {
        List<Task> candidate = new ArrayList<>(tasks);
        candidate.remove(requireIndex(id));
        commit(candidate);
    }

    public Task setPriority(String id, Priority priority) {
        return update(id, task -> task.setPriority(priority));
    }

    public Task setRecurrence(String id, Recurrence recurrence) {
        return update(id, task -> task.setRecurrence(recurrence));
    }

    public Task assignSubject(String id, String subjectId) {
        return update(id, task -> task.setSubjectId(subjectId));
    }

    public synchronized Task updateStatus(String id, TaskStatus status) {
        int index = requireIndex(id);
        Task current = tasks.get(index);
        if (current.getStatus() == status) {
            return current.copy();
        }
        Task edited = current.copy();
        edited.updateStatus(status);
        List<Task> candidate = new ArrayList<>(tasks);
        if (status == TaskStatus.COMPLETED && edited.getRecurrence() != Recurrence.NONE
                && !edited.hasAdvancedRecurrence()) {
            candidate.add(TaskFactory.nextOccurrence(edited, LocalDate.now(clock)));
            edited.markRecurrenceAdvanced();
        }
        candidate.set(index, edited);
        commit(candidate);
        return edited.copy();
    }

    public Task markCompleted(String id) {
        return updateStatus(id, TaskStatus.COMPLETED);
    }

    public Task createSubtask(String rootId, TaskDraft draft) {
        return createSubtask(rootId, rootId, draft);
    }

    /** Adds a child to the selected root or descendant. */
    public synchronized Task createSubtask(String rootId, String parentId, TaskDraft draft) {
        int index = requireIndex(rootId);
        String parentTaskId = ValidationUtils.requiredText(parentId, "Parent task ID");
        Task root = tasks.get(index).copy();
        Task child = TaskFactory.create(draft, LocalDate.now(clock), defaultPriority.get());
        if (root.getId().equals(parentTaskId)) {
            root.addSubtask(child);
        } else {
            requireEditedSubtask(root, parentTaskId, parent -> parent.addSubtask(child));
        }
        replace(index, root);
        return child.copy();
    }

    public List<Task> findSubtasks(String rootId) {
        return findSubtasks(rootId, rootId);
    }

    /** Returns the direct children of the selected root or descendant. */
    public synchronized List<Task> findSubtasks(String rootId, String parentId) {
        Task root = tasks.get(requireIndex(rootId));
        String parentTaskId = ValidationUtils.requiredText(parentId, "Parent task ID");
        Task parent = root.getId().equals(parentTaskId) ? root : requireSubtask(root, parentTaskId);
        return parent.getSubtasks();
    }

    public synchronized void removeSubtask(String rootId, String childId) {
        int index = requireIndex(rootId);
        String subtaskId = ValidationUtils.requiredText(childId, "Subtask ID");
        Task root = tasks.get(index).copy();
        if (!root.removeSubtask(subtaskId)) {
            throw new TaskNotFoundException(subtaskId);
        }
        replace(index, root);
    }

    public synchronized Task updateSubtask(String rootId, String childId, Consumer<Task> editor) {
        ValidationUtils.required(editor, "Subtask edit");
        int index = requireIndex(rootId);
        String subtaskId = ValidationUtils.requiredText(childId, "Subtask ID");
        List<Task> baseline = tasks;
        Task root = baseline.get(index).copy();
        requireEditedSubtask(root, subtaskId, child -> {
            TaskStatus previousStatus = child.getStatus();
            boolean previouslyAdvanced = child.hasAdvancedRecurrence();
            editor.accept(child);
            requireUnchangedLifecycle(previousStatus, previouslyAdvanced, child);
        });
        requireCurrentSnapshot(baseline);
        replace(index, root);
        return requireSubtask(root, subtaskId);
    }

    public Task assignSubtaskSubject(String rootId, String childId, String subjectId) {
        return updateSubtask(rootId, childId, task -> task.setSubjectId(subjectId));
    }

    public synchronized Task updateSubtaskStatus(String rootId, String childId, TaskStatus status) {
        int index = requireIndex(rootId);
        String subtaskId = ValidationUtils.requiredText(childId, "Subtask ID");
        Task root = tasks.get(index).copy();
        Task child = requireSubtask(root, subtaskId);
        if (child.getStatus() == status) {
            return child;
        }
        requireEditedSubtask(root, subtaskId, edited -> edited.updateStatus(status));
        replace(index, root);
        return requireSubtask(root, subtaskId);
    }

    /** Includes matching roots and descendants so subject views can count all associated tasks. */
    public synchronized List<Task> findBySubject(String id) {
        String subjectId = ValidationUtils.requiredText(id, "Subject ID");
        return tasks.stream().flatMap(TaskManager::tree)
                .filter(task -> subjectId.equals(task.getSubjectId())).map(Task::copy).toList();
    }

    public synchronized List<Task> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return tasks.stream().filter(task -> matchesSearch(task, query)).map(Task::copy).toList();
    }

    public synchronized List<Task> filter(TaskFilter filter) {
        ValidationUtils.required(filter, "Task filter");
        LocalDate today = LocalDate.now(clock);
        return tasks.stream().filter(task -> filter.matches(task, today)).map(Task::copy).toList();
    }

    public List<Task> sort(TaskSortStrategy strategy) {
        ValidationUtils.required(strategy, "Task sort strategy");
        return findAll().stream().sorted(strategy).toList();
    }

    public synchronized List<Task> today() {
        LocalDate today = LocalDate.now(clock);
        return tasks.stream().filter(TaskManager::isIncomplete)
                .filter(task -> today.equals(task.getDueDate())).sorted().map(Task::copy).toList();
    }

    public synchronized List<Task> upcoming() {
        LocalDate today = LocalDate.now(clock);
        return tasks.stream().filter(TaskManager::isIncomplete).filter(task -> isUpcoming(task, today))
                .sorted().map(Task::copy).toList();
    }

    public synchronized List<Task> smartPriority() {
        LocalDate today = LocalDate.now(clock);
        Comparator<Task> order = Comparator.comparingInt((Task task) -> urgencyBand(task, today))
                .thenComparing(Task::getPriority).thenComparing(Comparator.naturalOrder());
        return tasks.stream().filter(TaskManager::isIncomplete).sorted(order).map(Task::copy).toList();
    }

    public synchronized Map<TaskStatus, List<Task>> kanban() {
        Map<TaskStatus, List<Task>> groups = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            groups.put(status, new ArrayList<>());
        }
        for (Task task : tasks) {
            groups.get(task.getStatus()).add(task.copy());
        }
        groups.replaceAll((status, group) -> List.copyOf(group));
        return Collections.unmodifiableMap(groups);
    }

    private int requireIndex(String id) {
        String taskId = ValidationUtils.requiredText(id, "Task ID");
        for (int index = 0; index < tasks.size(); index++) {
            if (taskId.equals(tasks.get(index).getId())) {
                return index;
            }
        }
        throw new TaskNotFoundException(taskId);
    }

    private void replace(int index, Task task) {
        List<Task> candidate = new ArrayList<>(tasks);
        candidate.set(index, task);
        commit(candidate);
    }

    private void requireCurrentSnapshot(List<Task> baseline) {
        if (tasks != baseline) {
            throw new ConcurrentModificationException("Task data changed during the edit. Retry the edit.");
        }
    }

    private void commit(List<Task> candidate) {
        validateForest(candidate);
        List<Task> committed = copies(candidate);
        storage.save(copies(committed));
        tasks = committed;
    }

    private void validateForest(List<Task> roots) {
        Set<String> taskIds = new HashSet<>();
        Set<String> validatedSubjectIds = new HashSet<>();
        for (Task task : roots) {
            ValidationUtils.required(task, "Task");
            validateTree(task, true, taskIds, validatedSubjectIds);
        }
    }

    private void validateTree(Task task, boolean root, Set<String> taskIds, Set<String> validatedSubjectIds) {
        if (!taskIds.add(task.getId())) {
            throw new DuplicateTaskException(task.getId());
        }
        String subjectId = task.getSubjectId();
        if (subjectId != null && validatedSubjectIds.add(subjectId) && subjectLookup.apply(subjectId).isEmpty()) {
            throw new IllegalArgumentException("Unknown subject ID: " + subjectId);
        }
        if (!root && (task.getRecurrence() != Recurrence.NONE || task.hasAdvancedRecurrence())) {
            throw new IllegalArgumentException("Only root tasks can recur: " + task.getId());
        }
        if (task.getRecurrence() != Recurrence.NONE && task.getDueDate() == null) {
            throw new IllegalArgumentException("A recurring task needs a deadline: " + task.getId());
        }
        for (Task child : task.getSubtasks()) {
            validateTree(child, false, taskIds, validatedSubjectIds);
        }
    }

    private static void requireUnchangedLifecycle(TaskStatus previousStatus, boolean previouslyAdvanced, Task task) {
        if (task.getStatus() != previousStatus || task.hasAdvancedRecurrence() != previouslyAdvanced) {
            throw new IllegalArgumentException("Use a status operation to change the task lifecycle.");
        }
    }

    private static void requireEditedSubtask(Task root, String id, Consumer<Task> editor) {
        if (!root.editSubtask(id, editor)) {
            throw new TaskNotFoundException(id);
        }
    }

    private static Task requireSubtask(Task root, String id) {
        return root.getSubtasks().stream().flatMap(TaskManager::tree)
                .filter(task -> id.equals(task.getId())).findFirst()
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    private static Stream<Task> tree(Task task) {
        return Stream.concat(Stream.of(task), task.getSubtasks().stream().flatMap(TaskManager::tree));
    }

    private static List<Task> copies(List<Task> source) {
        return source.stream().map(Task::copy).toList();
    }

    private boolean matchesSearch(Task task, String query) {
        if (task.getSubjectId() == null) {
            return task.matchesSearch(query);
        }
        return subjectLookup.apply(task.getSubjectId()).map(subject -> {
            String[] fields = Stream.concat(Stream.of(subject.getName()), subject.getTags().stream())
                    .toArray(String[]::new);
            return task.matchesSearch(query, fields);
        }).orElseGet(() -> task.matchesSearch(query));
    }

    private static boolean isIncomplete(Task task) {
        return task.getStatus() != TaskStatus.COMPLETED;
    }

    private static boolean isUpcoming(Task task, LocalDate today) {
        long days = task.getDaysRemaining(today).orElse(0);
        return days > 0 && days <= UPCOMING_DAYS;
    }

    private static int urgencyBand(Task task, LocalDate today) {
        if (task.isOverdue(today)) {
            return 0;
        }
        if (today.equals(task.getDueDate())) {
            return 1;
        }
        return isUpcoming(task, today) ? 2 : 3;
    }
}
