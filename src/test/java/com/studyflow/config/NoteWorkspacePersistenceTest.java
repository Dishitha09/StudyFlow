package com.studyflow.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteWorkspacePersistenceTest {
    @TempDir
    Path directory;

    @Test
    void savesNoteAndWorkspaceEditsToTheActiveProfileBeforeShutdown() throws IOException {
        String noteId;
        String workspaceId;
        String memberId;
        try (StudyFlowContext context = StudyFlowContext.open(directory)) {
            var originalSubject = context.subjectManager().create("Acoustics", "PHY201", "", List.of());
            var nextSubject = context.subjectManager().create("Geometry", "MAT301", "", List.of());
            memberId = nextSubject.getId();
            noteId = context.noteManager().create("Lecture notes", "A rough draft", originalSubject.getId(),
                    List.of("draft")).getId();
            workspaceId = context.workspaceManager().create("Autumn term", "",
                    List.of(originalSubject.getId())).getId();

            context.noteManager().update(noteId, "Geometry notes", "The triangle proof uses similar angles.",
                    memberId, List.of("review"));
            context.workspaceManager().update(workspaceId, "Proof workshop", "Preparation for the next tutorial.",
                    List.of(memberId));

            assertTrue(Files.readString(directory.resolve("notes/notes.json")).contains("similar angles"));
            assertTrue(Files.readString(directory.resolve("workspaces/workspaces.json")).contains("Proof workshop"));
        }

        try (StudyFlowContext reopened = StudyFlowContext.open(directory)) {
            var note = reopened.noteManager().findById(noteId).orElseThrow();
            var workspace = reopened.workspaceManager().findById(workspaceId).orElseThrow();
            assertEquals("Geometry notes", note.getTitle());
            assertEquals("The triangle proof uses similar angles.", note.getContent());
            assertEquals(memberId, note.getSubjectId());
            assertEquals(List.of("review"), note.getTags());
            assertEquals("Proof workshop", workspace.getName());
            assertEquals("Preparation for the next tutorial.", workspace.getDescription());
            assertEquals(List.of(memberId), workspace.getSubjectIds());
            assertEquals("Geometry", reopened.workspaceManager().findContents(workspaceId).getFirst().getName());
        }
    }
}
