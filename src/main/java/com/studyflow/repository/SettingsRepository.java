package com.studyflow.repository;

import com.fasterxml.jackson.databind.JavaType;
import com.studyflow.interfaces.Storage;
import com.studyflow.model.Priority;
import com.studyflow.model.Theme;
import com.studyflow.model.UserSettings;
import com.studyflow.utils.ValidationUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/** Persists the local profile's single settings record. */
public final class SettingsRepository implements Storage<UserSettings> {
    private final FileStorage storage;
    private final Path path;
    private final JavaType type;

    public SettingsRepository(FileStorage storage) {
        this.storage = Objects.requireNonNull(storage, "File storage");
        this.path = storage.prepare(Path.of("settings", "settings.json"));
        this.type = storage.recordType(SettingsRecord.class);
    }

    @Override
    public UserSettings load() {
        return storage.read(path, type, UserSettings::new, SettingsRecord::toDomain);
    }

    @Override
    public void save(UserSettings settings) {
        ValidationUtils.required(settings, "Settings");
        storage.write(path, type, SettingsRecord.from(settings), SettingsRecord::toDomain);
    }

    /** Creates a timestamped backup of the current validated settings. */
    public Path backup() {
        return storage.backup(path, type, SettingsRecord::toDomain);
    }

    public void restore(Path backup, Consumer<UserSettings> validateCandidate) {
        storage.restore(path, backup, type, SettingsRecord::toDomain, validateCandidate);
    }

    record SettingsRecord(Theme theme, Priority defaultPriority, Integer defaultAttendanceTarget,
                          Boolean notificationsEnabled, Duration focusDuration, Duration breakDuration) {
        static SettingsRecord from(UserSettings settings) {
            return new SettingsRecord(settings.getTheme(), settings.getDefaultPriority(),
                    settings.getDefaultAttendanceTarget(), settings.isNotificationsEnabled(),
                    settings.getFocusDuration(), settings.getBreakDuration());
        }

        UserSettings toDomain() {
            return new UserSettings(theme, defaultPriority,
                    ValidationUtils.required(defaultAttendanceTarget, "Attendance target"),
                    ValidationUtils.required(notificationsEnabled, "Notification preference"), focusDuration, breakDuration);
        }
    }
}
