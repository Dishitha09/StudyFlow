package com.studyflow.utils;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/** Matches every query term as a partial string in any supplied field. */
public final class SearchText {
    private static final Pattern WORD = Pattern.compile("\\S+", Pattern.UNICODE_CHARACTER_CLASS);

    private SearchText() {
    }

    public static boolean matches(String query, String... fields) {
        if (query == null) {
            return false;
        }
        String[] terms = WORD.matcher(query.toLowerCase(Locale.ROOT)).results()
                .map(result -> result.group())
                .toArray(String[]::new);
        if (terms.length == 0) {
            return false;
        }
        String[] searchableFields = Arrays.stream(fields)
                .map(ValidationUtils::textOrEmpty)
                .map(field -> field.toLowerCase(Locale.ROOT))
                .toArray(String[]::new);
        return Arrays.stream(terms)
                .allMatch(term -> Arrays.stream(searchableFields).anyMatch(field -> field.contains(term)));
    }
}
