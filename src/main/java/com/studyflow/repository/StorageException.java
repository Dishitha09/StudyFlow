package com.studyflow.repository;

import java.nio.file.Path;

/** A failed local storage operation with the affected file or directory. */
public final class StorageException extends RuntimeException {
    private final Path path;

    public StorageException(Path path, String detail, Throwable cause) {
        super(detail + " [" + path + "]" + (cause == null ? "" : ": " + cause.getMessage()), cause);
        this.path = path;
    }

    public Path getPath() {
        return path;
    }
}
