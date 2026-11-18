package com.studyflow.config;

import com.studyflow.interfaces.Storage;
import com.studyflow.model.AttendanceEntry;
import com.studyflow.model.AttendanceTarget;
import com.studyflow.model.Note;
import com.studyflow.model.StudyMaterial;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.UserSettings;
import com.studyflow.model.Workspace;
import com.studyflow.repository.FileStorage;
import com.studyflow.repository.AttendanceRepository;
import com.studyflow.repository.AttendanceTargetRepository;
import com.studyflow.repository.FocusRepository;
import com.studyflow.repository.MaterialRepository;
import com.studyflow.repository.IntegrationRepository;
import com.studyflow.integrations.GoogleCalendar;
import com.studyflow.integrations.LocalIntegrations;
import com.studyflow.integrations.OpenAiProvider;
import com.studyflow.repository.MemoryStorage;
import com.studyflow.repository.NoteRepository;
import com.studyflow.repository.SettingsRepository;
import com.studyflow.repository.StorageException;
import com.studyflow.repository.StudyHistoryRepository;
import com.studyflow.repository.SubjectRepository;
import com.studyflow.repository.TaskRepository;
import com.studyflow.repository.TimetableRepository;
import com.studyflow.repository.WorkspaceRepository;
import com.studyflow.service.NoteManager;
import com.studyflow.service.AttendanceService;
import com.studyflow.service.MaterialLibrary;
import com.studyflow.service.StudyAssistant;
import com.studyflow.service.SearchService;
import com.studyflow.service.SubjectManager;
import com.studyflow.service.TaskManager;
import com.studyflow.service.TimetableService;
import com.studyflow.service.WorkspaceManager;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Owns a locked local profile and assembles its storage-backed application services. */
public final class StudyFlowContext implements AutoCloseable {
    private final DataDirectoryLock lock;
    private final FileStorage storage;
    private final SettingsRepository settingsRepository;
    private final TaskManager taskManager;
    private final SubjectManager subjectManager;
    private final WorkspaceManager workspaceManager;
    private final NoteManager noteManager;
    private final SearchService searchService;
    private final FocusRepository focusRepository;
    private final AttendanceService attendanceService;
    private final MaterialLibrary materialLibrary;
    private final StudyAssistant studyAssistant;
    private final LocalIntegrations integrations;
    private final TimetableService timetable;
    private final OpenAiProvider aiProvider;
    private UserSettings settings;

    private StudyFlowContext(DataDirectoryLock lock, FileStorage storage, Clock clock) {
        this.lock = lock;
        this.storage = storage;
        Path directory = storage.dataDirectory();
        ProfileRepositories repositories = new ProfileRepositories(storage);
        settingsRepository = repositories.settings();
        settings = settingsRepository.load();
        materialLibrary = new MaterialLibrary(directory.resolve("materials"), new MaterialRepository(storage),
                repositories.subjects()::existsById, clock);
        aiProvider = OpenAiProvider.fromEnvironment();
        studyAssistant = new StudyAssistant(materialLibrary, aiProvider, new StudyHistoryRepository(storage), clock);
        integrations = new LocalIntegrations(new IntegrationRepository(storage), new GoogleCalendar(), clock);
        timetable = new TimetableService(new TimetableRepository(storage), clock.getZone());
        attendanceService = new AttendanceService(repositories.attendance(),
                repositories.attendanceTargets(), repositories.subjects()::load,
                () -> settings().getDefaultAttendanceTarget());
        Function<String, Optional<Subject>> subjects = repositories.subjects()::findById;
        taskManager = validate(directory, "tasks/tasks.json",
                () -> new TaskManager(clock, () -> settings().getDefaultPriority(), subjects, repositories.tasks()));
        subjectManager = validate(directory, "subjects/subjects.json",
                () -> new SubjectManager(repositories.subjects(), taskManager,
                        repositories.notes()::load, repositories.workspaces()::load,
                        id -> attendanceService.references(id) || materialLibrary.references(id)));
        workspaceManager = validate(directory, "workspaces/workspaces.json",
                () -> new WorkspaceManager(repositories.workspaces(), subjects, clock));
        noteManager = validate(directory, "notes/notes.json",
                () -> new NoteManager(clock, subjects, repositories.notes()));
        searchService = new SearchService(taskManager, subjectManager, noteManager, workspaceManager);
        focusRepository = new FocusRepository(storage);
        focusRepository.load();
        validateAttendanceReferences(directory, repositories.subjects().load(),
                repositories.attendance().load(), repositories.attendanceTargets().load());
    }

