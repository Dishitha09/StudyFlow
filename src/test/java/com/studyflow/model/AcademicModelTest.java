package com.studyflow.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AcademicModelTest {
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 13, 9, 0);

    @Test
    void subjectsEncapsulateTagsAndSupportMetadataEdits() {
        List<String> tags = new ArrayList<>(List.of("science"));
        Subject subject = new Subject("s", "Physics", "PHY", "Mechanics", tags);
        tags.clear();

        assertEquals(List.of("science"), subject.getTags());
        subject.setName("Applied Physics");
        subject.setSubjectCode("PHY101");
        subject.setDescription("Study motion");
        assertTrue(subject.addTag("lab"));
        assertFalse(subject.addTag("lab"));
        assertTrue(subject.removeTag("science"));
        assertEquals("Applied Physics", subject.getName());
        assertEquals("PHY101", subject.getSubjectCode());
        assertTrue(subject.matchesSearch("motion"));
        assertEquals(List.of("lab"), subject.getTags());
    }

    @Test
    void noteEditsKeepCreationTimeAndUpdateContentAndTags() {
        Note note = new Note("n", "Original", null, null, CREATED, CREATED, List.of());
        assertEquals("", note.getContent());
        note.update("Lecture", "Equations", "s", List.of("class"), CREATED.plusMinutes(1));
        assertTrue(note.addTag("revision", CREATED.plusMinutes(2)));
        note.updateContent("Conservation laws", CREATED.plusMinutes(3));

        assertEquals("Lecture", note.getTitle());
        assertEquals("s", note.getSubjectId());
        assertEquals(CREATED, note.getCreatedAt());
        assertEquals(CREATED.plusMinutes(3), note.getUpdatedAt());
        assertEquals(List.of("class", "revision"), note.getTags());
        assertTrue(note.matchesSearch("conservation"));
    }

    @Test
    void workspaceMaintainsOrderedUniqueMembership() {
        Workspace workspace = new Workspace("w", "Semester", "", List.of("physics"), CREATED);
        assertTrue(workspace.addSubject("math"));
        assertFalse(workspace.addSubject("physics"));
        assertEquals(List.of("physics", "math"), workspace.getSubjectIds());
        assertTrue(workspace.removeSubject("physics"));
        assertFalse(workspace.removeSubject("missing"));
        assertEquals(List.of("math"), workspace.getSubjectIds());
    }

    @Test
    void checksAcademicRecordAndSettingsInvariants() {
        assertThrows(IllegalArgumentException.class, () -> new Subject("s", "Physics", "", "", List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new Note("n", "Lecture", "", null, CREATED, CREATED.minusSeconds(1), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new Workspace("w", "", "", List.of(), CREATED));

        UserSettings settings = new UserSettings();
        assertEquals(Theme.LIGHT, settings.getTheme());
        assertEquals(Priority.MEDIUM, settings.getDefaultPriority());
        assertEquals(75, settings.getDefaultAttendanceTarget());
        assertTrue(settings.isNotificationsEnabled());
        assertEquals(Duration.ofMinutes(25), settings.getFocusDuration());
        assertEquals(Duration.ofMinutes(5), settings.getBreakDuration());
        assertThrows(IllegalArgumentException.class, () -> settings.setDefaultAttendanceTarget(101));
        assertThrows(IllegalArgumentException.class, () -> settings.setFocusDuration(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> settings.setTheme(null));
    }
}
