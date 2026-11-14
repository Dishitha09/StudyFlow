package com.studyflow.service;

import com.studyflow.model.Note;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.Workspace;
import com.studyflow.utils.SearchText;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Queries the academic managers and returns results grouped by record type. */
public final class SearchService {
    private final TaskManager tasks;
    private final SubjectManager subjects;
    private final NoteManager notes;
    private final Supplier<List<Workspace>> workspaces;

    public SearchService(TaskManager tasks, SubjectManager subjects, NoteManager notes) {
        this(tasks, subjects, notes, List::of);
    }

    public SearchService(TaskManager tasks, SubjectManager subjects, NoteManager notes, WorkspaceManager workspaces) {
        this(tasks, subjects, notes, Objects.requireNonNull(workspaces, "workspaces")::findAll);
    }

    private SearchService(TaskManager tasks, SubjectManager subjects, NoteManager notes,
                          Supplier<List<Workspace>> workspaces) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.subjects = Objects.requireNonNull(subjects, "subjects");
        this.notes = Objects.requireNonNull(notes, "notes");
        this.workspaces = workspaces;
    }

    public SearchResults search(String query) {
        List<Workspace> matchedWorkspaces = workspaces.get().stream()
                .filter(workspace -> SearchText.matches(query, workspace.getName(), workspace.getDescription()))
                .toList();
        return new SearchResults(tasks.search(query), subjects.search(query), notes.search(query), matchedWorkspaces);
    }

    public record SearchResults(List<Task> tasks, List<Subject> subjects, List<Note> notes, List<Workspace> workspaces) {
        public SearchResults(List<Task> tasks, List<Subject> subjects, List<Note> notes) {
            this(tasks, subjects, notes, List.of());
        }

        public SearchResults {
            tasks = List.copyOf(tasks);
            subjects = List.copyOf(subjects);
            notes = List.copyOf(notes);
            workspaces = List.copyOf(workspaces);
        }
    }
}
