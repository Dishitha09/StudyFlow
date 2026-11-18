package com.studyflow.service;

import com.studyflow.interfaces.Repository;
import com.studyflow.model.StudyMaterial;
import com.studyflow.utils.IdGenerator;
import com.studyflow.utils.SearchText;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Stores managed study files and searches eligible PDFs by cached, page-numbered passages. */
public final class MaterialLibrary {
    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    private static final int MAX_FILE_BYTES = 160 * 1024 * 1024;

    private final Path files;
    private final Path index;
    private final Repository<StudyMaterial> materials;
    private final Predicate<String> subjectExists;
    private final Clock clock;

    public MaterialLibrary(Path directory, Repository<StudyMaterial> materials, Predicate<String> subjectExists,
                           Clock clock) {
        Path managed = directory.toAbsolutePath().normalize();
        files = managed.resolve("files");
        index = managed.resolve("index");
        this.materials = materials;
        this.subjectExists = subjectExists;
        this.clock = clock;
    }

    public StudyMaterial upload(Path source, String title, String description, String subjectId) throws IOException {
        return upload(source, title, description, subjectId, () -> false, progress -> { });
    }

    /** Checks cancellation during copying and extraction, and once more before storing the new record. */
    public StudyMaterial upload(Path source, String title, String description, String subjectId,
                                BooleanSupplier cancelled, Consumer<ImportProgress> progress) throws IOException {
        Objects.requireNonNull(cancelled, "Cancellation check");
        Objects.requireNonNull(progress, "Import progress listener");
        checkCancelled(cancelled);
        if (source == null || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                || !StudyMaterial.supportsFileName(source.getFileName().toString())) {
            throw new IllegalArgumentException("Choose a supported study file.");
        }
        long bytes = Files.size(source);
        checkSize(bytes);
        // Check the format before copying a PDF too large for the text indexing path.
        if (bytes > PdfPassageIndex.MAX_PDF_BYTES
                && source.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            requirePdfHeader(source);
        }
        StudyMaterial material = new StudyMaterial(IdGenerator.next(), title, source.getFileName().toString(),
                description, subjectId, clock.instant());
        requireExistingSubject(material);
        requireSafeDirectories();
        Files.createDirectories(files);
        Files.createDirectories(index);
        requireSafeDirectories();
        Path file = filePath(material);
        Path cache = cachePath(material.id());
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) || Files.exists(cache, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Could not reserve a new material file. Try importing again.");
        }
        Path pending = Files.createTempFile(files, material.id() + ".", ".importing");
        boolean cached = false;
        boolean moved = false;
        try {
            copyFile(source, pending, (int) bytes, cancelled, progress);
            if (material.isPdf()) {
                try {
                    PdfPassageIndex.rebuild(pending, cache, PdfPassageIndex.stamp(pending), cancelled,
                            (completed, total) -> progress.accept(new ImportProgress(ImportStage.EXTRACTING, completed, total)));
                    cached = true;
                } catch (PdfPassageIndex.IndexLimitException limit) {
                    requirePdfHeader(pending);
                    material = material.withIndexWarning("Saved without PDF page search. " + limit.getMessage());
                }
            }
            progress.accept(new ImportProgress(ImportStage.SAVING, 0, 0));
            checkCancelled(cancelled);
            requireExistingSubject(material);
            Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE);
            moved = true;
            materials.insert(material);
            return material;
        } catch (IOException | RuntimeException failure) {
            if (cached) {
                clean(cache, failure);
            }
            clean(moved ? file : pending, failure);
            throw failure;
        }
    }

    private void requireExistingSubject(StudyMaterial material) {
        if (material.subjectId() != null && !subjectExists.test(material.subjectId())) {
            throw new IllegalArgumentException("Choose an existing subject.");
        }
    }

    private static void checkSize(long bytes) throws IOException {
        if (bytes <= 0 || bytes > MAX_FILE_BYTES) {
            throw new IOException("Choose a nonempty file up to 160 MB.");
        }
    }

    private static void requirePdfHeader(Path source) throws IOException {
        try (var stream = Files.newInputStream(source)) {
            String prefix = new String(stream.readNBytes(1024), java.nio.charset.StandardCharsets.ISO_8859_1);
            if (!prefix.contains("%PDF-")) {
                throw new IOException("This file has no PDF header. Files over 100 MB cannot be indexed.");
            }
        }
    }

    private static void copyFile(Path source, Path pending, int expected, BooleanSupplier cancelled,
                                Consumer<ImportProgress> progress) throws IOException {
        progress.accept(new ImportProgress(ImportStage.COPYING, 0, expected));
        checkCancelled(cancelled);
        int copied = 0;
        int lastReported = 0;
        try (var input = Files.newInputStream(source); var output = Files.newOutputStream(pending)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                checkCancelled(cancelled);
                copied += count;
                checkSize(copied);
                if (copied > expected) {
                    throw new IOException("The selected file changed while it was being copied. Try again.");
                }
                output.write(buffer, 0, count);
                if (copied == expected || copied - lastReported >= Math.max(COPY_BUFFER_SIZE, expected / 100)) {
                    progress.accept(new ImportProgress(ImportStage.COPYING, copied, expected));
                    lastReported = copied;
                }
            }
        }
        if (copied != expected) {
            throw new IOException("The selected file changed while it was being copied. Try again.");
        }
    }

    static void checkCancelled(BooleanSupplier cancelled) throws ImportCancelledException {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) {
            throw new ImportCancelledException();
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

    public List<Passage> retrieve(String query, String materialId) throws IOException {
        return searchPassages(query, materialId).passages();
    }

    public synchronized RetrievalResult searchPassages(String query, String materialId) {
        List<StudyMaterial> candidates = materialId == null
                ? findAll().stream().filter(StudyMaterial::hasPageSearch).toList() : List.of(require(materialId));
        PassageRanker best = new PassageRanker(query);
        List<RetrievalWarning> warnings = new ArrayList<>();
        for (StudyMaterial item : candidates) {
            if (!item.hasPageSearch()) {
                warnings.add(warning(item, item.indexWarning() == null
                        ? "This attachment does not have PDF page text." : item.indexWarning()));
                continue;
            }
            try {
                LoadedIndex result = loadIndex(item, query);
                best.merge(result.matches());
                if (result.originalMissing()) {
                    warnings.add(warning(item, "The managed PDF is missing. Saved excerpts can still be read."));
                }
                if (!result.hasText()) {
                    warnings.add(warning(item, "No searchable text was found. If this is a scan, import a copy with OCR text."));
                }
            } catch (IOException | SecurityException failure) {
                warnings.add(warning(item, "Searchable text is unavailable. Check that the managed PDF is present and readable."));
            }
        }
        return new RetrievalResult(best.matches().stream().map(PassageRanker.Match::passage).toList(), warnings);
    }

    /** Presents only an excerpt from the source text, centered on a matched keyword when possible. */
    public static String preview(Passage passage, String query) {
        return new PassageRanker(query).preview(passage.text());
    }

    private LoadedIndex loadIndex(StudyMaterial item, String query) throws IOException {
        requireSafeDirectories();
        Path pdf = filePath(item);
        Path cache = cachePath(item.id());
        PdfPassageIndex.SourceStamp stamp = PdfPassageIndex.stamp(pdf);
        try {
            return readIndex(item, query, cache, stamp);
        } catch (IOException failure) {
            if (stamp == null) {
                throw failure;
            }
            PdfPassageIndex.rebuild(pdf, cache, stamp, () -> false, (completed, total) -> { });
            return readIndex(item, query, cache, stamp);
        }
    }

    private static LoadedIndex readIndex(StudyMaterial item, String query, Path cache,
                                         PdfPassageIndex.SourceStamp stamp) throws IOException {
        PassageRanker ranker = new PassageRanker(query);
        boolean hasText = PdfPassageIndex.read(cache, stamp,
                (page, text) -> ranker.accept(new Passage(item.id(), item.fileName(), page, text)));
        return new LoadedIndex(ranker, hasText, stamp == null);
    }

    private static RetrievalWarning warning(StudyMaterial item, String message) {
        return new RetrievalWarning(item.id(), item.fileName(), message);
    }

    public synchronized Path openPath(String id) throws IOException {
        requireSafeDirectories();
        Path path = filePath(require(id));
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The stored file is missing or is not a regular managed file.");
        }
        return path;
    }

    public synchronized void delete(String id) throws IOException {
        requireSafeDirectories();
        StudyMaterial material = require(id);
        Path file = filePath(material);
        Path cache = cachePath(material.id());
        Path stagedPdf = null;
        Path stagedCache = null;
        try {
            stagedPdf = stageRemoval(file);
            stagedCache = stageRemoval(cache);
            if (!materials.delete(material.id())) {
                throw new IOException("The study material changed. Reload the library and try again.");
            }
        } catch (IOException | RuntimeException failure) {
            boolean cacheRestored = restoreStaged(stagedCache, cache, failure);
            boolean pdfRestored = restoreStaged(stagedPdf, file, failure);
            if (!cacheRestored || !pdfRestored) {
                throw new IOException("Removal failed and a staged file could not be restored. "
                        + "The staged copy was kept in the StudyFlow materials folder.", failure);
            }
            throw failure;
        }

        IOException cleanup = new IOException("The study material was removed, but temporary files could not be "
                + "deleted. Close the file viewer and check the StudyFlow materials folder.");
        clean(stagedCache, cleanup);
        clean(stagedPdf, cleanup);
        if (cleanup.getSuppressed().length != 0) {
            throw cleanup;
        }
    }

    private StudyMaterial require(String id) {
        return materials.findById(id).orElseThrow(() -> new NoSuchElementException("Study material not found."));
    }

    private void requireSafeDirectories() throws IOException {
        Path managed = files.getParent();
        for (Path folder : List.of(managed, files, index)) {
            if (Files.exists(folder, LinkOption.NOFOLLOW_LINKS)
                    && (folder.getParent() == null || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)
                    || !folder.toRealPath().equals(folder.getParent().toRealPath().resolve(folder.getFileName())))) {
                throw new IOException("StudyFlow won't read or change files through a redirected library folder.");
            }
        }
    }

    private Path filePath(StudyMaterial material) {
        return material.isPdf() ? files.resolve(material.id() + ".pdf")
                : files.resolve(material.id() + "--" + material.fileName());
    }
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

    public enum ImportStage { COPYING, EXTRACTING, SAVING }

    /** Copy progress reports bytes. Extraction reports pages. Saving has no determinate length. */
    public record ImportProgress(ImportStage stage, int completed, int total) { }

    public static final class ImportCancelledException extends IOException {
        private ImportCancelledException() { super("Material import cancelled."); }
    }

    public record RetrievalWarning(String materialId, String fileName, String message) { }

    public record RetrievalResult(List<Passage> passages, List<RetrievalWarning> warnings) {
        public RetrievalResult {
            passages = List.copyOf(passages);
            warnings = List.copyOf(warnings);
        }
    }

    private record LoadedIndex(PassageRanker matches, boolean hasText, boolean originalMissing) { }
}
