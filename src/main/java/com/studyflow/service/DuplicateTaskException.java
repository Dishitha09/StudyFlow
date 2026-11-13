package com.studyflow.service;

/** A task ID has already been used by a root or descendant. */
public final class DuplicateTaskException extends IllegalArgumentException {
    public DuplicateTaskException(String id) {
        super("Duplicate task ID: " + id);
    }
}
