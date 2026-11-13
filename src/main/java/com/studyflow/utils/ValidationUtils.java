package com.studyflow.utils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Shared checks for values accepted by academic records. */
public final class ValidationUtils {
    private ValidationUtils() {
    }

    public static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }

    public static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value.strip();
    }

    public static String optionalId(String value, String field) {
        return value == null ? null : requiredText(value, field);
    }

    public static String textOrEmpty(String value) {
        return value == null ? "" : value;
    }

    public static Duration positiveDuration(Duration value, String field) {
        required(value, field);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
        return value;
    }

    public static List<String> tags(List<String> values) {
        required(values, "Tags");
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(requiredText(value, "Tag"));
        }
        return new ArrayList<>(normalized);
    }
}
