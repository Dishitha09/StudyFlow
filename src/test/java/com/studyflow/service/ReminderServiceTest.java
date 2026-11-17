package com.studyflow.service;

import com.studyflow.model.GeneralTask;
import com.studyflow.model.Priority;
import com.studyflow.model.TaskStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReminderServiceTest {
    @Test
    void remindersAreNotReplayedAndResetAtTheLocalDayBoundary() {
        LocalDate date = LocalDate.of(2026, 11, 17);
        var task = new GeneralTask("qa-task", "Read chapter", "", null, date, date, Priority.HIGH, TaskStatus.TODO);
        var reminders = new ReminderService();
        assertTrue(reminders.collect(List.of(task), date, false).isEmpty());
        assertEquals(List.of("Due today · Read chapter (2026-11-17)"), reminders.collect(List.of(task), date, true));
        assertTrue(reminders.collect(List.of(task), date, true).isEmpty());
        assertEquals(List.of("Overdue · Read chapter (2026-11-17)"),
                reminders.collect(List.of(task), date.plusDays(1), true));
        assertTrue(reminders.collect(List.of(task), date.plusDays(1), true).isEmpty());
    }
}
