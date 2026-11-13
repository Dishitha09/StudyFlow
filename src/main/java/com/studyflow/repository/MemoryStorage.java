package com.studyflow.repository;

import com.studyflow.interfaces.Storage;
import java.util.Objects;

/** In-memory storage for snapshots owned and detached by the calling service. */
public final class MemoryStorage<T> implements Storage<T> {
    private T data;

    public MemoryStorage(T initial) {
        save(initial);
    }

    @Override
    public synchronized void save(T data) {
        this.data = Objects.requireNonNull(data, "snapshot");
    }

    @Override
    public synchronized T load() {
        return data;
    }
}
