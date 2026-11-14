package com.studyflow.config;

import com.studyflow.model.AttendanceEntry;
import com.studyflow.model.Subject;
import com.studyflow.service.AttendanceService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class AttendancePersistenceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T09:00:00Z"), ZoneOffset.UTC);
    @TempDir
    Path profile;

    @Test
    void restoresCorrectedClassHistoryAndIndependentSubjectTargets() throws IOException {
        String physicsId;
        String algebraId;
        String correctedId;
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            physicsId = subject(context, "Physics", "PHY").getId();
            algebraId = subject(context, "Algebra", "MTH").getId();
            AttendanceService attendance = context.attendanceService();
            AttendanceEntry corrected = attendance.add(physicsId, TODAY.minusDays(3), true);
            correctedId = corrected.id();
            attendance.add(physicsId, TODAY.minusDays(1), true);
            attendance.correct(correctedId, TODAY, false);
            attendance.add(algebraId, TODAY.minusDays(1), true);
            attendance.setTarget(physicsId, 85);
            attendance.setTarget(physicsId, 80);
            var settings = context.settings();
            settings.setDefaultAttendanceTarget(77);
            context.saveSettings(settings);
        }

        try (StudyFlowContext reopened = StudyFlowContext.open(profile, CLOCK)) {
            AttendanceService.Snapshot snapshot = reopened.attendanceService().snapshot();
            AttendanceService.SubjectAttendance physics = row(snapshot, physicsId);
            AttendanceService.SubjectAttendance algebra = row(snapshot, algebraId);

            assertEquals(2, snapshot.subjects().size());
            assertEquals(2, snapshot.attended());
            assertEquals(3, snapshot.total());
            assertEquals(80, physics.target());
            assertEquals(77, algebra.target());
            assertEquals(1, physics.projection().attended());
            assertEquals(2, physics.projection().total());
            assertEquals(50.0, physics.projection().percentage());
            assertEquals(OptionalLong.of(3), physics.projection().needed());
            assertEquals(100.0, algebra.projection().percentage());
            assertEquals(List.of(TODAY, TODAY.minusDays(1)),
                    physics.history().stream().map(AttendanceEntry::date).toList());
            AttendanceEntry corrected = physics.history().getFirst();
            assertEquals(correctedId, corrected.id());
            assertEquals(physicsId, corrected.subjectId());
            assertFalse(corrected.attended());
        }
    }

    @Test
    void invalidEditsAndUnknownSubjectsLeaveRecordedClassesUnchanged() throws IOException {
        try (StudyFlowContext context = StudyFlowContext.open(profile, CLOCK)) {
            String subjectId = subject(context, "Physics", "PHY").getId();
            AttendanceService attendance = context.attendanceService();
            AttendanceEntry first = attendance.add(subjectId, TODAY, true);

            assertThrows(IllegalArgumentException.class, () -> attendance.add("unknown", TODAY, true));
            assertThrows(IllegalArgumentException.class, () -> attendance.setTarget("unknown", 85));
            assertThrows(IllegalArgumentException.class, () -> attendance.setTarget(subjectId, 101));
            assertThrows(IllegalArgumentException.class, () -> attendance.add(subjectId, null, true));
            assertThrows(IllegalArgumentException.class, () -> attendance.correct(first.id(), null, false));
            assertThrows(NoSuchElementException.class, () -> attendance.correct("unknown", TODAY, false));

            AttendanceService.SubjectAttendance row = row(attendance.snapshot(), subjectId);
            assertEquals(List.of(first), row.history());
            assertEquals(context.settings().getDefaultAttendanceTarget(), row.target());
            assertEquals(1, row.projection().attended());
            assertEquals(1, row.projection().total());
        }
    }

    private static Subject subject(StudyFlowContext context, String name, String code) {
        return context.subjectManager().create(name, code, "", List.of());
    }

    private static AttendanceService.SubjectAttendance row(AttendanceService.Snapshot snapshot, String id) {
        return snapshot.subjects().stream().filter(subject -> subject.subjectId().equals(id))
                .findFirst().orElseThrow();
    }
}
