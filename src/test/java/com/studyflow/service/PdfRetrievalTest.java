package com.studyflow.service;

import com.studyflow.config.StudyFlowContext;
import com.studyflow.model.StudyMaterial;
import com.studyflow.service.MaterialLibrary.ImportCancelledException;
import com.studyflow.service.MaterialLibrary.ImportProgress;
import com.studyflow.service.MaterialLibrary.ImportStage;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class PdfRetrievalTest {
    @TempDir
    Path directory;

    @Test
    void ranksCompleteUnicodeWordMatchesAndCitesThePhysicalPage() throws IOException {
        Path first = writePdf("a-partial.pdf", "The partial result concerns cafés, makers, and artful methods.",
                "A different café is a location.");
        Path second = writePdf("b-evidence.pdf", "", "A printmaking studio.", "Café art uses printmaking.");
        try (StudyFlowContext context = StudyFlowContext.open(directory.resolve("profile"))) {
            MaterialLibrary library = context.materialLibrary();
            StudyMaterial partial = library.upload(first, "Partial evidence", "", null);
            StudyMaterial evidence = library.upload(second, "Evidence", "", null);

            var exact = library.searchPassages("How is CAFE\u0301 art made?", null).passages();
            assertEquals(evidence.id(), exact.getFirst().materialId());
            assertEquals(3, exact.getFirst().page());
            assertTrue(exact.getFirst().text().contains("Café art"));
            assertTrue(MaterialLibrary.preview(exact.getFirst(), "art").contains("Café art"));
            assertTrue(library.searchPassages("art", partial.id()).passages().isEmpty());
            assertTrue(library.searchPassages("what is the", null).passages().isEmpty());
        }
    }

    @Test
    void keepsBothKeywordsAcrossAChunkBoundaryAndBoundsResultsByPage() throws IOException {
        List<String> pages = new ArrayList<>();
        pages.add("padding ".repeat(111) + "elastic modulus describes this material");
        for (int index = 0; index < 14; index++) {
            pages.add("Elastic properties are discussed here.");
        }
        Path source = writePdf("elasticity.pdf", pages.toArray(String[]::new));
        try (StudyFlowContext context = StudyFlowContext.open(directory.resolve("profile"))) {
            MaterialLibrary library = context.materialLibrary();
            StudyMaterial elastic = library.upload(source, "Elasticity", "", null);

            var passages = library.searchPassages("elastic modulus", elastic.id()).passages();
            assertEquals(12, passages.size());
            assertEquals(12, passages.stream().map(MaterialLibrary.Passage::page).distinct().count());
            assertEquals(1, passages.getFirst().page());
            assertTrue(passages.getFirst().text().contains("elastic modulus"));
            assertTrue(MaterialLibrary.preview(passages.getFirst(), "modulus").contains("modulus"));
        }
    }

    @Test
    void repairsInvalidLegacyAndMissingIndexesAndReindexesAChangedPdf() throws IOException {
        Path profile = directory.resolve("profile");
        Path source = writePdf("first.pdf", "Counterpoint is studied in this example.");
        try (StudyFlowContext context = StudyFlowContext.open(profile)) {
            MaterialLibrary library = context.materialLibrary();
            StudyMaterial material = library.upload(source, "Handout", "", null);
            Path cache = indexPath(profile, material);
            String intact = Files.readString(cache);

            Files.writeString(cache, "1\tRm9yZ2VkIGNhY2hlIHRleHQ=\n");
            assertEquals(List.of(1), library.searchPassages("counterpoint", material.id()).passages().stream()
                    .map(MaterialLibrary.Passage::page).toList());
            assertEquals(intact, Files.readString(cache));
            Files.delete(cache);
            assertFalse(library.searchPassages("counterpoint", material.id()).passages().isEmpty());
            assertEquals(intact, Files.readString(cache));

            Path managed = library.openPath(material.id());
            Path updated = writePdf("updated.pdf", "Fugue is now the topic.", "The final page reviews polyphony.");
            Files.copy(updated, managed, StandardCopyOption.REPLACE_EXISTING);
            Files.setLastModifiedTime(managed, FileTime.from(Instant.parse("2030-01-01T00:00:00Z")));
            var result = library.searchPassages("polyphony", material.id());
            assertEquals(2, result.passages().getFirst().page());
            assertTrue(result.warnings().isEmpty());
            assertTrue(library.searchPassages("counterpoint", material.id()).passages().isEmpty());
        }
    }

    @Test
    void isolatesUnrepairableSourcesAndContinuesSearchingHealthyFiles() throws IOException {
        Path profile = directory.resolve("profile");
        Path first = writePdf("missing.pdf", "Original spectroscopy notes.");
        Path second = writePdf("healthy.pdf", "Correct spectroscopy lines remain available.");
        try (StudyFlowContext context = StudyFlowContext.open(profile)) {
            MaterialLibrary library = context.materialLibrary();
            StudyMaterial missing = library.upload(first, "Missing", "", null);
            StudyMaterial healthy = library.upload(second, "Available", "", null);
            Path cache = indexPath(profile, missing);
            List<String> valid = Files.readAllLines(cache);
            Files.delete(library.openPath(missing.id()));
            var cached = library.searchPassages("spectroscopy", missing.id());
            assertEquals(missing.id(), cached.passages().getFirst().materialId());
            assertTrue(cached.warnings().getFirst().message().contains("managed PDF is missing"));

            String damaged = String.join("\n", valid.subList(0, valid.size() - 1));
            Files.writeString(cache, damaged);
            var result = library.searchPassages("spectroscopy", null);
            assertEquals(List.of(healthy.id()), result.passages().stream()
                    .map(MaterialLibrary.Passage::materialId).toList());
            assertEquals(List.of(missing.id()), result.warnings().stream()
                    .map(MaterialLibrary.RetrievalWarning::materialId).toList());
            assertEquals(damaged, Files.readString(cache));
        }
    }

    @Test
    void reportsOcrLimitForBlankOrImageOnlyPdfWithoutInventingPassages() throws IOException {
        Path source = writePdf("blank.pdf", "");
        try (StudyFlowContext context = StudyFlowContext.open(directory.resolve("profile"))) {
            MaterialLibrary library = context.materialLibrary();
            StudyMaterial empty = library.upload(source, "Blank", "", null);

            var result = library.searchPassages("physics", empty.id());
            assertTrue(result.passages().isEmpty());
            assertTrue(result.warnings().getFirst().message().contains("OCR"));
        }
    }

    @ParameterizedTest
    @EnumSource(ImportStage.class)
    void cancellationLeavesNoAssetIndexOrMetadata(ImportStage phase) throws IOException {
        Path source = writePdf("cancel.pdf", "First page about binary trees.", "Second page about heaps.");
        Path profile = directory.resolve("profile");
        AtomicBoolean cancelled = new AtomicBoolean();
        List<ImportProgress> updates = new ArrayList<>();
        byte[] original = Files.readAllBytes(source);
        try (StudyFlowContext context = StudyFlowContext.open(profile)) {
            MaterialLibrary library = context.materialLibrary();
            assertThrows(ImportCancelledException.class,
                    () -> library.upload(source, "Cancelled", "", null, cancelled::get, progress -> {
                        updates.add(progress);
                        if (progress.stage() == phase && (phase != ImportStage.EXTRACTING || progress.completed() > 0)) {
                            cancelled.set(true);
                        }
                    }));
            assertTrue(updates.stream().anyMatch(progress -> progress.stage() == phase));
            assertTrue(library.findAll().isEmpty());
            assertArrayEquals(original, Files.readAllBytes(source));
            for (String path : List.of("materials/files", "materials/index")) {
                try (var leftovers = Files.list(profile.resolve(path))) {
                    assertEquals(0, leftovers.count());
                }
            }
        }
        try (StudyFlowContext reopened = StudyFlowContext.open(profile)) {
            assertTrue(reopened.materialLibrary().findAll().isEmpty());
        }
    }

    @Test
    void rejectsOversizedAndPasswordProtectedPdfsWithoutPublishingThem() throws IOException {
        Path huge = directory.resolve("huge.pdf");
        try (var file = new RandomAccessFile(huge.toFile(), "rw")) {
            file.setLength(PdfPassageIndex.MAX_PDF_BYTES + 1L);
        }
        Path locked = directory.resolve("locked.pdf");
        try (var document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("owner", "reader", new AccessPermission()));
            document.save(locked.toFile());
        }
        try (StudyFlowContext context = StudyFlowContext.open(directory.resolve("profile"))) {
            MaterialLibrary library = context.materialLibrary();
            assertTrue(assertThrows(IOException.class,
                    () -> library.upload(huge, "Huge", "", null)).getMessage().contains("100 MB"));
            assertTrue(assertThrows(IOException.class,
                    () -> library.upload(locked, "Locked", "", null)).getMessage().contains("password"));
            assertTrue(library.findAll().isEmpty());
        }
    }

    @Test
    void rejectsOversizedPageCollectionsBeforeExtractingAnyText() throws IOException {
        Path source = directory.resolve("too-many-pages.pdf");
        try (var document = new PDDocument()) {
            for (int index = 0; index <= 2_000; index++) {
                document.addPage(new PDPage());
            }
            document.save(source.toFile());
        }
        try (StudyFlowContext context = StudyFlowContext.open(directory.resolve("profile"))) {
            List<ImportProgress> updates = new ArrayList<>();
            MaterialLibrary library = context.materialLibrary();
            IOException oversized = assertThrows(IOException.class,
                    () -> library.upload(source, "Too long", "", null, () -> false, updates::add));
            assertTrue(oversized.getMessage().contains("2,000 pages"));
            assertTrue(updates.stream().allMatch(progress -> progress.stage() == ImportStage.COPYING));
            assertTrue(library.findAll().isEmpty());
        }
    }

    private static Path indexPath(Path profile, StudyMaterial material) {
        return profile.resolve("materials/index").resolve(material.id() + ".txt");
    }

    private Path writePdf(String fileName, String... pages) throws IOException {
        Path path = directory.resolve(fileName);
        try (var document = new PDDocument()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String pageText : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (!pageText.isEmpty()) {
                    try (var stream = new PDPageContentStream(document, page)) {
                        stream.beginText();
                        stream.setFont(font, 10);
                        stream.setLeading(16);
                        stream.newLineAtOffset(55, 730);
                        for (String line : lines(pageText)) {
                            stream.showText(line);
                            stream.newLine();
                        }
                        stream.endText();
                    }
                }
            }
            document.save(path.toFile());
        }
        return path;
    }

    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        int offset = 0;
        while (offset < text.length()) {
            int end = Math.min(text.length(), offset + 75);
            if (end < text.length()) {
                int boundary = text.lastIndexOf(' ', end) + 1;
                if (boundary > offset) {
                    end = boundary;
                }
            }
            lines.add(text.substring(offset, end).strip());
            offset = end;
        }
        return lines;
    }
}
