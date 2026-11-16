package com.studyflow.service;

import com.studyflow.config.StudyFlowContext;
import com.studyflow.model.StudyMaterial;
import com.studyflow.service.StudyAssistant.Mode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialLibraryTest {
    @TempDir
    Path directory;

    @Test
    void uploadsSearchablePagesAndRetainsTheirSourceReferencesAfterReopen() throws IOException {
        Path profile = directory.resolve("profile");
        Path original = writePdf("memory.pdf", "Virtual memory maps address spaces.",
                "Clock replacement algorithm tracks reference bits.");
        StudyMaterial material;
        Path managedPdf;
        try (StudyFlowContext context = StudyFlowContext.open(profile)) {
            String subjectId = context.subjectManager().create("Operating Systems", "OS", "", List.of()).getId();
            MaterialLibrary library = context.materialLibrary();
            material = library.upload(original, "Memory management", "Lecture slides", subjectId);
            managedPdf = library.openPath(material.id());

            assertEquals("Memory management", material.title());
            assertEquals("memory.pdf", material.fileName());
            assertEquals("Lecture slides", material.description());
            assertEquals(subjectId, material.subjectId());
            assertTrue(managedPdf.toAbsolutePath().normalize().startsWith(profile));
            assertTrue(Files.isRegularFile(managedPdf));
            assertEquals(-1, Files.mismatch(original, managedPdf));
            assertEquals(List.of(material), library.search("LECTURE"));

            var matches = library.retrieve("replacement", material.id());
            assertEquals(1, matches.size());
            var passage = matches.getFirst();
            assertEquals(material.id(), passage.materialId());
            assertEquals("memory.pdf", passage.fileName());
            assertEquals(2, passage.page());
            assertTrue(passage.text().contains("tracks reference bits"));
        }

        Files.delete(original);
        try (StudyFlowContext reopened = StudyFlowContext.open(profile)) {
            MaterialLibrary library = reopened.materialLibrary();
            assertEquals(List.of(material), library.findAll());
            assertEquals(managedPdf, library.openPath(material.id()));
            Files.move(managedPdf, directory.resolve("managed-copy.pdf"));
            assertEquals(List.of(1, 2), library.retrieve("", material.id()).stream()
                    .map(passage -> passage.page()).sorted().toList());
            assertTrue(library.retrieve("virtual", material.id()).getFirst().text().contains("address spaces"));
        }
    }

    @Test
    void restrictsRetrievalToTheSelectedMaterialAndRemovesDeletedSources() throws IOException {
        Path profile = directory.resolve("profile");
        Path first = writePdf("sorting.pdf", "Sorting arranges items in a consistent order.");
        Path second = writePdf("network.pdf", "Packet routing selects paths across networks.");
        StudyMaterial network;
        try (StudyFlowContext context = StudyFlowContext.open(profile)) {
            MaterialLibrary library = context.materialLibrary();
            StudyMaterial sorting = library.upload(first, "Sorting", "", null);
            network = library.upload(second, "Networking", "", null);
            assertTrue(library.retrieve("routing", sorting.id()).isEmpty());
            assertEquals(network.id(), library.retrieve("routing", null).getFirst().materialId());

            Path managed = library.openPath(sorting.id());
            library.delete(sorting.id());
            assertFalse(Files.exists(managed));
            assertTrue(Files.isRegularFile(first));
            assertTrue(library.retrieve("sorting", null).isEmpty());
        }

        try (StudyFlowContext reopened = StudyFlowContext.open(profile)) {
            assertEquals(List.of(network), reopened.materialLibrary().findAll());
        }
    }

    @Test
    void rejectsMalformedPdfWithoutCommittingAMaterial() throws IOException {
        Path invalid = Files.writeString(directory.resolve("broken.pdf"), "Test file with no PDF structure.");
        Path profile = directory.resolve("profile");
        try (StudyFlowContext context = StudyFlowContext.open(profile)) {
            MaterialLibrary library = context.materialLibrary();
            Exception failure = assertThrows(Exception.class, () -> library.upload(invalid, "Broken", "", null));
            assertTrue(failure instanceof IOException || failure instanceof IllegalArgumentException);
            assertTrue(library.findAll().isEmpty());
        }
        try (StudyFlowContext reopened = StudyFlowContext.open(profile)) {
            assertTrue(reopened.materialLibrary().findAll().isEmpty());
        }
    }

    @Test
    void keepsBlankPdfInTheLibraryWithoutInventingPassages() throws IOException {
        Path blank = writePdf("blank.pdf", "");
        try (StudyFlowContext context = StudyFlowContext.open(directory.resolve("profile"))) {
            MaterialLibrary library = context.materialLibrary();
            StudyMaterial material = library.upload(blank, "Blank handout", "", null);
            assertEquals(List.of(material), library.findAll());
            assertTrue(Files.isRegularFile(library.openPath(material.id())));
            assertTrue(library.retrieve("", material.id()).isEmpty());
            assertTrue(library.retrieve("nothing", material.id()).isEmpty());
        }
    }

    @Test
    void savesAssistantOutputAndSourcesLocallyAndClearsHistoryWithoutACopy() throws IOException {
        Path profile = directory.resolve("history-profile");
        Path original = writePdf("database.pdf", "Transaction isolation controls concurrent access.");
        String materialId;
        try (StudyFlowContext context = StudyFlowContext.open(profile)) {
            materialId = context.materialLibrary().upload(original, "Databases", "", null).id();
            StudyAssistant assistant = context.studyAssistant();
            var saved = assistant.run("isolation", materialId, Mode.SHORT_SUMMARY, false, true);
            assertFalse(saved.historySaveFailed());
            assertEquals(1, assistant.history().size());
            assistant.run("transaction", materialId, Mode.KEY_POINTS, false, false);
            assistant.run("networking", materialId, Mode.KEY_POINTS, false, true);
            assertEquals(1, assistant.history().size());
        }
        Path snapshot = profile.resolve("materials/assistant-history.json");
        try (StudyFlowContext reopened = StudyFlowContext.open(profile)) {
            var assistant = reopened.studyAssistant();
            var saved = assistant.history().getFirst();
            assertEquals("isolation", saved.topic());
            assertEquals(materialId, saved.materialId());
            assertEquals("Databases", saved.materialTitle());
            assertEquals(Mode.SHORT_SUMMARY.name(), saved.mode());
            assertTrue(saved.output().contains("concurrent access"));
            assertEquals("database.pdf", saved.references().getFirst().fileName());
            assertEquals(1, saved.references().getFirst().page());
            assertFalse(saved.generated());
            assistant.run("transaction", materialId, Mode.KEY_POINTS, false, true);
            assertEquals(List.of("transaction", "isolation"),
                    assistant.history().stream().map(entry -> entry.topic()).toList());
            assertTrue(Files.isRegularFile(snapshot));
            assistant.clearHistory();
            assertTrue(assistant.history().isEmpty());
            assertFalse(Files.exists(snapshot));
        }
        try (var backups = Files.list(profile.resolve("backups"))) {
            assertTrue(backups.noneMatch(path -> path.getFileName().toString().startsWith("assistant-history")));
        }
        try (StudyFlowContext reopened = StudyFlowContext.open(profile)) {
            assertTrue(reopened.studyAssistant().history().isEmpty());
        }
    }

    @Test
    void damagedOptionalHistoryDoesNotPreventGeneratingOrClearingOutput() throws IOException {
        Path profile = directory.resolve("damaged-history-profile");
        Path original = writePdf("algorithms.pdf", "Binary search uses the sorted order.");
        try (StudyFlowContext context = StudyFlowContext.open(profile)) {
            context.materialLibrary().upload(original, "Algorithms", "", null);
        }
        Path snapshot = profile.resolve("materials/assistant-history.json");
        Files.writeString(snapshot, "damaged history");
        try (StudyFlowContext context = StudyFlowContext.open(profile)) {
            StudyAssistant assistant = context.studyAssistant();
            var run = assistant.run("binary", null, Mode.SHORT_SUMMARY, false, true);
            assertTrue(run.historySaveFailed());
            assertTrue(run.answer().text().contains("sorted order"));
            assertEquals("damaged history", Files.readString(snapshot));
            assistant.clearHistory();
            assertTrue(assistant.history().isEmpty());
        }
    }

    private Path writePdf(String name, String... pageTexts) throws IOException {
        Path file = directory.resolve(name);
        try (PDDocument document = new PDDocument()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (!pageText.isEmpty()) {
                    try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                        stream.beginText();
                        stream.setFont(font, 12);
                        stream.newLineAtOffset(60, 700);
                        stream.showText(pageText);
                        stream.endText();
                    }
                }
            }
            document.save(file.toFile());
        }
        return file;
    }
}
