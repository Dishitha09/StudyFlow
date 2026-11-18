package com.studyflow.view.planning;

import com.studyflow.model.TimetableEntry;
import com.studyflow.view.FormDialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Edits one local timetable entry while keeping its import identity. */
final class TimetableDialog {
    private TimetableDialog() { }

    static TimetableEntry edit(Component owner, TimetableEntry entry) {
        JTextField title = field("timetableTitle", entry.title());
        JTextField location = field("timetableLocation", entry.location());
        JCheckBox allDay = new JCheckBox("All day", entry.allDay());
        allDay.setName("timetableAllDay");
        JTextField startDate = field("timetableStartDate", entry.start().toLocalDate().toString());
        LocalDate displayedEnd = entry.allDay() ? entry.end().minusDays(1).toLocalDate() : entry.end().toLocalDate();
        JTextField endDate = field("timetableEndDate", displayedEnd.toString());
        JTextField startTime = field("timetableStartTime", entry.allDay() ? "09:00" : entry.start().toLocalTime().toString());
        JTextField endTime = field("timetableEndTime", entry.allDay() ? "10:00" : entry.end().toLocalTime().toString());
        JTextArea description = new JTextArea(entry.description(), 4, 30);
        description.setName("timetableDescription");
        description.setLineWrap(true);
        description.setWrapStyleWord(true);

        JLabel endDateLabel = new JLabel();
        Runnable adjustFields = () -> {
            endDateLabel.setText(allDay.isSelected() ? "Last day (YYYY-MM-DD)" : "End date (YYYY-MM-DD)");
            startTime.setEnabled(!allDay.isSelected());
            endTime.setEnabled(!allDay.isSelected());
        };
        allDay.addActionListener(event -> adjustFields.run());
        adjustFields.run();

        JPanel schedule = new JPanel(new GridLayout(0, 2, 10, 5));
        schedule.add(fieldRow(new JLabel("Start date (YYYY-MM-DD)"), startDate, startDate));
        schedule.add(fieldRow(endDateLabel, endDate, endDate));
        schedule.add(fieldRow(new JLabel("Start time (24-hour)"), startTime, startTime));
        schedule.add(fieldRow(new JLabel("End time (24-hour)"), endTime, endTime));

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        add(fields, fieldRow(new JLabel("Title"), title, title));
        add(fields, fieldRow(new JLabel("Location"), location, location));
        add(fields, allDay);
        add(fields, schedule);
        add(fields, fieldRow(new JLabel("Description"), new JScrollPane(description), description));
        List<String> initial = values(title, location, startDate, endDate, startTime, endTime, description);
        return FormDialogs.show(owner, "Edit timetable entry", fields, title, "Save", () ->
                        allDay.isSelected() != entry.allDay()
                                || !initial.equals(values(title, location, startDate, endDate, startTime, endTime, description)),
                () -> {
                    LocalDate first = readDate(startDate);
                    LocalDate last = readDate(endDate);
                    boolean day = allDay.isSelected();
                    if (day && last.isBefore(first)) {
                        endDate.requestFocusInWindow();
                        throw new IllegalArgumentException("Last day cannot be before the start date.");
                    }
                    try {
                        LocalDateTime start = day ? first.atStartOfDay() : first.atTime(readTime(startTime));
                        LocalDateTime end = day ? last.plusDays(1).atStartOfDay() : last.atTime(readTime(endTime));
                        return new TimetableEntry(entry.id(), title.getText(), description.getText(), location.getText(),
                                start, end, day);
                    } catch (DateTimeException invalid) {
                        throw new IllegalArgumentException("The date range is outside the supported calendar.", invalid);
                    }
                });
    }

    private static JTextField field(String name, String value) {
        JTextField field = new JTextField(value, 12);
        field.setName(name);
        return field;
    }

    private static JPanel fieldRow(JLabel label, JComponent field, JComponent input) {
        JPanel row = new JPanel(new BorderLayout(0, 3));
        label.setLabelFor(input);
        row.add(label, BorderLayout.PAGE_START);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private static void add(JPanel fields, JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0),
                component.getBorder()));
        fields.add(component);
    }

    private static List<String> values(JTextField title, JTextField location, JTextField startDate,
                                       JTextField endDate, JTextField startTime, JTextField endTime,
                                       JTextArea description) {
        return List.of(title.getText(), location.getText(), startDate.getText(), endDate.getText(),
                startTime.getText(), endTime.getText(), description.getText());
    }

    private static LocalDate readDate(JTextField field) {
        try {
            return LocalDate.parse(field.getText().strip());
        } catch (DateTimeException invalid) {
            field.requestFocusInWindow();
            throw new IllegalArgumentException("Enter dates in YYYY-MM-DD format.", invalid);
        }
    }

    private static LocalTime readTime(JTextField field) {
        try {
            return LocalTime.parse(field.getText().strip());
        } catch (DateTimeException invalid) {
            field.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a 24-hour time such as 09:00.", invalid);
        }
    }
}
