package com.studyflow.repository;

import com.studyflow.model.FocusRecord;
import java.nio.file.Path;

/** Stores focus history independently of the current task list. */
public final class FocusRepository extends JsonCollectionRepository<FocusRecord, FocusRecord> {
    public FocusRepository(FileStorage storage) {
        super(storage, Path.of("focus", "sessions.json"), FocusRecord.class, record -> record, record -> record);
    }
}
