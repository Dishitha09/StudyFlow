package com.studyflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyflow.config.StudyFlowContext;
import com.studyflow.interfaces.AIProvider;
import com.studyflow.model.QuestionSet;
import com.studyflow.repository.FileStorage;
import com.studyflow.repository.StudyHistoryRepository;
import com.studyflow.service.StudyAssistant.Difficulty;
import com.studyflow.service.StudyAssistant.Mode;
import com.studyflow.service.StudyAssistant.Request;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class StudyAssistantTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MCQ = """
            {"questions":[{"prompt":"What bends when light crosses the surface?",
            "choices":["The light path","The prism","The lamp","The experiment"],
            "answerIndex":0,"answer":"","explanation":"Refraction changes the light path.","sourceIds":["S1"]}]}
            """;
    private static final String WRITTEN = """
            {"questions":[{"prompt":"Explain what happens during refraction.","choices":[],
            "answerIndex":-1,"answer":"A change of medium bends the light path.",
            "explanation":"The second page discusses refraction.","sourceIds":["S1"]}]}
            """;

    @TempDir
    Path directory;

    @Test
    void previewsExactSelectedPassagesBeforeAnyCallAndRetriesWithoutDuplicateHistory() throws IOException {
        Path profile = directory.resolve("questions-profile");
        FakeProvider provider = new FakeProvider(MCQ);
        String entryId;
        try (StudyFlowContext context = StudyFlowContext.open(profile);
             FileStorage historyFile = new FileStorage(profile)) {
            String pdf = uploadFixture(context);
            var assistant = new StudyAssistant(context.materialLibrary(), provider, new StudyHistoryRepository(historyFile),
                    Clock.systemUTC());
            var prepared = assistant.prepare(new Request("refraction", pdf, Mode.MCQ, true, true, 1,
                    Difficulty.ADVANCED));
            assertTrue(prepared.requiresConsent());
            assertEquals(0, provider.calls);
            assertTrue(assistant.history().isEmpty());
            assertEquals(List.of(2), prepared.sources().stream().map(MaterialLibrary.Passage::page).toList());
            assertTrue(prepared.prompt().contains("Refraction changes the light path."));
            assertFalse(prepared.prompt().contains("Momentum is conserved."));
            assertTrue(prepared.prompt().contains("exactly 1"));
            assertTrue(prepared.prompt().contains("advanced"));

            var first = assistant.execute(prepared);
            assertEquals(prepared.prompt(), provider.sentPrompt);
            assertEquals("studyflow_questions", provider.schemaName);
            var format = JSON.readTree(provider.schema);
            assertFalse(format.path("additionalProperties").asBoolean());
            assertEquals("S1", format.at("/properties/questions/items/properties/sourceIds/items/enum/0").asText());
            assertTrue(first.answer().generated());
            assertEquals(QuestionSet.Type.MCQ, first.answer().questions().type());
            assertEquals(0, first.answer().questions().questions().getFirst().answerIndex());
            assertTrue(first.answer().text().contains("[physics.pdf, p. 2]"));
            assertFalse(first.historySaveFailed());

            assertEquals(first, assistant.execute(prepared));
            assertEquals(1, provider.calls);
            assertEquals(1, assistant.history().size());
            var entry = assistant.history().getFirst();
            entryId = entry.entryId();
            assertEquals(prepared.id(), entryId);
            assertEquals(pdf, entry.references().getFirst().materialId());
            assertEquals("S1", entry.references().getFirst().sourceId());
            assertEquals("ADVANCED", entry.difficulty());
        }
        try (StudyFlowContext reopened = StudyFlowContext.open(profile)) {
            var assistant = reopened.studyAssistant();
            var entry = assistant.history().getFirst();
            assertEquals(entryId, entry.entryId());
            assertEquals(1, entry.questions().questions().size());
            assertTrue(assistant.deleteHistory(entryId).isEmpty());
            assertFalse(Files.exists(profile.resolve("materials/assistant-history.json")));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"SHORT_ANSWER", "LONG_ANSWER"})
    void createsWrittenQuestionTypesWithModelAnswers(Mode mode) throws IOException {
        try (StudyFlowContext context = StudyFlowContext.open(directory.resolve("written"))) {
            String pdf = uploadFixture(context);
            var assistant = new StudyAssistant(context.materialLibrary(), new FakeProvider(WRITTEN));
            var result = assistant.execute(assistant.prepare(new Request("refraction", pdf, mode,
                    true, false, 1, Difficulty.STANDARD)));
            assertEquals(mode.name(), result.answer().questions().type().name());
            var question = result.answer().questions().questions().getFirst();
            assertTrue(question.choices().isEmpty());
            assertEquals(-1, question.answerIndex());
            assertTrue(question.answerText().contains("bends the light path"));
            assertTrue(assistant.history().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"index", "source", "duplicate choices", "count", "unexpected field", "unparsed"})
    void rejectsUntrustedQuestionResponsesWithoutCreatingHistory(String problem) throws IOException {
        String invalid = switch (problem) {
            case "index" -> MCQ.replace("\"answerIndex\":0", "\"answerIndex\":4");
            case "source" -> MCQ.replace("\"S1\"", "\"S99\"");
            case "duplicate choices" -> MCQ.replace("The lamp", "the prism");
            case "count" -> "{\"questions\":[]}";
            case "unexpected field" -> MCQ.replace("\"answer\":\"\"", "\"answer\":\"\",\"extra\":true");
            default -> MCQ + " trailing words";
        };
        try (StudyFlowContext context = StudyFlowContext.open(directory.resolve("invalid"))) {
            String pdf = uploadFixture(context);
            var assistant = new StudyAssistant(context.materialLibrary(), new FakeProvider(invalid));
            var prepared = assistant.prepare(new Request("refraction", pdf, Mode.MCQ, true, true, 1,
                    Difficulty.STANDARD));
            IOException error = assertThrows(IOException.class, () -> assistant.execute(prepared));
            assertTrue(error.getMessage().contains("valid, source-linked"));
            assertTrue(assistant.history().isEmpty());
        }
    }

    @Test
    void requiresAConfiguredProviderForQuestionsAndKeepsLocalExtractsAvailable() throws IOException {
        try (StudyFlowContext context = StudyFlowContext.open(directory.resolve("offline"))) {
            String pdf = uploadFixture(context);
            var assistant = new StudyAssistant(context.materialLibrary(), null);
            assertThrows(IllegalArgumentException.class, () -> new Request("refraction", pdf, Mode.MCQ, false,
                    false, 1, Difficulty.STANDARD));
            assertThrows(IllegalArgumentException.class, () -> new Request("refraction", pdf, Mode.MCQ, true,
                    false, 0, Difficulty.STANDARD));
            assertThrows(IOException.class, () -> assistant.prepare(new Request("refraction", pdf, Mode.MCQ, true,
                    false, 1, Difficulty.STANDARD)));
            var local = assistant.prepare(new Request("refraction", pdf, Mode.EXPLAIN, false, false, 1,
                    Difficulty.STANDARD));
            assertFalse(local.requiresConsent());
            var answer = assistant.execute(local).answer();
            assertFalse(answer.generated());
            assertNull(answer.questions());
            assertTrue(answer.text().contains("Refraction changes the light path."));
        }
    }

    @Test
    void legacyHistoryCanBeReopenedAndOneEntryCanBeDeletedIndependently() throws IOException {
        Path profile = directory.resolve("legacy");
        try (StudyFlowContext context = StudyFlowContext.open(profile)) {
            Files.writeString(profile.resolve("materials/assistant-history.json"), """
                    [{"createdAt":"2026-11-13T10:00:00Z","topic":"Legacy one","mode":"SHORT_SUMMARY",
                    "materialId":null,"materialTitle":"","generated":false,"output":"First extract.",
                    "references":[{"fileName":"a.pdf","page":1}]},
                    {"createdAt":"2026-11-13T10:00:01Z","topic":"Legacy two","mode":"EXPLAIN",
                    "materialId":null,"materialTitle":"","generated":false,"output":"Second extract.",
                    "references":[{"fileName":"b.pdf","page":2}]}]
                    """);
            var assistant = context.studyAssistant();
            var first = assistant.history().getFirst();
            var second = assistant.history().getLast();
            assertNotEquals(first.entryId(), second.entryId());
            assertEquals(List.of(second), assistant.deleteHistory(first.entryId()));
        }
        try (StudyFlowContext reopened = StudyFlowContext.open(profile)) {
            assertEquals(List.of("Legacy two"), reopened.studyAssistant().history().stream()
                    .map(entry -> entry.topic()).toList());
        }
    }

    private String uploadFixture(StudyFlowContext context) throws IOException {
        Path original = directory.resolve("physics.pdf");
        try (PDDocument document = new PDDocument()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : List.of("Momentum is conserved.", "Refraction changes the light path.")) {
                var page = new PDPage();
                document.addPage(page);
                try (var output = new PDPageContentStream(document, page)) {
                    output.beginText();
                    output.setFont(font, 12);
                    output.newLineAtOffset(40, 700);
                    output.showText(text);
                    output.endText();
                }
            }
            document.save(original.toFile());
        }
        return context.materialLibrary().upload(original, "Optics notes", "", null).id();
    }

    private static final class FakeProvider implements AIProvider {
        private final String response;
        private int calls;
        private String sentPrompt;
        private String schemaName;
        private String schema;

        private FakeProvider(String response) {
            this.response = response;
        }

        @Override public boolean configured() { return true; }

        @Override
        public String generate(String prompt) {
            throw new AssertionError("Question requests must use structured generation.");
        }

        @Override
        public String generateStructured(String prompt, String name, String schema) {
            calls++;
            sentPrompt = prompt;
            schemaName = name;
            this.schema = schema;
            return response;
        }
    }
}
