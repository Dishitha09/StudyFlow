package com.studyflow.model;

import com.studyflow.interfaces.Identifiable;
import com.studyflow.interfaces.Searchable;
import com.studyflow.utils.SearchText;
import com.studyflow.utils.ValidationUtils;

import java.util.List;
import java.util.stream.Stream;

/** A course that can be referenced by tasks, notes and workspaces. */
public final class Subject implements Identifiable, Searchable {
    private final String id;
    private String name;
    private String subjectCode;
    private String description;
    private List<String> tags;

    public Subject(String id, String name, String subjectCode, String description, List<String> tags) {
        this.id = ValidationUtils.requiredText(id, "Subject ID");
        setName(name);
        setSubjectCode(subjectCode);
        setDescription(description);
        setTags(tags);
    }

    @Override
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = ValidationUtils.requiredText(name, "Subject name");
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = ValidationUtils.requiredText(subjectCode, "Subject code");
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = ValidationUtils.textOrEmpty(description);
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    public void setTags(List<String> tags) {
        this.tags = ValidationUtils.tags(tags);
    }

    public boolean addTag(String tag) {
        String value = ValidationUtils.requiredText(tag, "Tag");
        return !tags.contains(value) && tags.add(value);
    }

    public boolean removeTag(String tag) {
        return tags.remove(ValidationUtils.requiredText(tag, "Tag"));
    }

    @Override
    public boolean matchesSearch(String query) {
        String[] fields = Stream.concat(Stream.of(name, subjectCode, description), tags.stream())
                .toArray(String[]::new);
        return SearchText.matches(query, fields);
    }

    public Subject copy() {
        return new Subject(id, name, subjectCode, description, tags);
    }
}
