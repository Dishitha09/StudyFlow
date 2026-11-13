package com.studyflow.controller;

import com.studyflow.model.Note;
import com.studyflow.model.Priority;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.Workspace;
import com.studyflow.service.NoteManager;
import com.studyflow.service.SubjectManager;
import com.studyflow.service.TaskManager;
import com.studyflow.service.WorkspaceManager;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Executes shell commands on the application worker and delivers results on the EDT. */
public final class StudyFlowController {
    private final TaskManager tasks;
    private final SubjectManager subjects;
    private final WorkspaceManager workspaces;
    private final NoteManager notes;
    private final Executor worker;

    public StudyFlowController(TaskManager tasks, SubjectManager subjects, WorkspaceManager workspaces,
                               NoteManager notes, Executor worker) {
        this.tasks = tasks;
        this.subjects = subjects;
        this.workspaces = workspaces;
        this.notes = notes;
        this.worker = worker;
    }

    public void load(Consumer<Records> success, Consumer<String> failure) {
        submit(() -> { }, success, failure);
    }

    public void createTask(String title, String subjectId, String date, Priority priority,
                           Consumer<Records> success, Consumer<String> failure) {
        submit(() -> tasks.quickAdd(title, subjectId, parseDate(date), priority), success, failure);
    }

    public void createSubject(String name, String code, Consumer<Records> success, Consumer<String> failure) {
        submit(() -> subjects.create(name, code, "", List.of()), success, failure);
    }

    public void createWorkspace(String name, String subjectId, Consumer<Records> success, Consumer<String> failure) {
        List<String> memberships = subjectId == null ? List.of() : List.of(subjectId);
        submit(() -> workspaces.create(name, "", memberships), success, failure);
    }

    public void createNote(String title, String content, String subjectId,
                           Consumer<Records> success, Consumer<String> failure) {
        submit(() -> notes.create(title, content, subjectId, List.of()), success, failure);
    }

    private void submit(Runnable action, Consumer<Records> success, Consumer<String> failure) {
        try {
            worker.execute(() -> {
                try {
                    action.run();
                    Records records = new Records(tasks.findAll(), subjects.findAll(), workspaces.findAll(),
                            notes.findAll());
                    SwingUtilities.invokeLater(() -> success.accept(records));
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

    private static LocalDate parseDate(String text) {
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Use YYYY-MM-DD for the deadline.", error);
        }
    }

    /** Detached records for the shell's lists and details. */
    public record Records(List<Task> tasks, List<Subject> subjects, List<Workspace> workspaces, List<Note> notes) {
    }
}
