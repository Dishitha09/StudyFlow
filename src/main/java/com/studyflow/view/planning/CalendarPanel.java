package com.studyflow.view.planning;

import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskType;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/** Monthly date picker and deadline list backed by the current task snapshot. */
public final class CalendarPanel extends JPanel {
    private final Consumer<String> openTask;
    private final Consumer<Task> editTask;
    private final Consumer<String> editSubject;
    private final JLabel monthLabel = ViewStyle.subheading("");
    private final JLabel selectedLabel = ViewStyle.subheading("");
    private final JPanel days = new JPanel(new GridLayout(0, 7, 5, 5));
    private final JPanel eventList = new JPanel();
    private final JButton previous = new JButton("‹");
    private final JButton next = new JButton("›");
    private final JComboBox<SubjectFilter> subjectFilter = new JComboBox<>();
    private YearMonth month = YearMonth.now();
    private LocalDate selected = LocalDate.now();
    private Map<LocalDate, List<Task>> tasksByDate = Map.of();
    private Map<String, String> subjects = Map.of();
    private Set<String> linkedTasks = Set.of();

    public CalendarPanel(Consumer<String> openTask, Consumer<Task> editTask, Consumer<String> editSubject,
                         Runnable openGoogle) {
        super(new BorderLayout(0, 14));
        this.openTask = openTask;
        this.editTask = editTask;
        this.editSubject = editSubject;
        setName("calendarPanel");
        setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));
        JPanel heading = new JPanel(new BorderLayout(10, 0));
        heading.setOpaque(false);
        heading.add(ViewStyle.heading("Calendar"), BorderLayout.LINE_START);
        JButton google = new JButton("Google Calendar…");
        calendarButton(google, "calendarGoogle", "Manage Google Calendar sync or export a calendar file", openGoogle);
        heading.add(google, BorderLayout.LINE_END);
        add(heading, BorderLayout.PAGE_START);

        JPanel content = new PlanningScrollContent(new BorderLayout(0, 14));
        content.add(monthCard(), BorderLayout.PAGE_START);
        content.add(selectedDayCard(), BorderLayout.CENTER);
        JScrollPane scroll = ViewStyle.scroll(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        render();
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
        calendarButton(today, "calendarToday", "Go to today", () -> {
            selected = LocalDate.now();
            month = YearMonth.from(selected);
            render();
        });
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
        JLabel filterLabel = ViewStyle.muted("Subject:");
        filterLabel.setLabelFor(subjectFilter);
        subjectFilter.setName("calendarSubjectFilter");
        var filterRenderer = new javax.swing.DefaultListCellRenderer();
        filterRenderer.putClientProperty("html.disable", Boolean.TRUE);
        subjectFilter.setRenderer(filterRenderer);
        subjectFilter.setPrototypeDisplayValue(new SubjectFilter(null, "Select a course subject"));
        subjectFilter.addActionListener(event -> render());
        filters.add(filterLabel);
        filters.add(subjectFilter);
        toolbar.add(filters, BorderLayout.PAGE_END);
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
        JPanel cell = new JPanel(new BorderLayout(0, 2));
        cell.setOpaque(false);
        cell.add(dayButton(date, today, deadlines), BorderLayout.PAGE_START);
        if (deadlines.isEmpty()) {
            cell.add(Box.createRigidArea(new Dimension(0, 19)), BorderLayout.CENTER);
        } else {
            Task preview = deadlines.stream().filter(task -> task.getType() == TaskType.EXAM)
                    .findFirst().orElse(deadlines.getFirst());
            JButton event = new JButton(PlanningLabels.type(preview.getType()) + " · " + preview.getTitle());
            event.setName("calendarPreview-" + preview.getId());
            event.setMargin(new Insets(0, 2, 0, 2));
            event.setPreferredSize(new Dimension(0, 19));
            event.setHorizontalAlignment(SwingConstants.LEADING);
            event.setFont(event.getFont().deriveFont(10f));
            event.setToolTipText(PlanningLabels.type(preview.getType()) + ": " + preview.getTitle());
            if (preview.getType() == TaskType.EXAM) {
                event.setForeground(ViewStyle.color("accent"));
            }
            event.addActionListener(action -> openTask.accept(preview.getId()));
            cell.add(event, BorderLayout.CENTER);
        }
        return cell;
    }

    private JButton dayButton(LocalDate date, boolean today, List<Task> deadlines) {
        long exams = deadlines.stream().filter(task -> task.getType() == TaskType.EXAM).count();
        String mark = deadlines.isEmpty() ? "" : (exams > 0 ? " ★" : " •") + deadlines.size();
        JButton button = new JButton(date.getDayOfMonth() + mark);
        button.setMargin(new Insets(2, 3, 2, 3));
        button.setPreferredSize(new Dimension(45, 30));
        button.setName("calendarDay-" + date);
        String details = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu"))
                + (today ? ", today" : "") + ", " + deadlines.size() + " deadlines"
                + (exams > 0 ? ", including " + exams + (exams == 1 ? " exam" : " exams") : "");
        button.getAccessibleContext().setAccessibleName(details + (date.equals(selected) ? ", selected" : ""));
        button.setToolTipText(details);
        if (date.equals(selected)) {
            ViewStyle.primary(button);
        } else if (today) {
            button.setBackground(ViewStyle.color("accentSoft"));
            button.setForeground(ViewStyle.color("accent"));
        }
        button.addActionListener(event -> {
            selected = date;
            render();
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
        List<Task> tasks = deadlines(selected);
        if (tasks.isEmpty()) {
            eventList.add(ViewStyle.muted("No matching deadlines for this date."));
        } else {
            for (Task task : tasks) {
                String type = PlanningLabels.type(task.getType());
                String details = PlanningLabels.subject(subjects, task) + " · " + PlanningLabels.status(task.getStatus())
                        + (linkedTasks.contains(task.getId()) ? " · Linked to Google" : "");
                JButton item = new JButton(type + "  ·  " + task.getTitle());
                item.setName("calendarEvent-" + task.getId());
                item.setHorizontalAlignment(SwingConstants.LEADING);
                item.setAlignmentX(LEFT_ALIGNMENT);
                item.setMaximumSize(new Dimension(Integer.MAX_VALUE, item.getPreferredSize().height));
                item.setToolTipText(details);
                if (task.getType() == TaskType.EXAM) {
                    item.setForeground(ViewStyle.color("accent"));
                }
                item.addActionListener(event -> openTask.accept(task.getId()));
                JPanel row = new JPanel(new BorderLayout(6, 4));
                row.setOpaque(false);
                row.setAlignmentX(LEFT_ALIGNMENT);
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
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
                eventList.add(row);
                eventList.add(Box.createVerticalStrut(8));
            }
        }
        eventList.revalidate();
        eventList.repaint();
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
        selected = target.atDay(Math.min(selected.getDayOfMonth(), target.lengthOfMonth()));
        month = target;
        render();
    }

    private static void calendarButton(JButton button, String name, String label, Runnable action) {
        button.setName(name);
        button.setToolTipText(label);
        button.getAccessibleContext().setAccessibleName(label);
        button.addActionListener(event -> action.run());
    }
}
