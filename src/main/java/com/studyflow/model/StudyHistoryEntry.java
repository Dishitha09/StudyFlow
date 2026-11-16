package com.studyflow.model;

import com.studyflow.utils.ValidationUtils;
import java.time.Instant;
import java.util.List;

/** A local copy of an assistant request, its output, and the PDF pages cited. */
public record StudyHistoryEntry(Instant createdAt, String topic, String mode, String materialId,
                                String materialTitle, boolean generated, String output, List<Reference> references) {
    public StudyHistoryEntry {
        ValidationUtils.required(createdAt, "Study history time");
        topic = ValidationUtils.textOrEmpty(topic);
        mode = ValidationUtils.requiredText(mode, "Study mode");
        materialId = ValidationUtils.optionalId(materialId, "Material ID");
        materialTitle = ValidationUtils.textOrEmpty(materialTitle);
        output = ValidationUtils.requiredText(output, "Study output");
        references = List.copyOf(ValidationUtils.required(references, "Study source references"));
    }

    public record Reference(String fileName, int page) {
        public Reference {
            fileName = ValidationUtils.requiredText(fileName, "Study source file name");
            if (page < 1) {
                throw new IllegalArgumentException("Study source page must be positive.");
            }
        }
    }
}
