package com.studyflow.service;

import com.studyflow.interfaces.Repository;
import com.studyflow.model.AttendanceBaseline;
import com.studyflow.model.AttendanceEntry;
import com.studyflow.model.AttendanceTarget;
import com.studyflow.model.Subject;
import com.studyflow.utils.IdGenerator;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.NoSuchElementException;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Maintains class history and computes projections for the profile's current subjects. */
public final class AttendanceService {
    private final Repository<AttendanceEntry> entries;
    private final Repository<AttendanceTarget> targets;
    private final Repository<AttendanceBaseline> baselines;
    private final Supplier<List<Subject>> subjects;
    private final IntSupplier defaultTarget;

    public AttendanceService(Repository<AttendanceEntry> entries, Repository<AttendanceTarget> targets,
                             Repository<AttendanceBaseline> baselines, Supplier<List<Subject>> subjects,
                             IntSupplier defaultTarget) {
        this.entries = entries;
        this.targets = targets;
        this.baselines = baselines;
        this.subjects = subjects;
        this.defaultTarget = defaultTarget;
    }

    public AttendanceEntry add(String subjectId, LocalDate date, boolean attended) {
        requireSubject(subjectId);
        AttendanceEntry entry = new AttendanceEntry(IdGenerator.next(), subjectId, date, attended);
        entries.insert(entry);
        return entry;
    }

    public void correct(String id, LocalDate date, boolean attended) {
        AttendanceEntry existing = entries.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Attendance entry not found."));
        entries.update(new AttendanceEntry(id, existing.subjectId(), date, attended));
    }

    public void setTarget(String subjectId, int target) {
        requireSubject(subjectId);
        AttendanceTarget preference = new AttendanceTarget(subjectId, target);
        if (targets.existsById(subjectId)) {
            targets.update(preference);
        } else {
            targets.insert(preference);
        }
    }

    public void setBaseline(String subjectId, Baseline baseline) {
        requireSubject(subjectId);
        Objects.requireNonNull(baseline, "Starting totals");
        if (baseline.total() == 0) {
            baselines.delete(subjectId);
            return;
        }
        var saved = new AttendanceBaseline(subjectId, baseline.attended(), baseline.total());
        var previous = baselines.findById(subjectId);
        if (previous.isEmpty()) baselines.insert(saved);
        else if (!saved.equals(previous.get())) baselines.update(saved);
    }

    public boolean references(String subjectId) {
        return baselines.existsById(subjectId) || targets.existsById(subjectId) || entries.findAll().stream()
                .anyMatch(entry -> entry.subjectId().equals(subjectId));
    }

    public Snapshot snapshot() {
        var history = entries.findAll().stream().collect(Collectors.groupingBy(AttendanceEntry::subjectId));
        var overrides = targets.findAll().stream()
                .collect(Collectors.toMap(AttendanceTarget::subjectId, AttendanceTarget::target));
        var starting = baselines.findAll().stream().collect(Collectors.toUnmodifiableMap(
                AttendanceBaseline::subjectId, item -> new Baseline(item.attended(), item.total())));
        List<SubjectAttendance> rows = subjects.get().stream().map(subject -> {
            String id = subject.getId();
            int target = overrides.getOrDefault(id, defaultTarget.getAsInt());
            List<AttendanceEntry> classes = history.getOrDefault(id, List.of()).stream()
                    .sorted(Comparator.comparing(AttendanceEntry::date).reversed()).toList();
            Baseline baseline = starting.getOrDefault(id, Baseline.EMPTY);
            long attended = baseline.attended() + classes.stream().filter(AttendanceEntry::attended).count();
            long total = (long) baseline.total() + classes.size();
            return new SubjectAttendance(id, subject.getName(), target, classes,
                    AttendanceMath.project(attended, total, target));
        }).toList();
        return new Snapshot(rows, rows.stream().mapToLong(row -> row.projection().attended()).sum(),
                rows.stream().mapToLong(row -> row.projection().total()).sum(), starting);
    }

    private void requireSubject(String id) {
        if (subjects.get().stream().noneMatch(subject -> subject.getId().equals(id))) {
            throw new IllegalArgumentException("Select an existing subject.");
        }
    }

    public record Baseline(int attended, int total) {
        private static final Baseline EMPTY = new Baseline(0, 0);

        public Baseline {
            AttendanceBaseline.validateCounts(attended, total);
        }
    }

    public record Snapshot(List<SubjectAttendance> subjects, long attended, long total, Map<String, Baseline> baselines) {
        public Snapshot {
            subjects = List.copyOf(subjects);
            baselines = Map.copyOf(baselines);
        }

        public Snapshot(List<SubjectAttendance> subjects, long attended, long total) {
            this(subjects, attended, total, Map.of());
        }
    }

    public record SubjectAttendance(String subjectId, String name, int target, List<AttendanceEntry> history,
                                    AttendanceMath.Projection projection) { }
}
