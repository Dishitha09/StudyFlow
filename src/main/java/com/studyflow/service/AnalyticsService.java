package com.studyflow.service;

import com.studyflow.model.FocusRecord;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/** Calculates activity from saved records and keeps it separate from the current task inventory. */
public final class AnalyticsService {
    public static final int MAX_RANGE_DAYS = 366;

    private final Clock clock;

    public AnalyticsService(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public Report summarize(LocalDate start, LocalDate end, List<Task> rootTasks, List<Subject> subjects,
                            List<FocusRecord> focusRecords, AttendanceService.Snapshot attendance) {
        DateRange range = new DateRange(start, end);
        LocalDate today = LocalDate.now(clock);
        ZoneId zone = clock.getZone();
        Map<LocalDate, Day> days = new LinkedHashMap<>();
        for (int offset = 0; offset < range.dayCount(); offset++) {
            days.put(start.plusDays(offset), new Day());
        }

        int completed = 0;
        int overdue = 0;
        int unknownCompletionDates = 0;
        long completionsInRange = 0;
        for (Task task : rootTasks) {
            if (task.getStatus() == TaskStatus.COMPLETED) {
                completed++;
                if (task.getCompletedAt() == null) {
                    unknownCompletionDates++;
                }
            } else if (task.isOverdue(today)) {
                overdue++;
            }
            if (task.getCompletedAt() != null) {
                Day day = days.get(LocalDate.ofInstant(task.getCompletedAt(), zone));
                if (day != null) {
                    day.taskCompletions++;
                    completionsInRange++;
                }
            }
        }
        TaskInventory inventory = new TaskInventory(rootTasks.size(), completed, rootTasks.size() - completed,
                overdue, unknownCompletionDates);

        Map<String, String> subjectNames = new HashMap<>();
        for (Subject subject : subjects) {
            subjectNames.put(subject.getId(), subject.getName());
        }
        Map<String, FocusTotal> focusBySubject = new LinkedHashMap<>();
        FocusTotal focusTotal = new FocusTotal();
        for (FocusRecord record : focusRecords) {
            Day day = days.get(LocalDate.ofInstant(record.completedAt(), zone));
            if (day == null) {
                continue;
            }
            day.focus.add(record.actual());
            focusTotal.add(record.actual());
            focusBySubject.computeIfAbsent(record.subjectId(), ignored -> new FocusTotal()).add(record.actual());
        }
        List<SubjectFocus> focusRows = focusBySubject.entrySet().stream().map(entry -> {
            String id = entry.getKey();
            FocusTotal focus = entry.getValue();
            String name = id == null ? "No subject" : subjectNames.getOrDefault(id, "Former subject");
            return new SubjectFocus(id, name, focus.sessions, focus.time);
        }).sorted(Comparator.comparing(SubjectFocus::focusTime).reversed()
                .thenComparing(SubjectFocus::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SubjectFocus::subjectId, Comparator.nullsLast(Comparator.naturalOrder()))).toList();

        List<SubjectAttendance> attendanceRows = attendance.subjects().stream()
                .map(subject -> attendanceInRange(subject, range)).toList();
        long attended = attendanceRows.stream().mapToLong(SubjectAttendance::attended).sum();
        long classes = attendanceRows.stream().mapToLong(SubjectAttendance::classes).sum();

        Totals totals = new Totals(completionsInRange, focusTotal.sessions, focusTotal.time, attended, classes);
        List<DailyActivity> daily = days.entrySet().stream().map(entry -> {
            Day day = entry.getValue();
            return new DailyActivity(entry.getKey(), day.taskCompletions, day.focus.sessions, day.focus.time);
        }).toList();
        return new Report(range, inventory, totals, daily, focusRows, attendanceRows);
    }

    private static SubjectAttendance attendanceInRange(AttendanceService.SubjectAttendance subject, DateRange range) {
        long classes = 0;
        long attended = 0;
        for (var entry : subject.history()) {
            if (range.contains(entry.date())) {
                classes++;
                if (entry.attended()) {
                    attended++;
                }
            }
        }
        return new SubjectAttendance(subject.subjectId(), subject.name(), subject.target(), attended, classes);
    }

    private static OptionalDouble percentage(long attended, long classes) {
        return classes == 0 ? OptionalDouble.empty() : OptionalDouble.of(100.0 * attended / classes);
    }

    public record DateRange(LocalDate start, LocalDate end) {
        public DateRange {
            if (start == null || end == null || end.isBefore(start)) {
                throw new IllegalArgumentException("Choose a start date on or before the end date.");
            }
            if (ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
                throw new IllegalArgumentException("Choose a date range of at most " + MAX_RANGE_DAYS + " days.");
            }
        }

        public int dayCount() {
            return (int) ChronoUnit.DAYS.between(start, end) + 1;
        }

        public boolean contains(LocalDate date) {
            return !date.isBefore(start) && !date.isAfter(end);
        }
    }

    /** Current status of the root tasks, regardless of the activity date range. */
    public record TaskInventory(int total, int completed, int pending, int overdue, int unknownCompletionDates) { }

    public record Totals(long taskCompletions, long focusSessions, Duration focusTime, long attended, long classes) {
        public OptionalDouble attendancePercentage() {
            return percentage(attended, classes);
        }
    }

    public record DailyActivity(LocalDate date, long taskCompletions, long focusSessions, Duration focusTime) { }

    public record SubjectFocus(String subjectId, String name, long sessions, Duration focusTime) { }

    public record SubjectAttendance(String subjectId, String name, int target, long attended, long classes) {
        public OptionalDouble attendancePercentage() {
            return percentage(attended, classes);
        }
    }

    /**
     * Activity over the selected dates. A surviving root task contributes at most its latest recorded completion.
     */
    public record Report(DateRange range, TaskInventory inventory, Totals totals, List<DailyActivity> days,
                         List<SubjectFocus> focusBySubject, List<SubjectAttendance> attendanceBySubject) {
        public Report {
            days = List.copyOf(days);
            focusBySubject = List.copyOf(focusBySubject);
            attendanceBySubject = List.copyOf(attendanceBySubject);
        }
    }

    private static final class Day {
        long taskCompletions;
        final FocusTotal focus = new FocusTotal();
    }

    private static final class FocusTotal {
        long sessions;
        Duration time = Duration.ZERO;

        void add(Duration duration) {
            sessions++;
            time = time.plus(duration);
        }
    }
}
