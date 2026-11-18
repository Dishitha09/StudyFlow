package com.studyflow.repository;

import com.fasterxml.jackson.databind.JavaType;
import com.studyflow.model.TimetableEntry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persists timetable occurrences together with IDs removed from future re-imports. */
public final class TimetableRepository {
    private final FileStorage storage;
    private final Path path;
    private final JavaType type;

    public TimetableRepository(FileStorage storage) {
        this.storage = storage;
        path = storage.prepare(Path.of("timetable", "timetable.json"));
        type = storage.recordType(Snapshot.class);
    }

    public List<TimetableEntry> findAll() {
        return read().entries();
    }

    public int addMissing(List<TimetableEntry> imported) {
        synchronized (storage) {
            Snapshot current = read();
            var known = new HashSet<>(current.removedIds());
            current.entries().forEach(entry -> known.add(entry.id()));
            var entries = new ArrayList<>(current.entries());
            for (TimetableEntry entry : imported) {
                if (known.add(entry.id())) entries.add(entry);
            }
            int added = entries.size() - current.entries().size();
            if (added > 0) write(new Snapshot(entries, current.removedIds()));
            return added;
        }
    }

    public void update(TimetableEntry edited) {
        synchronized (storage) {
            Snapshot current = read();
            var entries = new ArrayList<>(current.entries());
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).id().equals(edited.id())) {
                    entries.set(i, edited);
                    write(new Snapshot(entries, current.removedIds()));
                    return;
                }
            }
            throw new IllegalArgumentException("This timetable entry no longer exists.");
        }
    }

    public void delete(String id) {
        synchronized (storage) {
            Snapshot current = read();
            var entries = new ArrayList<>(current.entries());
            if (!entries.removeIf(entry -> entry.id().equals(id))) {
                throw new IllegalArgumentException("This timetable entry no longer exists.");
            }
            var removed = new HashSet<>(current.removedIds());
            removed.add(id);
            write(new Snapshot(entries, removed));
        }
    }

    public Path backup() {
        return storage.backup(path, type, (Snapshot value) -> value);
    }

    public void restore(Path backup) {
        storage.restore(path, backup, type, (Snapshot value) -> value, ignored -> { });
    }

    private Snapshot read() {
        return storage.read(path, type, () -> new Snapshot(List.of(), Set.of()), (Snapshot value) -> value);
    }

    private void write(Snapshot value) {
        storage.write(path, type, value, ignored -> { });
    }

    record Snapshot(List<TimetableEntry> entries, Set<String> removedIds) {
        Snapshot {
            entries = List.copyOf(entries);
            removedIds = Set.copyOf(removedIds);
            var ids = new HashSet<>(removedIds);
            for (TimetableEntry entry : entries) {
                if (!ids.add(entry.id())) {
                    throw new IllegalArgumentException("Timetable contains a duplicate or removed entry ID.");
                }
            }
        }
    }
}
