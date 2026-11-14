package com.studyflow.service;

import java.util.OptionalLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class AttendanceMathTest {
    @ParameterizedTest
    @MethodSource("regularProjections")
    void projectsAttendedClassesAndAllowedAbsences(long attended, long total, int target,
                                                    double percentage, long needed, long canMiss) {
        AttendanceMath.Projection projection = AttendanceMath.project(attended, total, target);

        assertEquals(attended, projection.attended());
        assertEquals(total, projection.total());
        assertEquals(target, projection.target());
        assertEquals(percentage, projection.percentage(), 1e-10);
        assertEquals(OptionalLong.of(needed), projection.needed());
        assertEquals(OptionalLong.of(canMiss), projection.canMiss());
    }

    private static Stream<Arguments> regularProjections() {
        return Stream.of(
                arguments(0, 0, 75, 0.0, 1, 0),
                arguments(15, 20, 75, 75.0, 0, 0),
                arguments(14, 20, 75, 70.0, 4, 0),
                arguments(20, 25, 80, 80.0, 0, 0),
                arguments(19, 25, 80, 76.0, 5, 0),
                arguments(17, 20, 85, 85.0, 0, 0),
                arguments(16, 20, 85, 80.0, 7, 0),
                arguments(18, 20, 85, 90.0, 0, 1),
                arguments(20, 20, 80, 100.0, 0, 5),
                arguments(31, 40, 77, 77.5, 0, 0),
                arguments(30, 40, 77, 75.0, 4, 0));
    }

    @Test
    void zeroTargetAllowsUnlimitedAbsencesEvenBeforeAnyClasses() {
        AttendanceMath.Projection projection = AttendanceMath.project(0, 0, 0);

        assertEquals(0.0, projection.percentage());
        assertEquals(OptionalLong.of(0), projection.needed());
        assertEquals(OptionalLong.empty(), projection.canMiss());
        assertEquals(OptionalLong.empty(), AttendanceMath.project(1, 10, 0).canMiss());
    }

    @Test
    void perfectTargetDistinguishesNoClassesPerfectAttendanceAndAnAbsence() {
        assertEquals(OptionalLong.of(1), AttendanceMath.project(0, 0, 100).needed());
        AttendanceMath.Projection perfect = AttendanceMath.project(30, 30, 100);
        assertEquals(OptionalLong.of(0), perfect.needed());
        assertEquals(OptionalLong.of(0), perfect.canMiss());

        AttendanceMath.Projection missedOnce = AttendanceMath.project(29, 30, 100);
        assertEquals(OptionalLong.empty(), missedOnce.needed());
        assertEquals(OptionalLong.of(0), missedOnce.canMiss());
    }

    @Test
    void largeCountsRetainExactRoundingAndAvoidIntermediateOverflow() {
        long total = 8_000_000_000_000_000_000L;
        long threshold = 6_000_000_000_000_000_000L;

        assertEquals(OptionalLong.of(0), AttendanceMath.project(threshold, total, 75).needed());
        assertEquals(OptionalLong.of(4), AttendanceMath.project(threshold - 1, total, 75).needed());
        assertEquals(OptionalLong.of(1), AttendanceMath.project(threshold + 1, total, 75).canMiss());
    }

    @Test
    void rejectsImpossibleCountsAndInvalidTargets() {
        assertThrows(IllegalArgumentException.class, () -> AttendanceMath.project(-1, 5, 75));
        assertThrows(IllegalArgumentException.class, () -> AttendanceMath.project(0, -1, 75));
        assertThrows(IllegalArgumentException.class, () -> AttendanceMath.project(5, 4, 75));
        assertThrows(IllegalArgumentException.class, () -> AttendanceMath.project(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> AttendanceMath.project(0, 0, 101));
    }
}
