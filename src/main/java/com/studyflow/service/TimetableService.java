package com.studyflow.service;

import com.studyflow.integrations.TimetableImport;
import com.studyflow.model.TimetableEntry;
import com.studyflow.repository.TimetableRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/** Imports a timetable once and keeps later edits in the local StudyFlow profile. */
public final class TimetableService {
    private final TimetableRepository repository;
    private final ZoneId zone;

    public TimetableService(TimetableRepository repository, ZoneId zone) {
        this.repository = repository;
        this.zone = zone;
    }

    public ImportResult importFile(Path path, LocalDate today) throws IOException {
        List<TimetableEntry> entries = TimetableImport.read(path, zone).stream()
                .map(event -> new TimetableEntry(event.id(), event.title(), event.description(), event.location(),
                        event.start(), event.end(), event.allDay()))
                .toList();
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("The calendar file contains no importable events.");
        }
        int added = repository.addMissing(entries);
        // Use saved dates so re-importing a file still follows the user's own edits.
        LocalDate date = preferredDate(today);
        return new ImportResult(added, entries.size() - added, date);
    }

    public LocalDate preferredDate(LocalDate today) {
        List<TimetableEntry> entries = repository.findAll();
        if (entries.stream().anyMatch(entry -> !entry.start().toLocalDate().isAfter(today)
                && entry.end().isAfter(today.atStartOfDay()))) return today;
        return entries.stream().map(entry -> entry.start().toLocalDate()).filter(date -> !date.isBefore(today)).min(Comparator.naturalOrder())
                .orElseGet(() -> entries.stream().map(entry -> entry.start().toLocalDate()).max(Comparator.naturalOrder())
                        .orElse(today));
    }

    public List<TimetableEntry> findAll() { return repository.findAll(); }

    public void update(TimetableEntry edited) { repository.update(edited); }

    public void delete(String id) { repository.delete(id); }

    public record ImportResult(int added, int alreadyHandled, LocalDate showDate) {
        public String message() {
            String text = added == 1 ? "Imported 1 timetable entry." : "Imported " + added + " timetable entries.";
            return alreadyHandled == 0 ? text : text + " Kept previous edits and removals.";
        }
    }
}
