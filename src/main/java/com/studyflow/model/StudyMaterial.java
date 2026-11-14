package com.studyflow.model;

import com.studyflow.interfaces.Identifiable;
import com.studyflow.utils.ValidationUtils;
import java.time.Instant;

/** Metadata for a managed PDF. */
public record StudyMaterial(String id, String title, String fileName, String description,
                            String subjectId, Instant uploadedAt) implements Identifiable {
    public StudyMaterial {
        id = ValidationUtils.requiredText(id, "Material ID");
        if (!id.matches("[a-zA-Z0-9-]{1,80}")) {
            throw new IllegalArgumentException("Invalid material ID.");
        }
        title = ValidationUtils.requiredText(title, "Material title");
        fileName = ValidationUtils.requiredText(fileName, "PDF file name");
        description = ValidationUtils.textOrEmpty(description);
        subjectId = ValidationUtils.optionalId(subjectId, "Subject ID");
        ValidationUtils.required(uploadedAt, "Upload time");
    }

    @Override
    public String getId() { return id; }
}
