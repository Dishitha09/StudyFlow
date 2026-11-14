package com.studyflow.config;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Reports fixed operation names and error types without user content or secrets. */
public final class AppLog {
    private static final Logger LOG = Logger.getLogger("com.studyflow");
    private AppLog() { }

    public static void event(String event) {
        LOG.log(Level.INFO, "event={0}", event);
    }

    public static void failure(String event, Throwable error) {
        Throwable cause = error.getCause();
        LOG.log(Level.WARNING, "event={0} error={1} cause={2}", new Object[]{
                event, error.getClass().getSimpleName(), cause == null ? "none" : cause.getClass().getSimpleName()});
    }
}
