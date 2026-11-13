package com.studyflow.model;

import com.studyflow.interfaces.Identifiable;
import com.studyflow.interfaces.Searchable;
import com.studyflow.utils.SearchText;
import com.studyflow.utils.ValidationUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

/** A plain text note with optional subject membership and editable tags. */
public final class Note implements Identifiable, Searchable {
    private final String id;
    private String title;
    private String content;
    private String subjectId;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> tags;

    public Note(String id, String title, String content, String subjectId, LocalDateTime createdAt,
                LocalDateTime updatedAt, List<String> tags) {
        this.id = ValidationUtils.requiredText(id, "Note ID");
        this.createdAt = ValidationUtils.required(createdAt, "Note creation time");
        update(title, content, subjectId, tags, updatedAt);
    }

    @Override
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    /** Updates note fields together. The caller supplies the timestamp so edits are reproducible in tests. */
    public void update(String title, String content, String subjectId, List<String> tags, LocalDateTime updatedAt) {
        String checkedTitle = ValidationUtils.requiredText(title, "Note title");
        String checkedSubject = ValidationUtils.optionalId(subjectId, "Subject ID");
        List<String> checkedTags = ValidationUtils.tags(tags);
        checkUpdateTime(updatedAt);

        this.title = checkedTitle;
        this.content = ValidationUtils.textOrEmpty(content);
        this.subjectId = checkedSubject;
        this.tags = checkedTags;
        this.updatedAt = updatedAt;
    }

    public void updateContent(String content, LocalDateTime updatedAt) {
        checkUpdateTime(updatedAt);
        this.content = ValidationUtils.textOrEmpty(content);
        this.updatedAt = updatedAt;
    }

    public boolean addTag(String tag, LocalDateTime updatedAt) {
        String value = ValidationUtils.requiredText(tag, "Tag");
        checkUpdateTime(updatedAt);
        if (tags.contains(value)) {
            return false;
        }
        tags.add(value);
        this.updatedAt = updatedAt;
        return true;
    }

    private void checkUpdateTime(LocalDateTime value) {
        ValidationUtils.required(value, "Note update time");
        LocalDateTime earliest = updatedAt == null ? createdAt : updatedAt;
        if (value.isBefore(earliest)) {
            throw new IllegalArgumentException("Note update time cannot precede the last saved time.");
        }
    }

    @Override
    public boolean matchesSearch(String query) {
        String[] fields = Stream.concat(Stream.of(title, content), tags.stream()).toArray(String[]::new);
        return SearchText.matches(query, fields);
    }

    public Note copy() {
        return new Note(id, title, content, subjectId, createdAt, updatedAt, tags);
    }
}
