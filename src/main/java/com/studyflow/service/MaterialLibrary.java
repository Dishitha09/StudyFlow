package com.studyflow.service;

import com.studyflow.interfaces.Repository;
import com.studyflow.model.StudyMaterial;
import com.studyflow.utils.IdGenerator;
import com.studyflow.utils.SearchText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** Imports managed PDFs and searches their cached, page-numbered passages. */
public final class MaterialLibrary {
    private static final int CHUNK = 900;
    private static final long MAX_PDF_BYTES = 100 * 1024 * 1024;
    private static final Set<String> COMMON = Set.of("a", "an", "and", "are", "explain", "for", "how", "in",
            "is", "of", "on", "the", "to", "what", "with");
    private static final Pattern TERMS = Pattern.compile("[\\p{L}\\p{N}]+");

    private final Path files;
    private final Path index;
    private final Repository<StudyMaterial> materials;
    private final Predicate<String> subjectExists;
    private final Clock clock;

    public MaterialLibrary(Path directory, Repository<StudyMaterial> materials, Predicate<String> subjectExists,
                           Clock clock) {
        files = directory.resolve("files");
        index = directory.resolve("index");
        this.materials = materials;
        this.subjectExists = subjectExists;
        this.clock = clock;
    }

    public StudyMaterial upload(Path source, String title, String description, String subjectId) throws IOException {
        if (!Files.isRegularFile(source) || !source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Choose a PDF file.");
        }
        if (Files.size(source) > MAX_PDF_BYTES) {
            throw new IllegalArgumentException("Choose a PDF smaller than 100 MB.");
        }
        StudyMaterial material = new StudyMaterial(IdGenerator.next(), title, source.getFileName().toString(),
                description, subjectId, clock.instant());
        if (material.subjectId() != null && !subjectExists.test(material.subjectId())) {
            throw new IllegalArgumentException("Choose an existing subject.");
        }
        Files.createDirectories(files);
        Files.createDirectories(index);
        Path pdf = pdfPath(material.id());
        Path cache = cachePath(material.id());
        try {
            Files.copy(source, pdf);
            extract(pdf, cache);
            materials.insert(material);
            return material;
        } catch (IOException | RuntimeException failure) {
            clean(cache, failure);
            clean(pdf, failure);
            throw failure;
        }
    }

    public List<StudyMaterial> findAll() {
        return materials.findAll();
    }

    public List<StudyMaterial> search(String query) {
        if (query == null || query.isBlank()) {
            return findAll();
        }
        return findAll().stream().filter(material -> SearchText.matches(query, material.title(), material.fileName(),
                material.description())).toList();
    }

    public boolean references(String subjectId) {
        return findAll().stream().anyMatch(material -> subjectId.equals(material.subjectId()));
    }

