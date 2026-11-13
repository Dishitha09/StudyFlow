package com.studyflow.service;

import java.util.NoSuchElementException;

/** A requested task or subtask ID does not exist in the relevant collection. */
public final class TaskNotFoundException extends NoSuchElementException {
    public TaskNotFoundException(String id) {
        super("Task not found: " + id);
    }
}
