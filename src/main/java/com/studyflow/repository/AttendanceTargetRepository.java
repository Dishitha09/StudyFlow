package com.studyflow.repository;

import com.studyflow.model.AttendanceTarget;
import java.nio.file.Path;

public final class AttendanceTargetRepository
        extends JsonCollectionRepository<AttendanceTarget, AttendanceTargetRepository.SavedTarget> {
    public AttendanceTargetRepository(FileStorage storage) {
        super(storage, Path.of("attendance", "targets.json"), SavedTarget.class,
                target -> new SavedTarget(target.subjectId(), target.target()),
                target -> new AttendanceTarget(target.subjectId(), target.target()));
    }

    record SavedTarget(String subjectId, int target) { }
}
