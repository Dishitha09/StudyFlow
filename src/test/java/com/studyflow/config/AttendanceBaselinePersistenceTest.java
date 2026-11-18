package com.studyflow.config;

import com.studyflow.model.AttendanceEntry;
import com.studyflow.service.AnalyticsService;
import com.studyflow.service.AttendanceService;
import com.studyflow.service.AttendanceService.Baseline;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttendanceBaselinePersistenceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC);

    @TempDir Path profile;

    @Test
    void reopensBaselineAndDatedClassesAndProjectsAgainstSeparateTargets() throws IOException {
        String programming;
        String finance;
        String workshop;
        List<AttendanceEntry> programmingHistory;
        AttendanceEntry financeClass;
        try (var context = StudyFlowContext.open(profile, CLOCK)) {
            programming = subject(context, "Programming", "COMP205");
            finance = subject(context, "Finance", "MGMT207");
            workshop = subject(context, "Workshop", "WRK");
            var settings = context.settings();
            settings.setDefaultAttendanceTarget(75);
            context.saveSettings(settings);

            var attendance = context.attendanceService();
            attendance.setTarget(programming, 80);
            attendance.setBaseline(programming, new Baseline(15, 20));
            attendance.setBaseline(finance, new Baseline(21, 25));

            var first = attendance.add(programming, TODAY.minusDays(2), true);
            var second = attendance.add(programming, TODAY.minusDays(1), false);
            var third = attendance.add(programming, TODAY, false);
            programmingHistory = List.of(third, second, first);
            financeClass = attendance.add(finance, TODAY, true);
            attendance.add(workshop, TODAY, true);
        }

        try (var reopened = StudyFlowContext.open(profile, CLOCK)) {
            var snapshot = reopened.attendanceService().snapshot();
            assertEquals(Map.of(programming, new Baseline(15, 20), finance, new Baseline(21, 25)),
                    snapshot.baselines());
            assertEquals(39, snapshot.attended());
            assertEquals(50, snapshot.total());

            var programmingRow = row(snapshot, programming);
            assertEquals(programmingHistory, programmingRow.history());
            assertEquals(80, programmingRow.target());
            assertEquals(16, programmingRow.projection().attended());
            assertEquals(23, programmingRow.projection().total());
            assertEquals(1600.0 / 23, programmingRow.projection().percentage(), 1e-10);
            assertEquals(OptionalLong.of(12), programmingRow.projection().needed());
            assertEquals(OptionalLong.of(0), programmingRow.projection().canMiss());

            var financeRow = row(snapshot, finance);
            assertEquals(List.of(financeClass), financeRow.history());
            assertEquals(75, financeRow.target());
            assertEquals(22, financeRow.projection().attended());
            assertEquals(26, financeRow.projection().total());
            assertEquals(OptionalLong.of(3), financeRow.projection().canMiss());

            var workshopRow = row(snapshot, workshop);
            assertEquals(1, workshopRow.history().size());
            assertEquals(1, workshopRow.projection().attended());
            assertEquals(1, workshopRow.projection().total());
        }
    }

    @Test
    void replacingBaselineDoesNotAccumulateOrRewriteClassHistory() throws IOException {
        String id;
        AttendanceEntry dated;
        try (var context = StudyFlowContext.open(profile, CLOCK)) {
            id = subject(context, "Lab", "LAB");
            var attendance = context.attendanceService();
            dated = attendance.add(id, TODAY, false);
            attendance.setBaseline(id, new Baseline(4, 5));
            attendance.setBaseline(id, new Baseline(4, 5));
            assertEquals(6, attendance.snapshot().total());
            attendance.setBaseline(id, new Baseline(0, 2));
        }

        try (var reopened = StudyFlowContext.open(profile, CLOCK)) {
            var snapshot = reopened.attendanceService().snapshot();
            assertEquals(Map.of(id, new Baseline(0, 2)), snapshot.baselines());
            assertEquals(0, snapshot.attended());
            assertEquals(3, snapshot.total());
            assertEquals(List.of(dated), row(snapshot, id).history());
        }
    }

    @Test
    void correctingDatedClassAndClearingBaselineAreIndependentOfTarget() throws IOException {
        String id;
        String datedId;
        try (var context = StudyFlowContext.open(profile, CLOCK)) {
            id = subject(context, "Lab", "LAB");
            var attendance = context.attendanceService();
            datedId = attendance.add(id, TODAY.minusDays(1), false).id();
            assertEquals(Map.of(), attendance.snapshot().baselines());
            attendance.setTarget(id, 85);
            attendance.setBaseline(id, new Baseline(8, 10));
            assertEquals(OptionalLong.of(9), row(attendance.snapshot(), id).projection().needed());

            attendance.correct(datedId, TODAY, true);
            var corrected = attendance.snapshot();
            assertEquals(Map.of(id, new Baseline(8, 10)), corrected.baselines());
            assertEquals(9, corrected.attended());
            assertEquals(11, corrected.total());
            assertEquals(OptionalLong.of(3), row(corrected, id).projection().needed());

            var analytics = new AnalyticsService(CLOCK).summarize(TODAY, TODAY, List.of(),
                    context.subjectManager().findAll(), List.of(), corrected);
            assertEquals(1, analytics.totals().attended());
            assertEquals(1, analytics.totals().classes());

            attendance.setBaseline(id, new Baseline(0, 0));
        }

        try (var reopened = StudyFlowContext.open(profile, CLOCK)) {
            var snapshot = reopened.attendanceService().snapshot();
            assertEquals(Map.of(), snapshot.baselines());
            assertEquals(1, snapshot.attended());
            assertEquals(1, snapshot.total());
            assertEquals(List.of(new AttendanceEntry(datedId, id, TODAY, true)), row(snapshot, id).history());
            assertEquals(85, row(snapshot, id).target());
            assertEquals(OptionalLong.of(0), row(snapshot, id).projection().needed());
        }
    }

    @Test
    void rejectsImpossibleBaselinesAndUnknownSubjectsWithoutChangingSavedCounts() throws IOException {
        String id;
        try (var context = StudyFlowContext.open(profile, CLOCK)) {
            id = subject(context, "Lab", "LAB");
            var attendance = context.attendanceService();
            attendance.setBaseline(id, new Baseline(2, 4));
            assertThrows(IllegalArgumentException.class, () -> attendance.setBaseline(id, new Baseline(-1, 4)));
            assertThrows(IllegalArgumentException.class, () -> attendance.setBaseline(id, new Baseline(0, -1)));
            assertThrows(IllegalArgumentException.class, () -> attendance.setBaseline(id, new Baseline(5, 4)));
            assertThrows(IllegalArgumentException.class,
                    () -> attendance.setBaseline("unknown-subject", new Baseline(1, 1)));
        }

        try (var reopened = StudyFlowContext.open(profile, CLOCK)) {
            var snapshot = reopened.attendanceService().snapshot();
            assertEquals(Map.of(id, new Baseline(2, 4)), snapshot.baselines());
            assertEquals(2, snapshot.attended());
            assertEquals(4, snapshot.total());
            assertEquals(List.of(), row(snapshot, id).history());
        }
    }

    private static String subject(StudyFlowContext context, String name, String code) {
        return context.subjectManager().create(name, code, "", List.of()).getId();
    }

    private static AttendanceService.SubjectAttendance row(AttendanceService.Snapshot snapshot, String id) {
        return snapshot.subjects().stream().filter(r -> r.subjectId().equals(id)).findFirst().orElseThrow();
    }
}
