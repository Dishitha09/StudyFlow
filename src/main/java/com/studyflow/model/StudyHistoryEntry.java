package com.studyflow.model;

import com.studyflow.utils.ValidationUtils;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A local copy of an assistant request, its output, and the PDF pages cited. */
public record StudyHistoryEntry(Instant createdAt, String topic, String mode, String materialId,
                                String materialTitle, boolean generated, String output, List<Reference> references,
                                String entryId, QuestionSet questions, String difficulty) {
    public StudyHistoryEntry(Instant createdAt, String topic, String mode, String materialId, String materialTitle,
                             boolean generated, String output, List<Reference> references) {
        this(createdAt, topic, mode, materialId, materialTitle, generated, output, references, null, null, null);
    }

    public StudyHistoryEntry {
        ValidationUtils.required(createdAt, "Study history time");
        topic = ValidationUtils.textOrEmpty(topic);
        mode = ValidationUtils.requiredText(mode, "Study mode");
        materialId = ValidationUtils.optionalId(materialId, "Material ID");
        materialTitle = ValidationUtils.textOrEmpty(materialTitle);
        output = ValidationUtils.requiredText(output, "Study output");
        references = List.copyOf(ValidationUtils.required(references, "Study source references"));
        if (entryId == null) {
            String legacyKey = createdAt + "\n" + topic + "\n" + mode + "\n" + materialId + "\n" + output;
            entryId = UUID.nameUUIDFromBytes(legacyKey.getBytes(StandardCharsets.UTF_8)).toString();
        }
        entryId = ValidationUtils.requiredText(entryId, "Study history ID");
        difficulty = ValidationUtils.optionalId(difficulty, "Difficulty");
    }

    public record Reference(String fileName, int page, String materialId, String sourceId) {
        public Reference(String fileName, int page) {
            this(fileName, page, null, null);
        }

        public Reference {
            fileName = ValidationUtils.requiredText(fileName, "Study source file name");
            materialId = ValidationUtils.optionalId(materialId, "Source material");
            sourceId = ValidationUtils.optionalId(sourceId, "Source ID");
            if (page < 1) {
                throw new IllegalArgumentException("Study source page must be positive.");
            }
        }
    }
}
