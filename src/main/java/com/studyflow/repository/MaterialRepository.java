package com.studyflow.repository;

import com.studyflow.model.StudyMaterial;
import java.nio.file.Path;

public final class MaterialRepository extends JsonCollectionRepository<StudyMaterial, StudyMaterial> {
    public MaterialRepository(FileStorage storage) {
        super(storage, Path.of("materials", "materials.json"), StudyMaterial.class, material -> material,
                material -> material);
    }
}