    public static StudyFlowContext open(Path dataDirectory) throws IOException {
        return open(dataDirectory, Clock.systemDefaultZone());
    }

    public static StudyFlowContext open(Path dataDirectory, Clock clock) throws IOException {
        Path directory = dataDirectory.toAbsolutePath().normalize();
        DataDirectoryLock lock = acquire(directory);
        try {
            return new StudyFlowContext(lock, new FileStorage(directory), Objects.requireNonNull(clock, "clock"));
        } catch (RuntimeException error) {
            try {
                lock.close();
            } catch (IOException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    /** Locks and validates the profile before backing up one saved record type. */
    public static Path backup(Path dataDirectory, String type) throws IOException {
        Path directory = dataDirectory.toAbsolutePath().normalize();
        try (DataDirectoryLock ignored = acquire(directory); FileStorage storage = new FileStorage(directory)) {
            ProfileRepositories repositories = new ProfileRepositories(storage);
            UserSettings settings = repositories.settings().load();
            List<Subject> subjects = repositories.subjects().load();
            List<Workspace> workspaces = repositories.workspaces().load();
            List<Task> tasks = repositories.tasks().load();
            List<Note> notes = repositories.notes().load();
            validateProfile(directory, tasks, subjects, workspaces, notes, settings);
            validateAttendanceReferences(directory, subjects,
                    repositories.attendance().load(), repositories.attendanceTargets().load());

            return switch (type) {
                case "tasks" -> repositories.tasks().backup();
                case "subjects" -> repositories.subjects().backup();
                case "workspaces" -> repositories.workspaces().backup();
                case "notes" -> repositories.notes().backup();
                case "settings" -> repositories.settings().backup();
                case "attendance" -> repositories.attendance().backup();
                case "attendance-targets" -> repositories.attendanceTargets().backup();
                case "focus" -> new FocusRepository(storage).backup();
                case "integrations" -> new IntegrationRepository(storage).backup();
                case "timetable" -> new TimetableRepository(storage).backup();
                default -> throw new IllegalArgumentException("Unknown record type for backup: " + type);
            };
        }
    }

    public static void restore(Path dataDirectory, String type, Path backup) throws IOException {
        Path directory = dataDirectory.toAbsolutePath().normalize();
        try (DataDirectoryLock ignored = acquire(directory); FileStorage storage = new FileStorage(directory)) {
            ProfileRepositories repositories = new ProfileRepositories(storage);
            switch (type) {
                case "tasks" -> repositories.tasks().restore(backup, tasks -> validateProfile(directory,
                        tasks, repositories.subjects().load(), repositories.workspaces().load(),
                        repositories.notes().load(), repositories.settings().load()));
                case "subjects" -> repositories.subjects().restore(backup, subjects -> {
                    validateProfile(directory, repositories.tasks().load(), subjects, repositories.workspaces().load(),
                            repositories.notes().load(), repositories.settings().load());
                    validateAttendanceReferences(directory, subjects,
                            repositories.attendance().load(), repositories.attendanceTargets().load());
                    validateSubjectReferences(directory.resolve("materials/materials.json"), subjectIds(subjects),
                            new MaterialRepository(storage).load().stream()
                                    .map(StudyMaterial::subjectId).filter(Objects::nonNull));
                });
                case "workspaces" -> repositories.workspaces().restore(backup, workspaces -> validateProfile(directory,
                        repositories.tasks().load(), repositories.subjects().load(), workspaces,
                        repositories.notes().load(), repositories.settings().load()));
                case "notes" -> repositories.notes().restore(backup, notes -> validateProfile(directory,
                        repositories.tasks().load(), repositories.subjects().load(), repositories.workspaces().load(),
                        notes, repositories.settings().load()));
                case "settings" -> repositories.settings().restore(backup, settings -> validateProfile(directory,
                        repositories.tasks().load(), repositories.subjects().load(), repositories.workspaces().load(),
                        repositories.notes().load(), settings));
                case "attendance" -> repositories.attendance().restore(backup, entries ->
                        validateSubjectReferences(directory.resolve("attendance/classes.json"),
                                subjectIds(repositories.subjects().load()), entries.stream().map(AttendanceEntry::subjectId)));
                case "attendance-targets" -> repositories.attendanceTargets().restore(backup, targets ->
                        validateSubjectReferences(directory.resolve("attendance/targets.json"),
                                subjectIds(repositories.subjects().load()), targets.stream().map(AttendanceTarget::subjectId)));
                case "focus" -> new FocusRepository(storage).restore(backup, records -> { });
                case "integrations" -> new IntegrationRepository(storage).restore(backup, records -> { });
                case "timetable" -> new TimetableRepository(storage).restore(backup);
                default -> throw new IllegalArgumentException("Unknown record type for recovery: " + type);
            }
        }
    }

    private static void validateAttendanceReferences(Path directory, List<Subject> subjects,
                                                     List<AttendanceEntry> entries, List<AttendanceTarget> targets) {
        Set<String> knownSubjects = subjectIds(subjects);
        validateSubjectReferences(directory.resolve("attendance/classes.json"), knownSubjects,
                entries.stream().map(AttendanceEntry::subjectId));
        validateSubjectReferences(directory.resolve("attendance/targets.json"), knownSubjects,
                targets.stream().map(AttendanceTarget::subjectId));
    }

    private static Set<String> subjectIds(List<Subject> subjects) {
        return subjects.stream().map(Subject::getId).collect(Collectors.toUnmodifiableSet());
    }

    private static void validateSubjectReferences(Path path, Set<String> subjectIds, Stream<String> references) {
        references.filter(id -> !subjectIds.contains(id)).findFirst().ifPresent(id -> {
            throw new StorageException(path, "Unknown subject ID: " + id, null);
        });
    }

    private static void validateProfile(Path directory, List<Task> tasks, List<Subject> subjects,
                                        List<Workspace> workspaces, List<Note> notes, UserSettings settings) {
        Clock clock = Clock.systemDefaultZone();
        Storage<List<Subject>> subjectStorage = new MemoryStorage<>(subjects);
        Function<String, Optional<Subject>> lookup = id -> subjects.stream()
                .filter(subject -> subject.getId().equals(id)).findFirst();
        TaskManager taskManager = validate(directory, "tasks/tasks.json",
                () -> new TaskManager(clock, settings.getDefaultPriority(), lookup, new MemoryStorage<>(tasks)));
        validate(directory, "subjects/subjects.json",
                () -> new SubjectManager(subjectStorage, taskManager, () -> notes, () -> workspaces));
        validate(directory, "workspaces/workspaces.json",
                () -> new WorkspaceManager(new MemoryStorage<>(workspaces), lookup, clock));
        validate(directory, "notes/notes.json", () -> new NoteManager(clock, lookup, new MemoryStorage<>(notes)));
    }

    private static <T> T validate(Path directory, String relativePath, Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid records in " + directory.resolve(relativePath)
                    + ": " + error.getMessage(), error);
        }
    }

    private static DataDirectoryLock acquire(Path directory) throws IOException {
        try {
            return DataDirectoryLock.acquire(directory);
        } catch (IOException error) {
            throw new IOException("Cannot open the data directory " + directory + ": " + error.getMessage(), error);
        }
    }

    public TaskManager taskManager() {
        return taskManager;
    }

    public SubjectManager subjectManager() {
        return subjectManager;
    }

    public WorkspaceManager workspaceManager() {
        return workspaceManager;
    }

    public NoteManager noteManager() {
        return noteManager;
    }

    public SearchService searchService() {
        return searchService;
    }

    public FocusRepository focusRepository() {
        return focusRepository;
    }

    public AttendanceService attendanceService() {
        return attendanceService;
    }

    public MaterialLibrary materialLibrary() {
        return materialLibrary;
    }

    public StudyAssistant studyAssistant() {
        return studyAssistant;
    }

    public LocalIntegrations integrations() { return integrations; }

    public OpenAiProvider aiProvider() { return aiProvider; }

    public TimetableService timetable() { return timetable; }

    public synchronized UserSettings settings() {
        return settings.copy();
    }

    public synchronized void saveSettings(UserSettings candidate) {
        UserSettings checked = Objects.requireNonNull(candidate, "settings").copy();
        settingsRepository.save(checked);
        settings = checked;
    }

    @Override
    public void close() throws IOException {
        storage.close();
        lock.close();
    }

    private record ProfileRepositories(TaskRepository tasks, SubjectRepository subjects,
                                       WorkspaceRepository workspaces, NoteRepository notes,
                                       SettingsRepository settings, AttendanceRepository attendance,
                                       AttendanceTargetRepository attendanceTargets) {
        private ProfileRepositories(FileStorage storage) {
            this(new TaskRepository(storage), new SubjectRepository(storage), new WorkspaceRepository(storage),
                    new NoteRepository(storage), new SettingsRepository(storage), new AttendanceRepository(storage),
                    new AttendanceTargetRepository(storage));
        }
    }
}
