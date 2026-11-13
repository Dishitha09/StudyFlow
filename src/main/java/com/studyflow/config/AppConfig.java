package com.studyflow.config;

import com.studyflow.model.Priority;
import com.studyflow.model.TaskStatus;
import com.studyflow.model.Theme;
import com.studyflow.model.UserSettings;
import java.nio.file.Path;
import java.util.Objects;

/** Launch configuration and defaults for a local StudyFlow installation. */
public record AppConfig(String name, String version, Path dataDirectory, Theme defaultTheme) {
    public static final String APPLICATION_NAME = "StudyFlow";
    public static final String VERSION = "0.1.0";
    public static final Priority DEFAULT_PRIORITY = UserSettings.DEFAULT_PRIORITY;
    public static final TaskStatus DEFAULT_STATUS = TaskStatus.TODO;

    public AppConfig {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(defaultTheme, "defaultTheme");
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
        return new AppConfig(APPLICATION_NAME, VERSION, directory, UserSettings.DEFAULT_THEME);
    }

    public boolean darkByDefault() {
        return defaultTheme == Theme.DARK;
    }
}
