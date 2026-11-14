package com.studyflow.repository;

import com.studyflow.model.AttendanceEntry;
import java.nio.file.Path;

public final class AttendanceRepository extends JsonCollectionRepository<AttendanceEntry, AttendanceEntry> {
    public AttendanceRepository(FileStorage storage) {
        super(storage, Path.of("attendance", "classes.json"), AttendanceEntry.class, entry -> entry, entry -> entry);
    }
}
