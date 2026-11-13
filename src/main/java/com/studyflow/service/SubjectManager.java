package com.studyflow.service;

import com.studyflow.interfaces.Storage;
import com.studyflow.model.Note;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.Workspace;
import com.studyflow.utils.IdGenerator;
import com.studyflow.utils.ValidationUtils;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;

/** Manages course records and their relationships to the academic workspace. */
public final class SubjectManager {
    private final AcademicCollection<Subject> subjects;
    private final TaskManager taskManager;
    private final Supplier<List<Note>> notes;
    private final Supplier<List<Workspace>> workspaces;

    public SubjectManager(Storage<List<Subject>> storage, TaskManager taskManager,
                          Supplier<List<Note>> notes, Supplier<List<Workspace>> workspaces) {
        this.subjects = new AcademicCollection<>(storage, Subject::copy);
        this.taskManager = ValidationUtils.required(taskManager, "Task manager");
        this.notes = ValidationUtils.required(notes, "Note lookup");
        this.workspaces = ValidationUtils.required(workspaces, "Workspace lookup");
    }

    public Subject create(String name, String code, String description, List<String> tags) {
        return subjects.insert(new Subject(IdGenerator.next(), name, code, description, tags));
    }

    public Subject update(String id, String name, String code, String description, List<String> tags) {
        return subjects.edit(id, "Subject", subject -> {
            subject.setName(name);
            subject.setSubjectCode(code);
            subject.setDescription(description);
            subject.setTags(tags);
        });
    }

    public Optional<Subject> findById(String id) {
        return subjects.findById(id);
    }

    public List<Subject> findAll() {
        return subjects.findAll();
    }

    public List<Subject> search(String query) {
        return findAll().stream().filter(subject -> subject.matchesSearch(query)).toList();
    }

    /** Removes a subject only after all referencing records are reassigned or detached. */
    public void delete(String id) {
        Subject subject = subjects.require(id, "Subject");
        String subjectId = subject.getId();
        List<Task> tasks = taskManager.findBySubject(subjectId);
        if (!tasks.isEmpty()) {
            throw referencedSubject(subject, "task", tasks.getFirst().getTitle());
        }
        for (Note note : notes.get()) {
            if (subjectId.equals(note.getSubjectId())) {
                throw referencedSubject(subject, "note", note.getTitle());
            }
        }
        for (Workspace workspace : workspaces.get()) {
            if (workspace.getSubjectIds().contains(subjectId)) {
                throw referencedSubject(subject, "workspace", workspace.getName());
            }
        }
        if (!subjects.delete(subjectId)) {
            throw new NoSuchElementException("Subject not found: " + subjectId);
        }
    }

    public Task assignTask(String taskId, String subjectId) {
        return taskManager.assignSubject(taskId, subjectId);
    }

    public Task assignSubtask(String rootTaskId, String childTaskId, String subjectId) {
        return taskManager.assignSubtaskSubject(rootTaskId, childTaskId, subjectId);
    }

    public List<Task> findTasks(String subjectId) {
        return taskManager.findBySubject(subjects.require(subjectId, "Subject").getId());
    }

    public int countTasks(String subjectId) {
        return findTasks(subjectId).size();
    }

    private static IllegalStateException referencedSubject(Subject subject, String kind, String name) {
        return new IllegalStateException("Cannot delete subject '" + subject.getName()
                + "' while it is referenced by " + kind + " '" + name + "'.");
    }
}
