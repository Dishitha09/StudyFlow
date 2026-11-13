package com.studyflow.interfaces;

/** Reads and commits a complete snapshot. A failed save leaves the previous snapshot available. */
public interface Storage<T> {
    void save(T data);

    T load();
}
