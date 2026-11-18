package com.studyflow.view.planning;

import com.studyflow.integrations.GoogleCalendar;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskType;
import com.studyflow.model.TimetableEntry;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Monthly view of StudyFlow deadlines, timetable entries and read-only Google events. */
public final class CalendarPanel extends JPanel {
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SHORT_DATE_TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm");

    private final Consumer<String> openTask;
    private final Consumer<Task> editTask;
    private final Consumer<String> editSubject;
    private final Consumer<YearMonth> monthChanged;
    private final TimetableActions timetableActions;
    private final JLabel monthLabel = ViewStyle.subheading("");
    private final JLabel selectedLabel = ViewStyle.subheading("");
    private final JLabel googleStatus = ViewStyle.muted("");
    private final JPanel days = new JPanel(new GridLayout(0, 7, 5, 5));
    private final JPanel eventList = new JPanel();
    private final JButton previous = new JButton("‹");
    private final JButton next = new JButton("›");
    private final JButton importTimetable = new JButton("Import timetable…");
    private final List<JButton> timetableButtons = new ArrayList<>();
    private final JComboBox<SubjectFilter> subjectFilter = new JComboBox<>();
    private YearMonth month = YearMonth.now();
    private LocalDate selected = LocalDate.now();
    private Map<LocalDate, List<Task>> tasksByDate = Map.of();
    private Map<LocalDate, List<GoogleCalendar.Event>> googleByDate = Map.of();
    private List<TimetableEntry> timetable = List.of();
    private Map<LocalDate, List<TimetableEntry>> timetableByDate = Map.of();
    private Map<String, String> subjects = Map.of();
    private Set<String> linkedTasks = Set.of();
    private boolean timetableBusy;

