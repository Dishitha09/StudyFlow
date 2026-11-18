package com.studyflow.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.studyflow.interfaces.Identifiable;
import com.studyflow.utils.ValidationUtils;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/** Local study file metadata. Only eligible PDFs have indexed page text. */
public record StudyMaterial(String id, String title, String fileName, String description,
                            String subjectId, Instant uploadedAt,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String indexWarning) implements Identifiable {
    private static final Set<String> FORMATS = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "md", "csv", "java", "png", "jpg", "jpeg", "gif", "webp");

    public StudyMaterial(String id, String title, String fileName, String description, String subjectId, Instant uploadedAt) {
        this(id, title, fileName, description, subjectId, uploadedAt, null);
    }

    public StudyMaterial {
        id = ValidationUtils.requiredText(id, "Material ID");
        if (!id.matches("[a-zA-Z0-9-]{1,80}")) {
            throw new IllegalArgumentException("Invalid material ID.");
        }
        title = ValidationUtils.requiredText(title, "Material title");
        fileName = ValidationUtils.requiredText(fileName, "File name");
        if (!supportsFileName(fileName)) {
            throw new IllegalArgumentException("Unsupported material file name.");
        }
        description = ValidationUtils.textOrEmpty(description);
        subjectId = ValidationUtils.optionalId(subjectId, "Subject ID");
        ValidationUtils.required(uploadedAt, "Upload time");
        if (indexWarning != null && (!"pdf".equals(extensionOf(fileName)) || indexWarning.isBlank())) {
            throw new IllegalArgumentException("Only PDF files can have a page search warning.");
        }
    }

    public static boolean supportsFileName(String name) {
        if (name == null || name.isBlank()) return false;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch < 32 || "<>:\"/\\|?*".indexOf(ch) >= 0) return false;
        }
        return FORMATS.contains(extensionOf(name));
    }

    private static String extensionOf(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }

    public String extension() { return extensionOf(fileName); }

    @JsonIgnore
    public boolean isPdf() { return "pdf".equals(extension()); }

    public boolean hasPageSearch() { return isPdf() && indexWarning == null; }

    public StudyMaterial withIndexWarning(String reason) {
        return new StudyMaterial(id, title, fileName, description, subjectId, uploadedAt, reason);
    }

    @Override
    public String getId() { return id; }
}
