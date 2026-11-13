package com.studyflow.service;

import com.studyflow.interfaces.Identifiable;
import com.studyflow.interfaces.Storage;
import com.studyflow.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** Shared snapshot operations for academic managers. The application serializes commands across managers. */
final class AcademicCollection<T extends Identifiable> {
    private final Storage<List<T>> storage;
    private final UnaryOperator<T> copy;

    AcademicCollection(Storage<List<T>> storage, UnaryOperator<T> copy) {
        this.storage = ValidationUtils.required(storage, "Academic storage");
        this.copy = ValidationUtils.required(copy, "Record copy function");
        synchronized (storage) {
            Set<String> ids = new HashSet<>();
            for (T record : ValidationUtils.required(storage.load(), "Stored records")) {
                ValidationUtils.required(record, "Stored record");
                if (!ids.add(record.getId())) {
                    throw new IllegalArgumentException("Duplicate stored record ID: " + record.getId());
                }
            }
        }
    }

    List<T> findAll() {
        synchronized (storage) {
            return storage.load().stream().map(copy).toList();
        }
    }

    Optional<T> findById(String id) {
        String recordId = ValidationUtils.requiredText(id, "Record ID");
        synchronized (storage) {
            return storage.load().stream().filter(record -> recordId.equals(record.getId()))
                    .findFirst().map(copy);
        }
    }

    T require(String id, String label) {
        return findById(id).orElseThrow(() -> notFound(label, id));
    }

    T insert(T item) {
        ValidationUtils.required(item, "Record");
        synchronized (storage) {
            List<T> candidate = new ArrayList<>(storage.load());
            if (indexOf(candidate, item.getId()) >= 0) {
                throw new IllegalArgumentException("Duplicate record ID: " + item.getId());
            }
            candidate.add(copy.apply(item));
            storage.save(List.copyOf(candidate));
            return copy.apply(item);
        }
    }

    T edit(String id, String label, Consumer<T> edit) {
        ValidationUtils.required(edit, "Record edit");
        return editIfChanged(id, label, record -> {
            edit.accept(record);
            return true;
        });
    }

    T editIfChanged(String id, String label, Predicate<T> edit) {
        String recordId = ValidationUtils.requiredText(id, "Record ID");
        ValidationUtils.required(edit, "Record edit");
        synchronized (storage) {
            List<T> candidate = new ArrayList<>(storage.load());
            int index = indexOf(candidate, recordId);
            if (index < 0) {
                throw notFound(label, recordId);
            }
            T edited = copy.apply(candidate.get(index));
            if (edit.test(edited)) {
                candidate.set(index, copy.apply(edited));
                storage.save(List.copyOf(candidate));
                return edited;
            }
            return copy.apply(candidate.get(index));
        }
    }

    boolean delete(String id) {
        String recordId = ValidationUtils.requiredText(id, "Record ID");
        synchronized (storage) {
            List<T> candidate = new ArrayList<>(storage.load());
            int index = indexOf(candidate, recordId);
            if (index < 0) {
                return false;
            }
            candidate.remove(index);
            storage.save(List.copyOf(candidate));
            return true;
        }
    }

    private int indexOf(List<T> records, String id) {
        for (int index = 0; index < records.size(); index++) {
            if (id.equals(records.get(index).getId())) {
                return index;
            }
        }
        return -1;
    }

    private NoSuchElementException notFound(String label, String id) {
        return new NoSuchElementException(label + " not found: " + id);
    }
}
