package com.studyflow.repository;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/** Handles JSON snapshots, timestamped preimages and atomic local replacement. */
public final class FileStorage implements AutoCloseable {
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd'T'HHmmssSSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final Path dataDirectory;
    private final Path backupDirectory;
    private final JsonMapper mapper = JsonConfiguration.createMapper();
    private boolean closed;

    public FileStorage(Path dataDirectory) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "Data directory").toAbsolutePath().normalize();
        this.backupDirectory = this.dataDirectory.resolve("backups");
        createDirectories(this.dataDirectory);
        createDirectories(this.backupDirectory);
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public Path backupDirectory() {
        return backupDirectory;
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    JavaType recordType(Class<?> recordClass) {
        return mapper.constructType(recordClass);
    }

    JavaType listType(Class<?> recordClass) {
        return mapper.getTypeFactory().constructCollectionType(List.class, recordClass);
    }

    synchronized Path prepare(Path relativePath) {
        ensureOpen();
        Path path = dataDirectory.resolve(relativePath).normalize();
        if (relativePath.isAbsolute() || !path.startsWith(dataDirectory) || path.equals(dataDirectory)) {
            throw new IllegalArgumentException("A data file must be relative to the data directory.");
        }
        createDirectories(path.getParent());
        return path;
    }

    synchronized <D, T> T read(Path path, JavaType type, Supplier<T> empty, Function<D, T> decode) {
        ensureOpen();
        byte[] bytes = readIfPresent(path);
        if (bytes == null) {
            return empty.get();
        }
        D document = readDocument(path, bytes, type);
        return validate(path, document, decode);
    }

    synchronized <D> void write(Path path, JavaType type, D candidate, Consumer<D> validateDocument) {
        write(path, type, candidate, validateDocument, true);
    }

    synchronized <D> void write(Path path, JavaType type, D candidate, Consumer<D> validateDocument,
                                boolean backupPrevious) {
        ensureOpen();
        validate(path, candidate, document -> {
            validateDocument.accept(document);
            return document;
        });
        byte[] previousBytes = readIfPresent(path);
        if (previousBytes != null) {
            D previous = readDocument(path, previousBytes, type);
            validate(path, previous, document -> {
                validateDocument.accept(document);
                return document;
            });
            if (previous.equals(candidate)) {
                return;
            }
        }

        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(candidate);
        } catch (IOException | RuntimeException ex) {
            throw new StorageException(path, "Cannot serialize the StudyFlow data", ex);
        }
        replace(path, bytes, backupPrevious ? previousBytes : null, false);
    }

    synchronized void removeSnapshot(Path path) {
        ensureOpen();
        try {
            Files.deleteIfExists(path);
        } catch (IOException | SecurityException ex) {
            throw new StorageException(path, "Cannot remove the StudyFlow data file", ex);
        }
    }

    /** Copies the exact current bytes of a validated snapshot to a new timestamped backup. */
    synchronized <D, T> Path backup(Path path, JavaType type, Function<D, T> decode) {
        ensureOpen();
        byte[] bytes = readIfPresent(path);
        if (bytes == null) {
            throw new StorageException(path, "No saved data file to back up", null);
        }
        D document = readDocument(path, bytes, type);
        validate(path, document, decode);
        return writeBackup(path, bytes, "-manual-");
    }

    /** Decodes and validates the exact backup bytes that will replace the live file. */
    synchronized <D, T> void restore(Path path, Path backup, JavaType type, Function<D, T> decode,
                                    Consumer<T> validateCandidate) {
        ensureOpen();
        Path source = Objects.requireNonNull(backup, "Backup path").toAbsolutePath().normalize();
        Objects.requireNonNull(validateCandidate, "Restore validation");
        byte[] bytes = readIfPresent(source);
        if (bytes == null) {
            throw new StorageException(source, "Backup file does not exist", null);
        }
        try {
            if (source.equals(path) || (Files.exists(path) && Files.isSameFile(source, path))) {
                throw new StorageException(source, "Select a backup other than the live data file", null);
            }
        } catch (IOException | SecurityException ex) {
            throw new StorageException(source, "Cannot identify the selected backup", ex);
        }

        D document = readDocument(source, bytes, type);
        T candidate = validate(source, document, decode);
        validate(source, candidate, restored -> {
            validateCandidate.accept(restored);
            return restored;
        });
        replace(path, bytes, readIfPresent(path), true);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("StudyFlow file storage is closed.");
        }
    }

    private byte[] readIfPresent(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (NoSuchFileException ex) {
            return null;
        } catch (IOException | SecurityException ex) {
            throw new StorageException(path, "Cannot read the StudyFlow data", ex);
        }
    }

    private <D> D readDocument(Path path, byte[] bytes, JavaType type) {
        try {
            D document = mapper.readValue(bytes, type);
            if (document == null) {
                throw new IllegalArgumentException("A snapshot cannot be JSON null.");
            }
            return document;
        } catch (IOException | RuntimeException ex) {
            throw new StorageException(path, "Cannot parse the StudyFlow JSON", ex);
        }
    }

    private static <D, T> T validate(Path path, D document, Function<D, T> decode) {
        try {
            return decode.apply(Objects.requireNonNull(document, "Snapshot"));
        } catch (StorageException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new StorageException(path, "Invalid StudyFlow data", ex);
        }
    }

    private void replace(Path path, byte[] bytes, byte[] previousBytes, boolean restoring) {
        Path pending = stage(path, bytes);
        try {
            if (previousBytes != null) {
                writeBackup(path, previousBytes, restoring ? "-before-restore-" : "-");
            }
            moveIntoPlace(pending, path);
        } finally {
            removeTemporaryFile(pending);
        }
    }

    private Path writeBackup(Path path, byte[] bytes, String marker) {
        String file = path.getFileName().toString();
        String prefix = file.substring(0, file.lastIndexOf('.'));
        Path backup = backupDirectory.resolve(prefix + marker + BACKUP_TIME.format(Instant.now())
                + "-" + UUID.randomUUID() + ".json");
        Path pending = stage(backup, bytes);
        try {
            moveIntoPlace(pending, backup);
        } finally {
            removeTemporaryFile(pending);
        }
        return backup;
    }

    private static Path stage(Path path, byte[] bytes) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(path.getParent(), path.getFileName() + ".", ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            return temporary;
        } catch (IOException | SecurityException ex) {
            removeTemporaryFile(temporary);
            throw new StorageException(path, "Cannot stage the StudyFlow data for writing", ex);
        }
    }

    private static void moveIntoPlace(Path source, Path target) {
        try {
            Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            throw new StorageException(target, "This filesystem cannot safely replace the data atomically", ex);
        } catch (IOException | SecurityException ex) {
            throw new StorageException(target, "Cannot replace the StudyFlow data", ex);
        }
    }

    private static void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException | SecurityException ex) {
            throw new StorageException(path, "Cannot create the StudyFlow data directory", ex);
        }
    }

    private static void removeTemporaryFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException | SecurityException ignored) {
            // A failed cleanup must not conceal the original write failure.
        }
    }
}
