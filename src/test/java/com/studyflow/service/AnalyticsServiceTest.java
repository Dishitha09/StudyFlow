package com.studyflow.service;

import com.studyflow.model.AttendanceEntry;
import com.studyflow.model.FocusRecord;
import com.studyflow.model.GeneralTask;
import com.studyflow.model.Priority;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalyticsServiceTest {
    private static final LocalDate START = LocalDate.of(2026, 11, 14);
    private static final LocalDate END = START.plusDays(2);
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Instant FIRST = START.atStartOfDay(ZONE).toInstant();
    private static final Instant LAST = END.plusDays(1).atStartOfDay(ZONE).toInstant().minusNanos(1);
    private static final AnalyticsService SERVICE =
            new AnalyticsService(Clock.fixed(END.plusDays(1).atStartOfDay(ZONE).toInstant(), ZONE));

    @Test
    void separatesCurrentInventoryFromRecordedRootCompletionsAtLocalDateBoundaries() {
        Task reopened = task("reopened", TaskStatus.TODO, null, FIRST);
        Task pending = task("pending", TaskStatus.TODO, START, null);
        pending.addSubtask(task("nested", TaskStatus.COMPLETED, null, FIRST));
        Task before = task("before", TaskStatus.COMPLETED, null, FIRST.minusNanos(1));
        Task last = task("last", TaskStatus.COMPLETED, null, LAST);
        Task after = task("after", TaskStatus.COMPLETED, null, LAST.plusNanos(1));
        Task legacy = task("legacy", TaskStatus.COMPLETED, END, null);

        var report = SERVICE.summarize(START, END, List.of(reopened, pending, before, last, after, legacy),
                List.of(), List.of(), emptyAttendance());

        assertEquals(new AnalyticsService.TaskInventory(6, 4, 2, 1, 1), report.inventory());
        assertEquals(2, report.totals().taskCompletions());
        assertEquals(List.of(START, START.plusDays(1), END), report.days().stream()
                .map(AnalyticsService.DailyActivity::date).toList());
        assertEquals(List.of(1L, 0L, 1L), report.days().stream()
                .map(AnalyticsService.DailyActivity::taskCompletions).toList());
    }

    @Test
    void addsActualFocusTimeByLocalDayAndRecordedSubject() {
        Subject physics = new Subject("physics", "Physics", "PHY", "", List.of());
        List<FocusRecord> sessions = List.of(
                focus("old", "physics", FIRST.minusNanos(1), Duration.ofMinutes(20)),
                focus("physics-first", "physics", FIRST, Duration.ofMinutes(10).plusSeconds(30)),
                focus("physics-last", "physics", LAST, Duration.ofMinutes(2)),
                focus("unassigned", null, LAST, Duration.ofMinutes(5).plusSeconds(3)),
                focus("former", "removed", LAST, Duration.ofSeconds(10)),
                focus("after", "physics", LAST.plusNanos(1), Duration.ofMinutes(20)));

        var report = SERVICE.summarize(START, END, List.of(), List.of(physics), sessions, emptyAttendance());

        assertEquals(4, report.totals().focusSessions());
        assertEquals(Duration.ofMinutes(17).plusSeconds(43), report.totals().focusTime());
        assertEquals(List.of(Duration.ofMinutes(10).plusSeconds(30), Duration.ZERO,
                        Duration.ofMinutes(7).plusSeconds(13)),
                report.days().stream().map(AnalyticsService.DailyActivity::focusTime).toList());
        assertEquals(List.of(
                new AnalyticsService.SubjectFocus("physics", "Physics", 2, Duration.ofMinutes(12).plusSeconds(30)),
                new AnalyticsService.SubjectFocus(null, "No subject", 1, Duration.ofMinutes(5).plusSeconds(3)),
                new AnalyticsService.SubjectFocus("removed", "Former subject", 1, Duration.ofSeconds(10))),
                report.focusBySubject());
    }

    @Test
    void weighsOnlyClassesInTheRangeAndDistinguishesMissingAttendanceFromZeroPercent() {
        var physics = attendance("physics", "Physics", 75,
                new AttendanceEntry("1", "physics", START, true),
                new AttendanceEntry("2", "physics", END.plusDays(1), true));
        var history = attendance("history", "History", 80,
                new AttendanceEntry("3", "history", START, false),
                new AttendanceEntry("4", "history", END, false),
                new AttendanceEntry("5", "history", START.minusDays(1), true));
        var language = attendance("language", "Language", 85);
        var report = SERVICE.summarize(START, END, List.of(), List.of(), List.of(),
                new AttendanceService.Snapshot(List.of(physics, history, language), 3, 5));

        assertEquals(1, report.totals().attended());
        assertEquals(3, report.totals().classes());
        assertEquals(100.0 / 3, report.totals().attendancePercentage().orElseThrow());
        assertEquals(75, report.attendanceBySubject().getFirst().target());
        assertEquals(0.0, report.attendanceBySubject().get(1).attendancePercentage().orElseThrow());
        assertFalse(report.attendanceBySubject().get(2).attendancePercentage().isPresent());
    }

    @Test
    void allowsInclusiveSingleDayAndYearRangesAndRejectsInvalidRanges() {
        var empty = SERVICE.summarize(START, START, List.of(), List.of(), List.of(), emptyAttendance());
        assertEquals(List.of(new AnalyticsService.DailyActivity(START, 0, 0, Duration.ZERO)), empty.days());
        assertFalse(empty.totals().attendancePercentage().isPresent());
        assertEquals(366, new AnalyticsService.DateRange(START, START.plusDays(365)).dayCount());
        assertThrows(IllegalArgumentException.class, () -> new AnalyticsService.DateRange(END, START));
        assertThrows(IllegalArgumentException.class, () -> new AnalyticsService.DateRange(START, START.plusDays(366)));
    }

    private static Task task(String id, TaskStatus status, LocalDate due, Instant completedAt) {
        return new GeneralTask(id, id, "", null, START.minusDays(3), due, Priority.MEDIUM, status, completedAt);
    }

    private static FocusRecord focus(String id, String subjectId, Instant completedAt, Duration actual) {
        return new FocusRecord(id, null, subjectId, completedAt, Duration.ofMinutes(25), actual);
    }

    private static AttendanceService.Snapshot emptyAttendance() {
        return new AttendanceService.Snapshot(List.of(), 0, 0);
    }

    private static AttendanceService.SubjectAttendance attendance(String id, String name, int target,
                                                                  AttendanceEntry... entries) {
        List<AttendanceEntry> history = List.of(entries);
        long attended = history.stream().filter(AttendanceEntry::attended).count();
        return new AttendanceService.SubjectAttendance(id, name, target, history,
                AttendanceMath.project(attended, history.size(), target));
    }
}
