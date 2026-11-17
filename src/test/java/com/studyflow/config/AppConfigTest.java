package com.studyflow.config;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {
    @Test
    void defaultsToLightThemeAndProjectDataDirectory() {
        AppConfig config = AppConfig.fromArgs(new String[0]);
        assertEquals("StudyFlow", config.name());
        assertEquals(Path.of("data").toAbsolutePath().normalize(), config.dataDirectory());
        assertFalse(config.darkByDefault());
    }

    @Test
    void acceptsAnAlternateDataDirectory() {
        Path directory = Path.of("another folder").toAbsolutePath();
        AppConfig config = AppConfig.fromArgs(new String[]{"--data-dir", directory.toString()});
        assertEquals(directory, config.dataDirectory());
    }

    @Test
    void bundledAppKeepsUserDataOutsideTheRuntimeImage() {
        Path profile = Path.of("fixture-user");
        Path localAppData = profile.resolve("AppData").resolve("Local");
        assertEquals(localAppData.resolve("StudyFlow").resolve("data"),
                AppConfig.packagedProfile(localAppData.toString(), profile));
        assertEquals(profile.resolve(".studyflow").resolve("data"), AppConfig.packagedProfile("", profile));
    }

    @Test
    void reportsIncorrectArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> AppConfig.fromArgs(new String[]{"--data-dir"}));
    }
}
