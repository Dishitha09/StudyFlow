package com.studyflow.repository;

import com.studyflow.model.Subject;

import java.nio.file.Path;
import java.util.List;

/** Persists subjects and their searchable tags. */
public final class SubjectRepository extends JsonCollectionRepository<Subject, SubjectRepository.SubjectRecord> {
    public SubjectRepository(FileStorage storage) {
        super(storage, Path.of("subjects", "subjects.json"), SubjectRecord.class,
                SubjectRecord::from, SubjectRecord::toDomain);
    }

    record SubjectRecord(String id, String name, String subjectCode, String description, List<String> tags) {
        static SubjectRecord from(Subject subject) {
            return new SubjectRecord(subject.getId(), subject.getName(), subject.getSubjectCode(),
                    subject.getDescription(), subject.getTags());
        }

        Subject toDomain() {
            return new Subject(id, name, subjectCode, description, tags);
        }
    }
}
