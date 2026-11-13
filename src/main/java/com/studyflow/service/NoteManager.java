package com.studyflow.service;

import com.studyflow.interfaces.Storage;
import com.studyflow.model.Note;
import com.studyflow.model.Subject;
import com.studyflow.utils.IdGenerator;
import com.studyflow.utils.ValidationUtils;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Manages plain-text notes, timestamps and optional subject references. */
public final class NoteManager {
    private final Clock clock;
    private final Function<String, Optional<Subject>> subjectLookup;
    private final AcademicCollection<Note> notes;

    public NoteManager(Clock clock, Function<String, Optional<Subject>> subjectLookup,
                       Storage<List<Note>> storage) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.subjectLookup = Objects.requireNonNull(subjectLookup, "subjectLookup");
        notes = new AcademicCollection<>(storage, Note::copy);
        notes.findAll().forEach(note -> validateSubject(note.getSubjectId()));
    }

    public Note create(String title, String content, String subjectId, List<String> tags) {
        LocalDateTime now = LocalDateTime.now(clock);
        return notes.insert(new Note(IdGenerator.next(), title, content, validateSubject(subjectId),
                now, now, tags));
    }

    public Note update(String id, String title, String content, String subjectId, List<String> tags) {
        return notes.edit(id, "Note", note -> note.update(title, content, validateSubject(subjectId),
                tags, editTime(note)));
    }

    public Note assignSubject(String id, String subjectId) {
        return notes.editIfChanged(id, "Note", note -> {
            String checked = validateSubject(subjectId);
            if (Objects.equals(note.getSubjectId(), checked)) {
                return false;
            }
            note.update(note.getTitle(), note.getContent(), checked, note.getTags(), editTime(note));
            return true;
        });
    }

    public Note addTag(String id, String tag) {
        return notes.editIfChanged(id, "Note", note -> note.addTag(tag, editTime(note)));
    }

    public Optional<Note> findById(String id) {
        return notes.findById(id);
    }

    public List<Note> findAll() {
        return notes.findAll();
    }

    public List<Note> findBySubject(String subjectId) {
        String checked = ValidationUtils.requiredText(subjectId, "Subject ID");
        return findAll().stream().filter(note -> checked.equals(note.getSubjectId())).toList();
    }

    public List<Note> search(String query) {
        return findAll().stream().filter(note -> note.matchesSearch(query)).toList();
    }

    public boolean delete(String id) {
        return notes.delete(id);
    }

    private String validateSubject(String subjectId) {
        String checked = ValidationUtils.optionalId(subjectId, "Subject ID");
        if (checked != null && subjectLookup.apply(checked).isEmpty()) {
            throw new IllegalArgumentException("Unknown subject ID: " + checked);
        }
        return checked;
    }

    private LocalDateTime editTime(Note note) {
        LocalDateTime now = LocalDateTime.now(clock);
        return now.isBefore(note.getUpdatedAt()) ? note.getUpdatedAt() : now;
    }
}
