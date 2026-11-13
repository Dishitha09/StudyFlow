package com.studyflow.config;

import java.nio.file.Path;
import java.util.Objects;

/** Launch configuration and defaults for a local StudyFlow installation. */
public record AppConfig(String name, String version, Path dataDirectory, boolean darkByDefault) {
    public static final String APPLICATION_NAME = "StudyFlow";
    public static final String VERSION = "0.1.0";
    public static final String DEFAULT_PRIORITY = "MEDIUM";
    public static final String DEFAULT_STATUS = "TODO";

    public AppConfig {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
    }

    /** Accepts an optional --data-dir followed by a path. */
    public static AppConfig fromArgs(String[] args) {
        Path directory = Path.of("data");
        if (args.length == 2 && "--data-dir".equals(args[0]) && !args[1].isBlank()) {
            directory = Path.of(args[1]);
        } else if (args.length != 0) {
            throw new IllegalArgumentException("Usage: StudyFlow [--data-dir <directory>]");
        }
        return new AppConfig(APPLICATION_NAME, VERSION, directory, false);
    }
}
