package com.studyflow.education;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageExamplesTest {
    @Test
    void formatsTheStudyGridAndReadsLegacyTags() {
        assertEquals("Monday 55\nTuesday 45\nWednesday 50\n", LanguageExamples.formatStudyGrid());
        assertEquals(List.of("math", "revision"), LanguageExamples.importLegacyTags("math, revision"));
    }

    @Test
    void insertsAnAgendaItemUsingTheIterator() {
        assertEquals(List.of("Class", "Review", "Exam"),
                LanguageExamples.insertReviewBeforeExam(List.of("Class", "Exam")));
    }

    @Test
    void fieldSelectionUsesTheReferenceTypeAndMethodDispatchUsesTheActualType() {
        LanguageExamples.Page page = new LanguageExamples.StudyPage();
        assertEquals("Page", page.label);
        assertEquals("Study page", page.displayLabel());
    }
}
