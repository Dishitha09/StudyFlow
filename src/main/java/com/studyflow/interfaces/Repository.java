package com.studyflow.interfaces;

import java.util.List;
import java.util.Optional;

/** Collection CRUD with detached results and snapshot writes. */
public interface Repository<T extends Identifiable> extends Storage<List<T>> {
    void insert(T record);

    Optional<T> findById(String id);

    default List<T> findAll() {
        return load();
    }

    void update(T record);

    boolean delete(String id);

    default boolean existsById(String id) {
        return findById(id).isPresent();
    }
}
