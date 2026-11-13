package com.studyflow.education;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Pattern examples for the course. The application wires its own services explicitly. */
class PatternExamplesTest {
    @Test
    void enumSingletonHasOneInstance() {
        assertSame(TermDefaults.INSTANCE, TermDefaults.valueOf("INSTANCE"));
    }

    @Test
    void abstractFactoryCreatesMatchingLabelsAndDecoratorAddsOneBehavior() {
        Map<String, StudyLabels> labelFamilies = new HashMap<>();
        labelFamilies.put("coursework", new CourseworkLabels());
        StudyLabels labels = labelFamilies.get("coursework");
        Label highlighted = new HighlightedLabel(labels.review());
        assertEquals("Assignment", labels.task().text());
        assertEquals("★ Review assignment", highlighted.text());
    }

    private enum TermDefaults {
        INSTANCE
    }

    private interface Label {
        String text();
    }

    private interface StudyLabels {
        Label task();
        Label review();
    }

    private static final class CourseworkLabels implements StudyLabels {
        @Override
        public Label task() {
            return () -> "Assignment";
        }

        @Override
        public Label review() {
            return () -> "Review assignment";
        }
    }

    private record HighlightedLabel(Label base) implements Label {
        @Override
        public String text() {
            return "★ " + base.text();
        }
    }
}
