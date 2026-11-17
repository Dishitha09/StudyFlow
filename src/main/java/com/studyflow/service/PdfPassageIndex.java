package com.studyflow.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

/** Stores verifiable, overlapping page excerpts alongside a fingerprint of the managed PDF. */
final class PdfPassageIndex {
    static final int MAX_PDF_BYTES = 100 * 1024 * 1024;
    private static final int MAX_PAGES = 2_000;
    private static final int MAX_PAGE_TEXT = 200_000;
    private static final int MAX_DOCUMENT_TEXT = 4_000_000;
    private static final long MAX_INDEX_BYTES = 32L * 1024 * 1024;
    private static final int CHUNK = 900;
    private static final int OVERLAP = 180;
    private static final String HEADER = "StudyFlow-PDF-Index\t1\t";
    private static final String FOOTER = "end\t";

    private PdfPassageIndex() { }

    static SourceStamp stamp(Path pdf) throws IOException {
        BasicFileAttributes attributes = attributes(pdf);
        if (attributes == null) {
            return null;
        }
        if (!attributes.isRegularFile()) {
            throw new IOException("The managed PDF is not a regular file.");
        }
        checkPdfSize(attributes.size());
        return new SourceStamp(attributes.size() + "\t" + attributes.lastModifiedTime());
    }

    static void checkPdfSize(long bytes) throws IndexLimitException {
        if (bytes <= 0 || bytes > MAX_PDF_BYTES) {
            throw new IndexLimitException("Choose a nonempty PDF up to 100 MB.");
        }
    }

    static void rebuild(Path pdf, Path cache, SourceStamp source, BooleanSupplier cancelled,
                        BiConsumer<Integer, Integer> progress) throws IOException {
        MaterialLibrary.checkCancelled(cancelled);
        requireRegularCache(cache);
        Files.createDirectories(cache.getParent());
        Path pending = Files.createTempFile(cache.getParent(), cache.getFileName() + ".", ".indexing");
        try {
            extract(pdf, pending, source, cancelled, progress);
            MaterialLibrary.checkCancelled(cancelled);
            if (!source.equals(stamp(pdf))) {
                throw new IOException("The PDF changed while its searchable text was being saved. Try again.");
            }
            Files.move(pending, cache, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(pending);
            } catch (IOException | SecurityException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /** Reads the complete index before the caller publishes any ranked excerpts. */
    static boolean read(Path cache, SourceStamp source, BiConsumer<Integer, String> passage) throws IOException {
        BasicFileAttributes attributes = requireRegularCache(cache);
        if (attributes == null || attributes.size() > MAX_INDEX_BYTES) {
            throw damagedIndex();
        }
        try (var reader = Files.newBufferedReader(cache, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || !header.startsWith(HEADER)) {
                throw damagedIndex();
            }
            String lastFields = header.substring(HEADER.length());
            int lastTab = lastFields.lastIndexOf('\t');
            if (lastTab < 1 || (source != null && !source.value().equals(lastFields.substring(0, lastTab)))) {
                throw damagedIndex();
            }
            int pages = Integer.parseInt(lastFields.substring(lastTab + 1));
            if (pages < 1 || pages > MAX_PAGES) {
                throw damagedIndex();
            }

            MessageDigest digest = sha256();
            update(digest, header);
            int previous = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(FOOTER)) {
                    String checksum = HexFormat.of().formatHex(digest.digest());
                    if (!line.equals(FOOTER + checksum) || reader.readLine() != null) {
                        throw damagedIndex();
                    }
                    return previous != 0;
                }
                int tab = line.indexOf('\t');
                if (tab < 1 || line.length() > 4 * CHUNK + 20) {
                    throw damagedIndex();
                }
                int page = Integer.parseInt(line.substring(0, tab));
                byte[] bytes = Base64.getDecoder().decode(line.substring(tab + 1));
                String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                if (page < 1 || page < previous || page > pages || text.isBlank() || text.length() > CHUNK) {
                    throw damagedIndex();
                }
                previous = page;
                update(digest, line);
                passage.accept(page, text);
            }
        } catch (IllegalArgumentException error) {
            throw new IOException("The saved PDF index is damaged.", error);
        }
        throw damagedIndex();
    }

    private static void extract(Path pdf, Path output, SourceStamp source, BooleanSupplier cancelled,
                                BiConsumer<Integer, Integer> progress) throws IOException {
        try (var document = Loader.loadPDF(pdf.toFile());
             var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            int pages = document.getNumberOfPages();
            if (pages < 1 || pages > MAX_PAGES) {
                throw new IndexLimitException("Choose a PDF with 1 to 2,000 pages.");
            }
            progress.accept(0, pages);
            IndexWriter index = new IndexWriter(writer, HEADER + source.value() + "\t" + pages);
            PageText text = new PageText(cancelled);
            var stripper = new PDFTextStripper() {
                @Override
                public void processPage(PDPage page) throws IOException {
                    MaterialLibrary.checkCancelled(cancelled);
                    super.processPage(page);
                    index.addPage(getCurrentPageNo(), text.drain());
                    progress.accept(getCurrentPageNo(), pages);
                }
            };
            stripper.setLineSeparator("\n");
            stripper.setSortByPosition(true);
            stripper.writeText(document, text);
            index.finish();
            progress.accept(pages, pages);
        } catch (InvalidPasswordException error) {
            throw new IOException("This PDF needs a password. Import an unlocked copy.", error);
        } catch (MaterialLibrary.ImportCancelledException | IndexLimitException error) {
            throw error;
        } catch (IOException error) {
            throw new IOException("Could not index the PDF. Check that it is readable and undamaged.", error);
        }
    }

    private static BasicFileAttributes requireRegularCache(Path path) throws IOException {
        BasicFileAttributes attributes = attributes(path);
        if (attributes != null && !attributes.isRegularFile()) {
            throw new IOException("The saved PDF index is not a regular file.");
        }
        return attributes;
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException absent) {
            return null;
        }
    }

