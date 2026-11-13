package com.studyflow.service;

import com.studyflow.model.Note;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import java.util.List;
import java.util.Objects;

/** Queries the academic managers and returns results grouped by record type. */
public final class SearchService {
    private final TaskManager tasks;
    private final SubjectManager subjects;
    private final NoteManager notes;

    public SearchService(TaskManager tasks, SubjectManager subjects, NoteManager notes) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.subjects = Objects.requireNonNull(subjects, "subjects");
        this.notes = Objects.requireNonNull(notes, "notes");
    }

    public SearchResults search(String query) {
        return new SearchResults(tasks.search(query), subjects.search(query), notes.search(query));
    }

    public record SearchResults(List<Task> tasks, List<Subject> subjects, List<Note> notes) {
        public SearchResults {
            tasks = List.copyOf(tasks);
            subjects = List.copyOf(subjects);
            notes = List.copyOf(notes);
        }
    }
}
