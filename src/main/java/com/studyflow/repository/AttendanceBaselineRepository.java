package com.studyflow.repository;

import com.studyflow.model.AttendanceBaseline;
import com.studyflow.utils.ValidationUtils;
import java.nio.file.Path;

public final class AttendanceBaselineRepository
        extends JsonCollectionRepository<AttendanceBaseline, AttendanceBaselineRepository.SavedBaseline> {
    public AttendanceBaselineRepository(FileStorage storage) {
        super(storage, Path.of("attendance", "baselines.json"), SavedBaseline.class,
                entry -> new SavedBaseline(entry.subjectId(), entry.attended(), entry.total()),
                entry -> new AttendanceBaseline(entry.subjectId(),
                        ValidationUtils.required(entry.attended(), "Classes attended"),
                        ValidationUtils.required(entry.total(), "Classes conducted")));
    }

    record SavedBaseline(String subjectId, Integer attended, Integer total) { }
}
