package com.studyflow.model;

import java.time.LocalDate;

/** Cadence for a root task, measured from the occurrence's deadline. */
public enum Recurrence {
    NONE, DAILY, WEEKLY, MONTHLY;

    public LocalDate nextAfter(LocalDate previousDeadline) {
        return switch (this) {
            case DAILY -> previousDeadline.plusDays(1);
            case WEEKLY -> previousDeadline.plusWeeks(1);
            case MONTHLY -> previousDeadline.plusMonths(1);
            case NONE -> throw new IllegalStateException("A non-recurring task has no next deadline.");
        };
    }
}
