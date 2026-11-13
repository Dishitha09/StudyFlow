package com.studyflow.repository;

import com.fasterxml.jackson.databind.JavaType;
import com.studyflow.interfaces.Identifiable;
import com.studyflow.interfaces.Repository;
import com.studyflow.utils.ValidationUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Shared collection CRUD. Each operation reads from or commits to the same typed JSON snapshot. */
abstract class JsonCollectionRepository<T extends Identifiable, D> implements Repository<T> {
    private final FileStorage storage;
    private final Path path;
    private final JavaType listType;
    private final Function<T, D> encode;
    private final Function<D, T> decode;

    JsonCollectionRepository(FileStorage storage, Path relativePath, Class<D> recordClass,
                             Function<T, D> encode, Function<D, T> decode) {
        this.storage = Objects.requireNonNull(storage, "File storage");
        this.path = storage.prepare(relativePath);
        this.listType = storage.listType(recordClass);
        this.encode = encode;
        this.decode = decode;
    }

    @Override
    public final List<T> load() {
        return storage.read(path, listType, List::of, this::decode);
    }

    @Override
    public final void save(List<T> records) {
        List<D> candidate;
        try {
            candidate = records.stream().map(encode).toList();
        } catch (RuntimeException ex) {
            throw new StorageException(path, "Cannot prepare the StudyFlow data for saving", ex);
        }
        storage.write(path, listType, candidate, this::decode);
    }

    /** Creates a timestamped backup of the current validated collection. */
    public final Path backup() {
        return storage.backup(path, listType, this::decode);
    }

    /** Validates a selected backup and commits the same bytes after the supplied checks pass. */
    public final void restore(Path backup, Consumer<List<T>> validateCandidate) {
        storage.restore(path, backup, listType, this::decode, validateCandidate);
    }

    @Override
    public final Optional<T> findById(String id) {
        String key = ValidationUtils.requiredText(id, "Record ID");
        return load().stream().filter(record -> key.equals(record.getId())).findFirst();
    }

    @Override
    public final void insert(T record) {
        ValidationUtils.required(record, "Record");
        synchronized (storage) {
            List<T> candidate = new ArrayList<>(load());
            if (indexOf(candidate, record.getId()) >= 0) {
                throw new IllegalArgumentException("Duplicate record ID: " + record.getId());
            }
            candidate.add(record);
            save(candidate);
        }
    }

    @Override
    public final void update(T record) {
        ValidationUtils.required(record, "Record");
        synchronized (storage) {
            List<T> candidate = new ArrayList<>(load());
            int index = indexOf(candidate, record.getId());
            if (index < 0) {
                throw new NoSuchElementException("Record not found: " + record.getId());
            }
            candidate.set(index, record);
            save(candidate);
        }
    }

    @Override
    public final boolean delete(String id) {
        String key = ValidationUtils.requiredText(id, "Record ID");
        synchronized (storage) {
            List<T> candidate = new ArrayList<>(load());
            int index = indexOf(candidate, key);
            if (index < 0) {
                return false;
            }
            candidate.remove(index);
            save(candidate);
            return true;
        }
    }

    private List<T> decode(List<D> records) {
        List<T> decoded = new ArrayList<>(records.size());
        Set<String> ids = new HashSet<>();
        for (D document : records) {
            T record = decode.apply(ValidationUtils.required(document, "Stored record"));
            if (!ids.add(record.getId())) {
                throw new IllegalArgumentException("Duplicate record ID: " + record.getId());
            }
            decoded.add(record);
        }
        validateCollection(decoded);
        return List.copyOf(decoded);
    }

    void validateCollection(List<T> records) {
        // Most collections need only individual record and ID validation.
    }

    private int indexOf(List<T> records, String id) {
        for (int index = 0; index < records.size(); index++) {
            if (id.equals(records.get(index).getId())) {
                return index;
            }
        }
        return -1;
    }
}
