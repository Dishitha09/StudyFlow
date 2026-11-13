package com.studyflow.model;

import com.studyflow.interfaces.Identifiable;
import com.studyflow.interfaces.Prioritizable;
import com.studyflow.interfaces.Searchable;
import com.studyflow.utils.SearchText;
import com.studyflow.utils.ValidationUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;

/** Common state and lifecycle for the four kinds of academic task. */
public abstract class Task implements Identifiable, Comparable<Task>, Prioritizable, Searchable {
    private static final Comparator<Task> DEADLINE_ORDER = Comparator
            .comparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Task::getId);

    private final String id;
    private String title;
    private String description;
    private String subjectId;
    private final LocalDate createdDate;
    private LocalDate dueDate;
    private Priority priority;
    private TaskStatus status;
    private Recurrence recurrence = Recurrence.NONE;
    private boolean recurrenceAdvanced;
    private final List<Task> subtasks = new ArrayList<>();

    protected Task(String id, String title, String description, String subjectId, LocalDate createdDate,
                   LocalDate dueDate, Priority priority, TaskStatus status) {
        this.id = ValidationUtils.requiredText(id, "Task ID");
        setTitle(title);
        setDescription(description);
        setSubjectId(subjectId);
        this.createdDate = ValidationUtils.required(createdDate, "Creation date");
        setDueDate(dueDate);
        setPriority(priority);
        this.status = ValidationUtils.required(status, "Task status");
    }

    protected Task(Task source) {
        this(source.id, source.title, source.description, source.subjectId, source.createdDate,
                source.dueDate, source.priority, source.status);
        recurrence = source.recurrence;
        recurrenceAdvanced = source.recurrenceAdvanced;
        for (Task child : source.subtasks) {
            subtasks.add(child.copy());
        }
    }

    @Override
    public final String getId() {
        return id;
    }

    public final String getTitle() {
        return title;
    }

    public final void setTitle(String title) {
        this.title = ValidationUtils.requiredText(title, "Task title");
    }

    public final String getDescription() {
        return description;
    }

    public final void setDescription(String description) {
        this.description = ValidationUtils.textOrEmpty(description);
    }

    public final String getSubjectId() {
        return subjectId;
    }

    public final void setSubjectId(String subjectId) {
        this.subjectId = ValidationUtils.optionalId(subjectId, "Subject ID");
    }

    public final LocalDate getCreatedDate() {
        return createdDate;
    }

    public final LocalDate getDueDate() {
        return dueDate;
    }

    public final void setDueDate(LocalDate dueDate) {
        if (dueDate == null && recurrence != Recurrence.NONE) {
            throw new IllegalArgumentException("A recurring task requires a deadline.");
        }
        this.dueDate = dueDate;
    }

    @Override
    public final Priority getPriority() {
        return priority;
    }

    public final void setPriority(Priority priority) {
        this.priority = ValidationUtils.required(priority, "Task priority");
    }

    public final TaskStatus getStatus() {
        return status;
    }

    public final void updateStatus(TaskStatus nextStatus) {
        ValidationUtils.required(nextStatus, "Task status");
        if (status == TaskStatus.COMPLETED && nextStatus == TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("Reopen a completed task to TODO before starting it.");
        }
        status = nextStatus;
    }

    public final void markCompleted() {
        updateStatus(TaskStatus.COMPLETED);
    }

    public final Recurrence getRecurrence() {
        return recurrence;
    }

    public final void setRecurrence(Recurrence recurrence) {
        ValidationUtils.required(recurrence, "Recurrence");
        if (recurrence != Recurrence.NONE && dueDate == null) {
            throw new IllegalArgumentException("A recurring task requires a deadline.");
        }
        this.recurrence = recurrence;
    }

    public final boolean hasAdvancedRecurrence() {
        return recurrenceAdvanced;
    }

    /** Records that this occurrence has already created its successor. */
    public final void markRecurrenceAdvanced() {
        recurrenceAdvanced = true;
    }

    public final boolean isOverdue() {
        return isOverdue(LocalDate.now());
    }

    public final boolean isOverdue(LocalDate today) {
        ValidationUtils.required(today, "Reference date");
        return status != TaskStatus.COMPLETED && dueDate != null && dueDate.isBefore(today);
    }

    public final OptionalLong getDaysRemaining() {
        return getDaysRemaining(LocalDate.now());
    }

    public final OptionalLong getDaysRemaining(LocalDate today) {
        ValidationUtils.required(today, "Reference date");
        return dueDate == null ? OptionalLong.empty() : OptionalLong.of(ChronoUnit.DAYS.between(today, dueDate));
    }

    /** Returns detached child trees in insertion order. */
    public final List<Task> getSubtasks() {
        return subtasks.stream().map(Task::copy).toList();
    }

    /** Attaches an independent subtree whose IDs must be new within this tree. */
    public final void addSubtask(Task task) {
        ValidationUtils.required(task, "Subtask");
        Set<String> ids = new HashSet<>();
        collectIds(ids, null);
        task.collectIds(ids, null);
        subtasks.add(task.copy());
    }

    /** Removes the descendant with this ID and all of its children. */
    public final boolean removeSubtask(String id) {
        String childId = ValidationUtils.requiredText(id, "Subtask ID");
        Task parent = findParentOf(childId);
        return parent != null && parent.subtasks.removeIf(child -> child.id.equals(childId));
    }

    /** Edits a detached descendant and replaces it in place after the entire tree passes validation. */
    public final boolean editSubtask(String id, Consumer<Task> edit) {
        String childId = ValidationUtils.requiredText(id, "Subtask ID");
        ValidationUtils.required(edit, "Subtask edit");
        Task parent = findParentOf(childId);
        if (parent == null) {
            return false;
        }
        var children = parent.subtasks.listIterator();
        while (children.hasNext()) {
            Task child = children.next();
            if (child.id.equals(childId)) {
                Task candidate = child.copy();
                edit.accept(candidate);
                Set<String> ids = new HashSet<>();
                collectIds(ids, child);
                candidate.collectIds(ids, null);
                children.set(candidate.copy());
                return true;
            }
        }
        return false;
    }

    private Task findParentOf(String childId) {
        for (Task child : subtasks) {
            if (child.id.equals(childId)) {
                return this;
            }
            Task parent = child.findParentOf(childId);
            if (parent != null) {
                return parent;
            }
        }
        return null;
    }

    private void collectIds(Set<String> ids, Task excludedSubtree) {
        if (this == excludedSubtree) {
            return;
        }
        if (!ids.add(id)) {
            throw new IllegalArgumentException("Duplicate task ID in subtask tree: " + id);
        }
        for (Task child : subtasks) {
            child.collectIds(ids, excludedSubtree);
        }
    }

    @Override
    public final boolean matchesSearch(String query) {
        return SearchText.matches(query, title, description);
    }

    /** Matches against task text and related text resolved by the caller. */
    public final boolean matchesSearch(String query, String... contextFields) {
        String[] fields = new String[contextFields.length + 2];
        fields[0] = title;
        fields[1] = description;
        System.arraycopy(contextFields, 0, fields, 2, contextFields.length);
        return SearchText.matches(query, fields);
    }

    /** Orders by deadline with undated tasks last, then by case-insensitive title and by ID. */
    @Override
    public final int compareTo(Task other) {
        return DEADLINE_ORDER.compare(this, other);
    }

    public abstract TaskType getType();

    /** Creates a detached copy with the same identity, concrete type and descendants. */
    public abstract Task copy();
}
