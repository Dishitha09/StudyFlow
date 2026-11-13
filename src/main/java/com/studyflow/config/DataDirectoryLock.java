package com.studyflow.config;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Holds exclusive ownership of a data directory until the application closes. */
final class DataDirectoryLock implements AutoCloseable {
    private final FileChannel channel;

    private DataDirectoryLock(FileChannel channel) {
        this.channel = channel;
    }

    static DataDirectoryLock acquire(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path lockFile = directory.resolve(".studyflow.lock");
        FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                throw new IOException("StudyFlow already has this data directory open: " + directory);
            }
            return new DataDirectoryLock(channel);
        } catch (IOException | RuntimeException error) {
            try {
                channel.close();
            } catch (IOException closeError) {
                error.addSuppressed(closeError);
            }
            if (error instanceof OverlappingFileLockException) {
                throw new IOException("StudyFlow already has this data directory open: " + directory, error);
            }
            throw error;
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