    public CalendarPanel(Consumer<String> openTask, Consumer<Task> editTask, Consumer<String> editSubject,
                         Runnable openGoogle, Consumer<YearMonth> monthChanged, Runnable refreshGoogleEvents,
                         TimetableActions timetableActions) {
        super(new BorderLayout(0, 14));
        this.openTask = openTask;
        this.editTask = editTask;
        this.editSubject = editSubject;
        this.monthChanged = monthChanged;
        this.timetableActions = timetableActions;
        setName("calendarPanel");
        setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));
        JPanel heading = new JPanel(new BorderLayout(10, 0));
        heading.setOpaque(false);
        heading.add(ViewStyle.heading("Calendar"), BorderLayout.LINE_START);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 5, 0));
        actions.setOpaque(false);
        calendarButton(importTimetable, "calendarImportTimetable", "Import a timetable (.ics)", this::chooseTimetable);
        JButton refresh = new JButton("Refresh Google");
        calendarButton(refresh, "calendarGoogleRefresh", "Refresh Google Calendar events", refreshGoogleEvents);
        JButton google = new JButton("Google Calendar…");
        calendarButton(google, "calendarGoogle", "Manage Google Calendar sync or export a calendar file", openGoogle);
        actions.add(importTimetable);
        actions.add(refresh);
        actions.add(google);
        heading.add(actions, BorderLayout.LINE_END);
        add(heading, BorderLayout.PAGE_START);

        JPanel content = new PlanningScrollContent(new BorderLayout(0, 14));
        content.add(monthCard(), BorderLayout.PAGE_START);
        content.add(selectedDayCard(), BorderLayout.CENTER);
        JScrollPane scroll = ViewStyle.scroll(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        render();
    }

    public YearMonth displayedMonth() {
        return month;
    }

    public void showTimetable(List<TimetableEntry> entries) {
        List<TimetableEntry> sorted = entries.stream().sorted(Comparator.comparing(TimetableEntry::start)
                .thenComparing(TimetableEntry::end).thenComparing(TimetableEntry::title)).toList();
        if (!timetable.equals(sorted)) {
            timetable = sorted;
            groupTimetableForMonth();
            render();
        }
    }

    public void showTimetableDate(LocalDate date) {
        selectDate(date);
    }

    public void setTimetableBusy(boolean busy) {
        timetableBusy = busy;
        importTimetable.setEnabled(!busy);
        timetableButtons.forEach(button -> button.setEnabled(!busy));
    }

    private void chooseTimetable() {
        JFileChooser chooser = new JFileChooser();
        chooser.setName("calendarTimetableImportChooser");
        chooser.setDialogTitle("Import timetable");
        chooser.setFileFilter(new FileNameExtensionFilter("iCalendar files (*.ics)", "ics"));
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            timetableActions.importFile(chooser.getSelectedFile().toPath());
        }
    }

    private void groupTimetableForMonth() {
        Map<LocalDate, List<TimetableEntry>> entries = new HashMap<>();
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        for (TimetableEntry entry : timetable) {
            LocalDate start = entry.start().toLocalDate();
            LocalDate end = lastDay(entry);
            if (start.isAfter(last) || end.isBefore(first)) continue;
            int from = start.isBefore(first) ? 1 : start.getDayOfMonth();
            int through = end.isAfter(last) ? last.getDayOfMonth() : end.getDayOfMonth();
            for (int day = from; day <= through; day++) {
                entries.computeIfAbsent(month.atDay(day), ignored -> new ArrayList<>()).add(entry);
            }
        }
        timetableByDate = entries;
    }

    /** Replaces the Google events for the displayed month. Dates in each event use an exclusive end. */
    public void showGoogleEvents(List<GoogleCalendar.Event> events, String status) {
        showGoogleStatus(status);
        Map<LocalDate, List<GoogleCalendar.Event>> visibleEvents = new HashMap<>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            List<GoogleCalendar.Event> onDate = new ArrayList<>();
            for (GoogleCalendar.Event event : events) {
                if (!date.isBefore(event.startDate()) && date.isBefore(event.endDateExclusive())) {
                    onDate.add(event);
                }
            }
            if (!onDate.isEmpty()) {
                visibleEvents.put(date, List.copyOf(onDate));
            }
        }
        if (!googleByDate.equals(visibleEvents)) {
            googleByDate = Map.copyOf(visibleEvents);
            render();
        }
    }

    private void showGoogleStatus(String status) {
        googleStatus.setText(status);
        googleStatus.setVisible(status != null && !status.isBlank());
    }

    public void showTasks(List<Task> tasks, List<Subject> subjects, Set<String> linkedTasks) {
        tasksByDate = tasks.stream().filter(task -> task.getDueDate() != null).sorted()
                .collect(Collectors.groupingBy(Task::getDueDate));
        this.subjects = PlanningLabels.subjectNames(subjects);
        this.linkedTasks = Set.copyOf(linkedTasks);
        SubjectFilter previous = (SubjectFilter) subjectFilter.getSelectedItem();
        var filters = new DefaultComboBoxModel<SubjectFilter>();
        filters.addElement(new SubjectFilter(null, "All subjects"));
        filters.addElement(new SubjectFilter("", "No subject"));
        subjects.stream().sorted(java.util.Comparator.comparing(Subject::getName, String.CASE_INSENSITIVE_ORDER))
                .map(subject -> new SubjectFilter(subject.getId(), subject.getName() + " (" + subject.getSubjectCode() + ")"))
                .forEach(filters::addElement);
        if (previous != null) {
            for (int index = 0; index < filters.getSize(); index++) {
                if (Objects.equals(filters.getElementAt(index).id(), previous.id())) {
                    filters.setSelectedItem(filters.getElementAt(index));
                    break;
                }
            }
        }
        subjectFilter.setModel(filters);
        render();
    }

    public void updateLinkedTasks(Set<String> linkedTasks) {
        if (!this.linkedTasks.equals(linkedTasks)) {
            this.linkedTasks = Set.copyOf(linkedTasks);
            showDate();
        }
    }

    private JPanel monthCard() {
        JPanel card = ViewStyle.card(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(14, 15, 15, 15));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        monthLabel.setName("calendarMonth");
        header.add(monthLabel, BorderLayout.LINE_START);
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.TRAILING, 5, 0));
        navigation.setOpaque(false);
        calendarButton(previous, "calendarPrevious", "Previous month", () -> changeMonth(month.minusMonths(1)));
        JButton today = new JButton("Today");
        calendarButton(today, "calendarToday", "Go to today", () -> selectDate(LocalDate.now()));
        calendarButton(next, "calendarNext", "Next month", () -> changeMonth(month.plusMonths(1)));
        navigation.add(previous);
        navigation.add(today);
        navigation.add(next);
        header.add(navigation, BorderLayout.LINE_END);
        JPanel toolbar = new JPanel(new BorderLayout(0, 6));
        toolbar.setOpaque(false);
        toolbar.add(header, BorderLayout.PAGE_START);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        filters.setOpaque(false);
        JLabel filterLabel = ViewStyle.muted("Task subject:");
        filterLabel.setLabelFor(subjectFilter);
        subjectFilter.setName("calendarSubjectFilter");
        var filterRenderer = new DefaultListCellRenderer();
        filterRenderer.putClientProperty("html.disable", Boolean.TRUE);
        subjectFilter.setRenderer(filterRenderer);
        subjectFilter.setPrototypeDisplayValue(new SubjectFilter(null, "Select a course subject"));
        subjectFilter.addActionListener(event -> render());
        filters.add(filterLabel);
        filters.add(subjectFilter);
        JPanel controls = new JPanel(new BorderLayout(8, 0));
        controls.setOpaque(false);
        controls.add(filters, BorderLayout.LINE_START);
        googleStatus.setName("calendarGoogleStatus");
        googleStatus.setHorizontalAlignment(SwingConstants.TRAILING);
        googleStatus.putClientProperty("html.disable", Boolean.TRUE);
        showGoogleStatus("");
        controls.add(googleStatus, BorderLayout.CENTER);
        toolbar.add(controls, BorderLayout.PAGE_END);
        card.add(toolbar, BorderLayout.PAGE_START);

        JPanel week = new JPanel(new GridLayout(1, 7, 5, 0));
        week.setOpaque(false);
        for (DayOfWeek day : DayOfWeek.values()) {
            JLabel name = ViewStyle.muted(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()));
            name.setHorizontalAlignment(SwingConstants.CENTER);
            week.add(name);
        }
        days.setOpaque(false);
        JPanel calendar = new JPanel(new BorderLayout(0, 5));
        calendar.setOpaque(false);
        calendar.add(week, BorderLayout.PAGE_START);
        calendar.add(days, BorderLayout.CENTER);
        card.add(calendar, BorderLayout.CENTER);
        return card;
    }

    private JPanel selectedDayCard() {
        JPanel card = ViewStyle.card(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(14, 15, 15, 15));
        selectedLabel.setName("calendarSelectedDate");
        card.add(selectedLabel, BorderLayout.PAGE_START);
        eventList.setName("calendarEvents");
        eventList.setLayout(new BoxLayout(eventList, BoxLayout.Y_AXIS));
        eventList.setOpaque(false);
        card.add(eventList, BorderLayout.CENTER);
        return card;
    }

    private void render() {
        monthLabel.setText(month.format(DateTimeFormatter.ofPattern("MMMM uuuu")));
        previous.setEnabled(!month.equals(YearMonth.from(LocalDate.MIN)));
        next.setEnabled(!month.equals(YearMonth.from(LocalDate.MAX)));
        days.removeAll();
        int offset = month.atDay(1).getDayOfWeek().getValue() - 1;
        for (int index = 0; index < offset; index++) {
            days.add(Box.createRigidArea(new Dimension(0, 34)));
        }
        LocalDate today = LocalDate.now();
        for (int value = 1; value <= month.lengthOfMonth(); value++) {
            LocalDate date = month.atDay(value);
            days.add(dayCell(date, date.equals(today)));
        }
        showDate();
        days.revalidate();
        days.repaint();
    }

    private JPanel dayCell(LocalDate date, boolean today) {
        List<Task> deadlines = deadlines(date);
        List<GoogleCalendar.Event> googleEvents = googleEvents(date);
        List<TimetableEntry> timetableEntries = timetableEntries(date);
        int googleCount = googleEvents.size();
        int timetableCount = timetableEntries.size();
        JPanel cell = new JPanel(new BorderLayout(0, 2));
        cell.setOpaque(false);
        cell.add(dayButton(date, today, deadlines, timetableCount, googleCount), BorderLayout.PAGE_START);
        if (!deadlines.isEmpty()) {
            cell.add(taskPreview(deadlines), BorderLayout.CENTER);
            addSourceCounts(cell, timetableCount, googleCount);
        } else if (timetableCount > 0) {
            TimetableEntry first = timetableEntries.getFirst();
            cell.add(sourcePreview("Timetable · " + first.title(), timetableCount(timetableCount) + " · "
                    + first.title(), "success"), BorderLayout.CENTER);
            addSourceCounts(cell, 0, googleCount);
        } else if (googleCount > 0) {
            GoogleCalendar.Event first = googleEvents.getFirst();
            cell.add(sourcePreview("Google · " + first.title(), googleCount(googleCount) + " · "
                    + first.title(), "accent"), BorderLayout.CENTER);
        } else {
            cell.add(Box.createRigidArea(new Dimension(0, 19)), BorderLayout.CENTER);
        }
        return cell;
    }

    private static void addSourceCounts(JPanel cell, int timetableCount, int googleCount) {
        if (timetableCount == 0 && googleCount == 0) return;
        boolean both = timetableCount > 0 && googleCount > 0;
        JPanel counts = new JPanel(new FlowLayout(FlowLayout.TRAILING, 1, 0));
        counts.setOpaque(false);
        if (timetableCount > 0) {
            counts.add(sourcePreview((both ? "T " : "Timetable ") + timetableCount,
                    timetableCount(timetableCount), "success"));
        }
        if (googleCount > 0) {
            counts.add(sourcePreview((both ? "G " : "Google ") + googleCount, googleCount(googleCount), "accent"));
        }
        cell.add(counts, BorderLayout.LINE_END);
    }

    private JButton taskPreview(List<Task> deadlines) {
        Task preview = deadlines.stream().filter(task -> task.getType() == TaskType.EXAM)
                .findFirst().orElse(deadlines.getFirst());
        JButton event = new JButton(PlanningLabels.type(preview.getType()) + " · " + preview.getTitle());
        event.setName("calendarPreview-" + preview.getId());
        event.setMargin(new Insets(0, 2, 0, 2));
        event.setPreferredSize(new Dimension(0, 19));
        event.setHorizontalAlignment(SwingConstants.LEADING);
        event.setFont(event.getFont().deriveFont(10f));
        event.setToolTipText("StudyFlow · " + PlanningLabels.type(preview.getType()) + " · " + preview.getTitle());
        if (preview.getType() == TaskType.EXAM) {
            event.setForeground(ViewStyle.color("accent"));
        }
        event.addActionListener(action -> openTask.accept(preview.getId()));
        return event;
    }

    private static JLabel sourcePreview(String text, String details, String color) {
        JLabel preview = new JLabel(text);
        preview.setFont(preview.getFont().deriveFont(10f));
        preview.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        preview.setForeground(ViewStyle.color(color));
        preview.setBackground(ViewStyle.color(color + "Soft"));
        preview.setOpaque(true);
        preview.setPreferredSize(new Dimension(preview.getPreferredSize().width, 19));
        preview.setToolTipText(details);
        preview.getAccessibleContext().setAccessibleName(details);
        return preview;
    }

    private JButton dayButton(LocalDate date, boolean today, List<Task> deadlines, int timetableCount, int googleCount) {
        long exams = deadlines.stream().filter(task -> task.getType() == TaskType.EXAM).count();
        String mark = deadlines.isEmpty() ? "" : (exams > 0 ? " ★" : " •") + deadlines.size();
        JButton button = new JButton(date.getDayOfMonth() + mark);
        button.setMargin(new Insets(2, 3, 2, 3));
        button.setPreferredSize(new Dimension(45, 30));
        button.setName("calendarDay-" + date);
        String details = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu"))
                + (today ? ", today" : "") + ", " + deadlines.size() + " StudyFlow deadlines"
                + (exams > 0 ? ", including " + exams + (exams == 1 ? " exam" : " exams") : "")
                + (timetableCount > 0 ? ", " + timetableCount(timetableCount) : "")
                + (googleCount > 0 ? ", " + googleCount(googleCount) : "");
        button.getAccessibleContext().setAccessibleName(details + (date.equals(selected) ? ", selected" : ""));
        button.setToolTipText(details);
        if (date.equals(selected)) {
            ViewStyle.primary(button);
        } else if (today) {
            button.setBackground(ViewStyle.color("accentSoft"));
            button.setForeground(ViewStyle.color("accent"));
        }
        button.addActionListener(event -> {
            selectDate(date);
            for (var cell : days.getComponents()) {
                if (cell instanceof JPanel panel && panel.getComponent(0) instanceof JButton replacement
                        && button.getName().equals(replacement.getName())) {
                    replacement.requestFocusInWindow();
                    break;
                }
            }
        });
        return button;
    }

    private void showDate() {
        selectedLabel.setText(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")));
        eventList.removeAll();
        timetableButtons.clear();
        List<Task> tasks = deadlines(selected);
        List<TimetableEntry> timetableEntries = timetableEntries(selected);
        List<GoogleCalendar.Event> events = googleEvents(selected);
        if (tasks.isEmpty() && timetableEntries.isEmpty() && events.isEmpty()) {
            eventList.add(ViewStyle.muted("No items for this date."));
        }
        tasks.forEach(task -> addEventRow(taskRow(task)));
        timetableEntries.forEach(entry -> addEventRow(timetableRow(entry)));
        events.forEach(event -> addEventRow(googleRow(event)));
        eventList.revalidate();
        eventList.repaint();
    }

    private JPanel taskRow(Task task) {
        String details = "StudyFlow · " + PlanningLabels.subject(subjects, task) + " · "
                + PlanningLabels.status(task.getStatus())
                + (linkedTasks.contains(task.getId()) ? " · Linked to Google" : "");
        JButton item = new JButton(PlanningLabels.type(task.getType()) + "  ·  " + task.getTitle());
        item.setName("calendarEvent-" + task.getId());
        item.setHorizontalAlignment(SwingConstants.LEADING);
        item.setToolTipText(details);
        if (task.getType() == TaskType.EXAM) {
            item.setForeground(ViewStyle.color("accent"));
        }
        item.addActionListener(event -> openTask.accept(task.getId()));

        JPanel row = new JPanel(new BorderLayout(6, 4));
        row.add(item, BorderLayout.CENTER);
        JLabel metadata = ViewStyle.muted("");
        metadata.putClientProperty("html.disable", Boolean.TRUE);
        metadata.setText(details);
        row.add(metadata, BorderLayout.PAGE_END);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 4, 0));
        actions.setOpaque(false);
        JButton edit = new JButton("Edit task");
        calendarButton(edit, "calendarEditTask-" + task.getId(), "Edit deadline or change the task's subject",
                () -> editTask.accept(task));
        actions.add(edit);
        if (task.getSubjectId() != null && subjects.containsKey(task.getSubjectId())) {
            JButton editCourse = new JButton("Edit subject");
            calendarButton(editCourse, "calendarEditSubject-" + task.getId(),
                    "Edit the linked course's name, code or details", () -> editSubject.accept(task.getSubjectId()));
            actions.add(editCourse);
        }
        row.add(actions, BorderLayout.LINE_END);
        return row;
    }

    private JPanel timetableRow(TimetableEntry entry) {
        String details = "Timetable · " + timetableTime(entry)
                + (entry.location().isBlank() ? "" : " · " + entry.location());
        JPanel row = externalRow(entry.title(), "calendarTimetableEvent-" + entry.id(), details, "success");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 4, 0));
        actions.setOpaque(false);
        JButton edit = new JButton("Edit");
        calendarButton(edit, "calendarTimetableEdit-" + entry.id(), "Edit timetable entry", () -> {
            TimetableEntry updated = TimetableDialog.edit(this, entry);
            if (updated != null && !updated.equals(entry)) timetableActions.update(updated);
        });
        JButton delete = new JButton("Delete");
        calendarButton(delete, "calendarTimetableDelete-" + entry.id(), "Delete timetable entry", () -> {
            if (JOptionPane.showConfirmDialog(this, "Delete this timetable entry?", "Delete timetable entry",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
                timetableActions.delete(entry.id());
            }
        });
        actions.add(edit);
        actions.add(delete);
        edit.setEnabled(!timetableBusy);
        delete.setEnabled(!timetableBusy);
        timetableButtons.add(edit);
        timetableButtons.add(delete);
        row.add(actions, BorderLayout.LINE_END);
        return row;
    }

    private JPanel googleRow(GoogleCalendar.Event event) {
        return externalRow(event.title(), "calendarGoogleEvent-" + event.id(),
                "Google Calendar · " + googleEventTime(event), "accent");
    }

    private static JPanel externalRow(String entryTitle, String titleName, String details, String color) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, ViewStyle.color(color)),
                BorderFactory.createEmptyBorder(4, 8, 4, 0)));
        JLabel title = new JLabel();
        title.putClientProperty("html.disable", Boolean.TRUE);
        title.setText(entryTitle);
        title.setName(titleName);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setToolTipText(details + " · " + entryTitle);
        row.add(title, BorderLayout.CENTER);
        JLabel source = ViewStyle.muted(details);
        source.setToolTipText(details);
        row.add(source, BorderLayout.PAGE_END);
        return row;
    }

    private static String timetableTime(TimetableEntry entry) {
        LocalDateTime start = entry.start();
        LocalDateTime end = entry.end();
        if (entry.allDay()) return allDayRange(start.toLocalDate(), lastDay(entry));
        if (start.equals(end)) return TIME.format(start);
        DateTimeFormatter format = start.toLocalDate().equals(end.toLocalDate()) ? TIME : SHORT_DATE_TIME;
        return format.format(start) + " – " + format.format(end);
    }

    private static LocalDate lastDay(TimetableEntry entry) {
        LocalDateTime end = entry.end();
        return (entry.start().equals(end) ? end : end.minusNanos(1)).toLocalDate();
    }

    private static String googleEventTime(GoogleCalendar.Event event) {
        if (!event.timeLabel().isBlank()) {
            return event.timeLabel();
        }
        return allDayRange(event.startDate(), event.endDateExclusive().minusDays(1));
    }

    private static String allDayRange(LocalDate start, LocalDate end) {
        return start.equals(end) ? "All day" : "All day · " + SHORT_DATE.format(start) + " – " + SHORT_DATE.format(end);
    }

    private void addEventRow(JPanel row) {
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        eventList.add(row);
        eventList.add(Box.createVerticalStrut(8));
    }

    private List<GoogleCalendar.Event> googleEvents(LocalDate date) {
        return googleByDate.getOrDefault(date, List.of());
    }

    private List<TimetableEntry> timetableEntries(LocalDate date) {
        return timetableByDate.getOrDefault(date, List.of());
    }

    private static String googleCount(int count) {
        return count + (count == 1 ? " Google Calendar event" : " Google Calendar events");
    }

    private static String timetableCount(int count) {
        return count + (count == 1 ? " timetable entry" : " timetable entries");
    }

    private List<Task> deadlines(LocalDate date) {
        List<Task> tasks = tasksByDate.getOrDefault(date, List.of());
        SubjectFilter filter = (SubjectFilter) subjectFilter.getSelectedItem();
        if (filter == null || filter.id() == null) {
            return tasks;
        }
        String id = filter.id().isEmpty() ? null : filter.id();
        return tasks.stream().filter(task -> Objects.equals(id, task.getSubjectId())).toList();
    }

    private record SubjectFilter(String id, String label) {
        @Override public String toString() { return label; }
    }

    private void changeMonth(YearMonth target) {
        selectDate(target.atDay(Math.min(selected.getDayOfMonth(), target.lengthOfMonth())));
    }

    private void selectDate(LocalDate date) {
        selected = date;
        YearMonth target = YearMonth.from(date);
        if (month.equals(target)) {
            render();
            return;
        }
        month = target;
        googleByDate = Map.of();
        showGoogleStatus("");
        groupTimetableForMonth();
        render();
        monthChanged.accept(target);
    }

    private static void calendarButton(JButton button, String name, String label, Runnable action) {
        button.setName(name);
        button.setToolTipText(label);
        button.getAccessibleContext().setAccessibleName(label);
        button.addActionListener(event -> action.run());
    }

    public interface TimetableActions {
        void importFile(Path source);
        void update(TimetableEntry updated);
        void delete(String id);
    }
}
