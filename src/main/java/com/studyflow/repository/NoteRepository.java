package com.studyflow.repository;

import com.studyflow.model.Note;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/** Persists plain text notes, timestamps, and optional subject references. */
public final class NoteRepository extends JsonCollectionRepository<Note, NoteRepository.NoteRecord> {
    public NoteRepository(FileStorage storage) {
        super(storage, Path.of("notes", "notes.json"), NoteRecord.class, NoteRecord::from, NoteRecord::toDomain);
    }

    record NoteRecord(String id, String title, String content, String subjectId, LocalDateTime createdAt,
                      LocalDateTime updatedAt, List<String> tags) {
        static NoteRecord from(Note note) {
            return new NoteRecord(note.getId(), note.getTitle(), note.getContent(), note.getSubjectId(),
                    note.getCreatedAt(), note.getUpdatedAt(), note.getTags());
        }

        Note toDomain() {
            return new Note(id, title, content, subjectId, createdAt, updatedAt, tags);
        }
    }
}
