package com.studyflow.model;

import com.studyflow.utils.ValidationUtils;

import java.time.Duration;

/** Preferences shared by the local StudyFlow profile. */
public final class UserSettings {
    public static final Theme DEFAULT_THEME = Theme.LIGHT;
    public static final Priority DEFAULT_PRIORITY = Priority.MEDIUM;

    private Theme theme;
    private Priority defaultPriority;
    private int defaultAttendanceTarget;
    private boolean notificationsEnabled;
    private Duration focusDuration;
    private Duration breakDuration;
    private boolean googleAutoSyncEnabled;

    public UserSettings() {
        this(DEFAULT_THEME, DEFAULT_PRIORITY, 75, true, Duration.ofMinutes(25), Duration.ofMinutes(5));
    }

    public UserSettings(Theme theme, Priority defaultPriority, int defaultAttendanceTarget,
                        boolean notificationsEnabled, Duration focusDuration, Duration breakDuration) {
        setTheme(theme);
        setDefaultPriority(defaultPriority);
        setDefaultAttendanceTarget(defaultAttendanceTarget);
        setNotificationsEnabled(notificationsEnabled);
        setFocusDuration(focusDuration);
        setBreakDuration(breakDuration);
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = ValidationUtils.required(theme, "Theme");
    }

    public Priority getDefaultPriority() {
        return defaultPriority;
    }

    public void setDefaultPriority(Priority defaultPriority) {
        this.defaultPriority = ValidationUtils.required(defaultPriority, "Default priority");
    }

    public int getDefaultAttendanceTarget() {
        return defaultAttendanceTarget;
    }

    public void setDefaultAttendanceTarget(int defaultAttendanceTarget) {
        if (defaultAttendanceTarget < 0 || defaultAttendanceTarget > 100) {
            throw new IllegalArgumentException("Attendance target must be from 0 to 100 percent.");
        }
        this.defaultAttendanceTarget = defaultAttendanceTarget;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public Duration getFocusDuration() {
        return focusDuration;
    }

    public void setFocusDuration(Duration focusDuration) {
        this.focusDuration = ValidationUtils.positiveDuration(focusDuration, "Focus duration");
    }

    public Duration getBreakDuration() {
        return breakDuration;
    }

    public void setBreakDuration(Duration breakDuration) {
        this.breakDuration = ValidationUtils.positiveDuration(breakDuration, "Break duration");
    }

    public boolean isGoogleAutoSyncEnabled() {
        return googleAutoSyncEnabled;
    }

    public void setGoogleAutoSyncEnabled(boolean enabled) {
        this.googleAutoSyncEnabled = enabled;
    }

    public UserSettings copy() {
        UserSettings copy = new UserSettings(theme, defaultPriority, defaultAttendanceTarget,
                notificationsEnabled, focusDuration, breakDuration);
        copy.setGoogleAutoSyncEnabled(googleAutoSyncEnabled);
        return copy;
    }
}
