package com.studyflow.education;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.StringTokenizer;

/** Isolated examples for discussing syllabus concepts in a viva. */
final class LanguageExamples {
    private static final String[] DAYS;

    static {
        DAYS = new String[]{"Monday", "Tuesday", "Wednesday"};
    }

    private LanguageExamples() {
    }

    static String formatStudyGrid() {
        int[][] minutes = {{25, 30}, {0, 45}, {50, 0}};
        StringBuffer result = new StringBuffer();
        for (int day = 0; day < minutes.length; day++) {
            result.append(DAYS[day]).append(' ')
                    .append(minutes[day][0] + minutes[day][1]).append('\n');
        }
        return result.toString();
    }

    static List<String> importLegacyTags(String commaSeparated) {
        StringTokenizer tokens = new StringTokenizer(commaSeparated, ",");
        List<String> tags = new ArrayList<>();
        while (tokens.hasMoreTokens()) {
            tags.add(tokens.nextToken().strip());
        }
        return tags;
    }

    static List<String> insertReviewBeforeExam(List<String> labels) {
        List<String> agenda = new LinkedList<>(labels);
        ListIterator<String> position = agenda.listIterator();
        while (position.hasNext()) {
            if (position.next().equals("Exam")) {
                position.previous();
                position.add("Review");
                break;
            }
        }
        return agenda;
    }

    static class Page {
        final String label = "Page";

        String displayLabel() {
            return label;
        }
    }

    static final class StudyPage extends Page {
        final String label = "Study page";

        @Override
        String displayLabel() {
            return label;
        }
    }
}