    public synchronized List<Passage> retrieve(String query, String materialId) throws IOException {
        List<String> words = TERMS.matcher(query == null ? "" : query.toLowerCase(Locale.ROOT)).results()
                .map(result -> result.group()).filter(word -> !COMMON.contains(word)).distinct().toList();
        List<StudyMaterial> candidates = materialId == null ? findAll() : List.of(require(materialId));
        List<ScoredPassage> matches = new ArrayList<>();
        for (StudyMaterial item : candidates) {
            try (var reader = Files.newBufferedReader(cachePath(item.id()), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int separator = line.indexOf('\t');
                    int page;
                    String body;
                    try {
                        page = Integer.parseInt(line.substring(0, separator));
                        body = new String(Base64.getDecoder().decode(line.substring(separator + 1)),
                                StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException | IndexOutOfBoundsException error) {
                        throw new IOException("Cannot read the cached text for " + item.fileName(), error);
                    }
                    String normalized = body.toLowerCase(Locale.ROOT);
                    long score = words.stream().filter(normalized::contains).count();
                    if (score > 0 || words.isEmpty()) {
                        matches.add(new ScoredPassage(score, new Passage(item.id(), item.fileName(), page, body)));
                    }
                }
            }
        }
        return matches.stream().sorted(Comparator.comparingLong(ScoredPassage::score).reversed())
                .limit(12).map(ScoredPassage::passage).toList();
    }

    public synchronized Path openPath(String id) throws IOException {
        Path path = pdfPath(require(id).id());
        if (!Files.isRegularFile(path)) {
            throw new IOException("The stored PDF is missing.");
        }
        return path;
    }

    public synchronized void delete(String id) throws IOException {
        StudyMaterial material = require(id);
        Path pdf = pdfPath(material.id());
        Path cache = cachePath(material.id());
        Path stagedPdf = null;
        Path stagedCache = null;
        try {
            stagedPdf = stageRemoval(pdf);
            stagedCache = stageRemoval(cache);
            if (!materials.delete(material.id())) {
                throw new IOException("The study material changed. Reload the library and try again.");
            }
        } catch (IOException | RuntimeException failure) {
            boolean cacheRestored = restoreStaged(stagedCache, cache, failure);
            boolean pdfRestored = restoreStaged(stagedPdf, pdf, failure);
            if (!cacheRestored || !pdfRestored) {
                throw new IOException("Removal failed and a staged file could not be restored. "
                        + "The staged copy was kept in the StudyFlow materials folder.", failure);
            }
            throw failure;
        }

        IOException cleanup = new IOException("The study material was removed, but temporary files could not be "
                + "deleted. Close the PDF viewer and check the StudyFlow materials folder.");
        clean(stagedCache, cleanup);
        clean(stagedPdf, cleanup);
        if (cleanup.getSuppressed().length != 0) {
            throw cleanup;
        }
    }

    private StudyMaterial require(String id) {
        return materials.findById(id).orElseThrow(() -> new NoSuchElementException("Study material not found."));
    }

    private Path pdfPath(String id) { return files.resolve(id + ".pdf"); }
    private Path cachePath(String id) { return index.resolve(id + ".txt"); }

    private static Path stageRemoval(Path source) throws IOException {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException missing) {
            return null;
        } catch (IOException | SecurityException denied) {
            throw new IOException("Cannot access a managed material file. No material was removed.", denied);
        }
        if (!attributes.isRegularFile()) {
            throw new IOException("A managed material file is not a regular file. No material was removed.");
        }
        Path staged = null;
        try {
            staged = Files.createTempFile(source.getParent(), source.getFileName() + ".", ".removing");
            return Files.move(source, staged, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | SecurityException failure) {
            clean(staged, failure);
            throw new IOException("Cannot prepare the material for removal. Close the PDF viewer and try again.",
                    failure);
        }
    }

    private static boolean restoreStaged(Path staged, Path target, Throwable failure) {
        if (staged == null) {
            return true;
        }
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("A new file occupies the original material path.");
            }
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | SecurityException restoreError) {
            failure.addSuppressed(restoreError);
            return false;
        }
    }

    private static void extract(Path pdf, Path cache) throws IOException {
        try (var document = Loader.loadPDF(pdf.toFile());
             var output = Files.newBufferedWriter(cache, StandardCharsets.UTF_8)) {
            PDFTextStripper extractor = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                extractor.setStartPage(page);
                extractor.setEndPage(page);
                String text = extractor.getText(document).strip();
                for (int offset = 0; offset < text.length();) {
                    int end = Math.min(text.length(), offset + CHUNK);
                    if (end < text.length()) {
                        int boundary = text.lastIndexOf(' ', end);
                        if (boundary > offset + CHUNK / 2) {
                            end = boundary;
                        } else if (Character.isHighSurrogate(text.charAt(end - 1))) {
                            end--;
                        }
                    }
                    String chunk = text.substring(offset, end).strip();
                    if (!chunk.isEmpty()) {
                        output.write(page + "\t" + Base64.getEncoder()
                                .encodeToString(chunk.getBytes(StandardCharsets.UTF_8)));
                        output.newLine();
                    }
                    offset = end;
                }
            }
        } catch (IOException error) {
            throw new IOException("Could not extract PDF text. The file may be damaged or password protected.", error);
        }
    }

    private static void clean(Path path, Throwable failure) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException | SecurityException cleanupError) {
            failure.addSuppressed(cleanupError);
        }
    }

    public record Passage(String materialId, String fileName, int page, String text) { }
    private record ScoredPassage(long score, Passage passage) { }
}