    private static IOException damagedIndex() {
        return new IOException("The saved PDF index is missing, out of date, or damaged.");
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static void update(MessageDigest digest, String line) {
        digest.update(line.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }

    private static int boundary(String text, int at) {
        return at > 0 && at < text.length() && Character.isLowSurrogate(text.charAt(at)) ? at - 1 : at;
    }

    record SourceStamp(String value) { }

    private static final class IndexLimitException extends IOException {
        private IndexLimitException(String message) { super(message); }
    }

    private static final class IndexWriter {
        private final BufferedWriter writer;
        private final MessageDigest digest = sha256();
        private int textSize;

        private IndexWriter(BufferedWriter writer, String header) throws IOException {
            this.writer = writer;
            line(header);
        }

        private void addPage(int page, String text) throws IOException {
            textSize += text.length();
            if (textSize > MAX_DOCUMENT_TEXT) {
                throw new IndexLimitException("The PDF contains too much text to index at once. Split it into smaller PDFs.");
            }
            int offset = 0;
            while (offset < text.length()) {
                int end = boundary(text, Math.min(text.length(), offset + CHUNK));
                if (end < text.length()) {
                    end = wordBoundary(text, end, Math.max(offset + CHUNK / 2, end - 52));
                }
                String chunk = text.substring(offset, end).strip();
                if (!chunk.isEmpty()) {
                    line(page + "\t" + Base64.getEncoder().encodeToString(chunk.getBytes(StandardCharsets.UTF_8)));
                }
                if (end == text.length()) {
                    break;
                }
                int next = boundary(text, end - OVERLAP);
                offset = wordBoundary(text, next, Math.max(offset + 1, next - 30));
            }
        }

        private static int wordBoundary(String text, int at, int minimum) {
            for (int index = at; index >= minimum; index--) {
                if (Character.isWhitespace(text.charAt(index - 1))) {
                    return index;
                }
            }
            return at;
        }

        private void line(String line) throws IOException {
            writer.write(line);
            writer.write('\n');
            update(digest, line);
        }

        private void finish() throws IOException {
            writer.write(FOOTER + HexFormat.of().formatHex(digest.digest()));
            writer.write('\n');
        }
    }

    private static final class PageText extends Writer {
        private final BooleanSupplier cancelled;
        private final StringBuilder page = new StringBuilder();

        private PageText(BooleanSupplier cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public void write(char[] text, int offset, int length) throws IOException {
            MaterialLibrary.checkCancelled(cancelled);
            if (length > MAX_PAGE_TEXT - page.length()) {
                throw new IndexLimitException("A page contains too much text to index. Import a smaller PDF.");
            }
            page.append(text, offset, length);
        }

        private String drain() {
            String text = page.toString().strip();
            page.setLength(0);
            return text;
        }

        @Override public void flush() { }
        @Override public void close() { }
    }
}
