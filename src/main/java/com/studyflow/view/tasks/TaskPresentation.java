package com.studyflow.view.tasks;

import com.studyflow.model.Subject;
import com.studyflow.model.TaskStatus;
import com.studyflow.model.TaskType;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Function;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.ListCellRenderer;

final class TaskPresentation {
    private TaskPresentation() {
    }

    static String label(Enum<?> value) {
        if (value == TaskStatus.TODO) {
            return "To do";
        }
        if (value == TaskType.GENERAL) {
            return "General task";
        }
        String text = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static String subject(Subject subject) {
        return subject.getName() + " (" + subject.getSubjectCode() + ")";
    }

    static LocalDate optionalDate(String input, String field) {
        if (input.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(input.strip());
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException(field + " must use YYYY-MM-DD.", error);
        }
    }

    static <T> void useLabels(JComboBox<T> combo, Function<? super T, String> label) {
        ListCellRenderer<T> renderer = (list, value, index, selected, focused) -> {
            DefaultListCellRenderer cell = new DefaultListCellRenderer();
            cell.putClientProperty("html.disable", Boolean.TRUE);
            return cell.getListCellRendererComponent(list, value == null ? "" : label.apply(value),
                    index, selected, focused);
        };
        combo.setRenderer(renderer);
    }

    static JLabel literalLabel(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        return label;
    }
}
