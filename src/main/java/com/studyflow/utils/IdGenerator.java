package com.studyflow.utils;

import java.util.UUID;

/** Creates portable IDs independently of titles and storage order. */
public final class IdGenerator {
    private IdGenerator() {
    }

    public static String next() {
        return UUID.randomUUID().toString();
    }
}
