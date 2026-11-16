package com.studyflow.repository;

import com.fasterxml.jackson.databind.JavaType;
import com.studyflow.interfaces.Storage;
import com.studyflow.model.StudyHistoryEntry;
import java.nio.file.Path;
import java.util.List;

/** Stores assistant history without putting cleared conversations in automatic backups. */
public final class StudyHistoryRepository implements Storage<List<StudyHistoryEntry>> {
    private final FileStorage storage;
    private final Path path;
    private final JavaType listType;

    public StudyHistoryRepository(FileStorage storage) {
        this.storage = storage;
        path = storage.prepare(Path.of("materials", "assistant-history.json"));
        listType = storage.listType(StudyHistoryEntry.class);
    }

    @Override
    public List<StudyHistoryEntry> load() {
        return storage.read(path, listType, List::of, (List<StudyHistoryEntry> entries) -> List.copyOf(entries));
    }

    @Override
    public void save(List<StudyHistoryEntry> entries) {
        List<StudyHistoryEntry> checked = List.copyOf(entries);
        if (checked.isEmpty()) {
            storage.removeSnapshot(path);
        } else {
            storage.write(path, listType, checked, List::copyOf, false);
        }
    }
}
