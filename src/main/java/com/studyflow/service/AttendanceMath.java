package com.studyflow.service;

import java.math.BigInteger;
import java.util.OptionalLong;

/** Projects attendance using exact integer comparisons before formatting percentages. */
public final class AttendanceMath {
    private static final BigInteger HUNDRED = BigInteger.valueOf(100);

    private AttendanceMath() { }

    public static Projection project(long attended, long total, int target) {
        if (attended < 0 || total < attended || target < 0 || target > 100) {
            throw new IllegalArgumentException("Invalid attendance counts or target.");
        }
        double percentage = total == 0 ? 0 : 100.0 * attended / total;
        if (target == 0) {
            return new Projection(attended, total, target, percentage, OptionalLong.of(0), OptionalLong.empty());
        }
        if (total == 0) {
            return new Projection(attended, total, target, 0, OptionalLong.of(1), OptionalLong.of(0));
        }
        BigInteger rate = BigInteger.valueOf(target);
        BigInteger earned = BigInteger.valueOf(attended).multiply(HUNDRED);
        BigInteger deficit = BigInteger.valueOf(total).multiply(rate).subtract(earned);
        if (deficit.signum() <= 0) {
            return new Projection(attended, total, target, percentage, OptionalLong.of(0),
                    exact(deficit.negate().divide(rate)));
        }
        OptionalLong needed = target == 100 ? OptionalLong.empty()
                : exact(deficit.add(BigInteger.valueOf(99L - target)).divide(BigInteger.valueOf(100L - target)));
        return new Projection(attended, total, target, percentage, needed, OptionalLong.of(0));
    }

    private static OptionalLong exact(BigInteger value) {
        return OptionalLong.of(value.longValueExact());
    }

    public record Projection(long attended, long total, int target, double percentage,
                             OptionalLong needed, OptionalLong canMiss) { }
}
