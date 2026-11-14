package com.studyflow.view.attendance;

import com.studyflow.model.AttendanceEntry;
import com.studyflow.service.AttendanceMath.Projection;
import com.studyflow.service.AttendanceService.Snapshot;
import com.studyflow.service.AttendanceService.SubjectAttendance;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;

/** Shows attendance calculations and lets students record or correct classes by subject. */
public final class AttendancePanel extends JPanel {
    public interface Actions {
        void add(String subjectId, LocalDate date, boolean attended);

        void correct(String id, LocalDate date, boolean attended);

        void setTarget(String subjectId, int target);
    }

    private final Actions actions;
    private final JComboBox<SubjectChoice> subjects = new JComboBox<>();
    private final JPanel metrics = new JPanel(new GridLayout(1, 3, 8, 0));
    private final JLabel overall = ViewStyle.muted("No classes recorded");
    private final JLabel status = new JLabel("Add a subject to track attendance.");
    private final JPanel projections = new JPanel(new GridLayout(1, 2, 8, 0));
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final HistoryModel historyModel = new HistoryModel();
    private final JTable history = new JTable(historyModel);
    private final JTextField date = new JTextField(LocalDate.now().toString(), 10);
    private final JButton attended = new JButton("Add attended");
    private final JButton absent = new JButton("Add absent");
    private final JButton correct = new JButton("Correct selected");
    private final JButton target = new JButton("Change target");
    private List<SubjectAttendance> currentSubjects = List.of();
    private boolean busy;
    private boolean loading;

    public AttendancePanel(Actions actions) {
        super(new BorderLayout(0, 14));
        this.actions = Objects.requireNonNull(actions, "actions");
        setName("attendancePanel");
        setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));
        add(ViewStyle.heading("Attendance"), BorderLayout.PAGE_START);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        content.add(createOverview(), BorderLayout.PAGE_START);
        content.add(createHistory(), BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        subjects.addActionListener(event -> {
            if (!loading) {
                showSubject();
            }
        });
        history.getSelectionModel().addListSelectionListener(event -> updateControls());
        showSubject();
    }

    public void showData(Snapshot snapshot) {
        String previousSubject = selectedSubjectId();
        currentSubjects = List.copyOf(snapshot.subjects());
        loading = true;
        subjects.removeAllItems();
        for (SubjectAttendance item : currentSubjects) {
            subjects.addItem(new SubjectChoice(item.subjectId(), item.name()));
            if (item.subjectId().equals(previousSubject)) {
                subjects.setSelectedIndex(subjects.getItemCount() - 1);
            }
        }
        loading = false;

        long total = snapshot.total();
        overall.setText(total == 0 ? "No classes recorded" : snapshot.attended() + " of " + total
                + " classes attended  ·  " + percentage(snapshot.attended() * 100.0 / total));
        showSubject();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        updateControls();
    }

    private JPanel createOverview() {
        JPanel card = ViewStyle.card(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(14, 15, 14, 15));
        JPanel profile = transparent(new BorderLayout(8, 0));
        JPanel summary = transparent(new GridLayout(0, 1, 0, 2));
        summary.add(ViewStyle.subheading("Overall attendance"));
        overall.setName("attendanceOverall");
        summary.add(overall);
        profile.add(summary, BorderLayout.CENTER);

        JPanel controls = transparent(new FlowLayout(FlowLayout.TRAILING, 6, 1));
        JLabel subjectLabel = new JLabel("Subject");
        subjectLabel.setLabelFor(subjects);
        controls.add(subjectLabel);
        subjects.setName("attendanceSubject");
        subjects.setPrototypeDisplayValue(new SubjectChoice("", "Select subject"));
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        subjects.setRenderer(renderer);
        controls.add(subjects);
        target.setName("attendanceTargetButton");
        target.addActionListener(event -> editTarget());
        controls.add(target);
        profile.add(controls, BorderLayout.LINE_END);
        addRow(card, profile, 0, 10);

        metrics.setOpaque(false);
        addRow(card, metrics, 1, 9);
        progress.setName("attendanceProgress");
        progress.setStringPainted(true);
        progress.getAccessibleContext().setAccessibleName("Selected subject attendance percentage");
        addRow(card, progress, 2, 6);
        status.setName("attendanceStatus");
        addRow(card, status, 3, 9);
        projections.setOpaque(false);
        addRow(card, projections, 4, 0);
        return card;
    }

    private JPanel createHistory() {
        JPanel card = ViewStyle.card(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(14, 15, 14, 15));
        JPanel heading = transparent(new BorderLayout(0, 6));
        heading.add(ViewStyle.subheading("Class history"), BorderLayout.PAGE_START);
        JPanel commands = transparent(new FlowLayout(FlowLayout.LEADING, 5, 0));
        JLabel dateLabel = new JLabel("Class date");
        dateLabel.setLabelFor(date);
        date.setName("attendanceDate");
        date.setToolTipText("Date in YYYY-MM-DD format");
        commands.add(dateLabel);
        commands.add(date);
        attended.setName("attendanceAddAttended");
        attended.addActionListener(event -> addClass(true));
        commands.add(ViewStyle.primary(attended));
        absent.setName("attendanceAddAbsent");
        absent.addActionListener(event -> addClass(false));
        commands.add(absent);
        correct.setName("attendanceCorrect");
        correct.addActionListener(event -> editClass());
        commands.add(correct);
        heading.add(commands, BorderLayout.CENTER);
        card.add(heading, BorderLayout.PAGE_START);

        history.setName("attendanceHistory");
        history.getAccessibleContext().setAccessibleName("Class attendance history");
        history.setRowHeight(26);
        history.setFillsViewportHeight(true);
        history.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = ViewStyle.scroll(history);
        scroll.setPreferredSize(new Dimension(440, 130));
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private void showSubject() {
        SubjectAttendance subject = selectedSubject();
        metrics.removeAll();
        projections.removeAll();
        if (subject == null) {
            progress.setValue(0);
            progress.setString("No subject selected");
            status.setText("Add a subject to track attendance.");
            status.setForeground(ViewStyle.color("muted"));
            historyModel.setRows(List.of());
        } else {
            Projection value = subject.projection();
            metrics.add(metric("Classes conducted", Long.toString(value.total()), "attendanceTotal"));
            metrics.add(metric("Classes attended", Long.toString(value.attended()), "attendanceAttended"));
            metrics.add(metric("Classes absent", Long.toString(value.total() - value.attended()), "attendanceAbsent"));
            progress.setValue((int) Math.round(value.percentage()));
            progress.setString(value.total() == 0 ? "No classes recorded" : percentage(value.percentage()));
            status.setText(attendanceStatus(value));
            status.setForeground(ViewStyle.color(value.total() > 0 && belowTarget(value) ? "danger" : "muted"));
            projections.add(metric("Consecutive classes to attend",
                    value.needed().isPresent() ? Long.toString(value.needed().getAsLong()) : "Unreachable",
                    "attendanceNeeded"));
            projections.add(metric("Maximum future classes to miss",
                    value.canMiss().isPresent() ? Long.toString(value.canMiss().getAsLong()) : "Unlimited",
                    "attendanceCanMiss"));
            historyModel.setRows(subject.history());
        }
        metrics.revalidate();
        projections.revalidate();
        metrics.repaint();
        projections.repaint();
        updateControls();
    }

    private static String attendanceStatus(Projection value) {
        String targetText = "Target " + value.target() + "%";
        if (value.total() == 0) {
            return targetText + "  ·  No attendance percentage until a class is recorded.";
        }
        String calculation = value.attended() + " ÷ " + value.total() + " × 100 = "
                + percentage(value.percentage()) + "  ·  " + targetText;
        if (belowTarget(value)) {
            return "Below target  ·  " + calculation;
        }
        return "Target met  ·  " + calculation;
    }

    private static boolean belowTarget(Projection value) {
        return value.needed().isEmpty() || value.needed().getAsLong() > 0;
    }

    private void addClass(boolean wasAttended) {
        String subjectId = selectedSubjectId();
        if (!busy && subjectId != null) {
            LocalDate classDate = parseDate(date.getText());
            if (classDate != null) {
                actions.add(subjectId, classDate, wasAttended);
            }
        }
    }

    private void editClass() {
        int index = history.getSelectedRow();
        if (busy || index < 0) {
            return;
        }
        AttendanceEntry entry = historyModel.rowAt(index);
        JTextField classDate = new JTextField(entry.date().toString(), 10);
        JComboBox<String> classStatus = new JComboBox<>(new String[]{"Attended", "Absent"});
        classStatus.setSelectedIndex(entry.attended() ? 0 : 1);
        JPanel form = transparent(new GridLayout(0, 1, 0, 5));
        JLabel dateLabel = new JLabel("Class date (YYYY-MM-DD)");
        dateLabel.setLabelFor(classDate);
        form.add(dateLabel);
        form.add(classDate);
        JLabel outcomeLabel = new JLabel("Class outcome");
        outcomeLabel.setLabelFor(classStatus);
        form.add(outcomeLabel);
        form.add(classStatus);
        if (showForm(form, "Correct class")) {
            LocalDate updatedDate = parseDate(classDate.getText());
            if (updatedDate != null) {
                actions.correct(entry.id(), updatedDate, classStatus.getSelectedIndex() == 0);
            }
        }
    }

    private void editTarget() {
        SubjectAttendance subject = selectedSubject();
        if (busy || subject == null) {
            return;
        }
        JSpinner input = new JSpinner(new SpinnerNumberModel(subject.target(), 0, 100, 1));
        input.setName("attendanceTargetValue");
        JPanel form = transparent(new BorderLayout(8, 0));
        JLabel label = new JLabel("Attendance target (%)");
        label.setLabelFor(input);
        form.add(label, BorderLayout.LINE_START);
        form.add(input, BorderLayout.CENTER);
        if (showForm(form, "Change attendance target")) {
            try {
                input.commitEdit();
                actions.setTarget(subject.subjectId(), (Integer) input.getValue());
            } catch (java.text.ParseException error) {
                inputError("Enter a whole percentage from 0 to 100.");
            }
        }
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException error) {
            inputError("Enter the class date in YYYY-MM-DD format.");
            return null;
        }
    }

    private boolean showForm(Component form, String title) {
        return JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private void inputError(String message) {
        JOptionPane.showMessageDialog(this, message, "Check attendance details", JOptionPane.WARNING_MESSAGE);
    }

    private void updateControls() {
        boolean available = !busy && selectedSubjectId() != null;
        subjects.setEnabled(!busy && !currentSubjects.isEmpty());
        date.setEnabled(available);
        attended.setEnabled(available);
        absent.setEnabled(available);
        target.setEnabled(available);
        correct.setEnabled(available && history.getSelectedRow() >= 0);
    }

    private SubjectAttendance selectedSubject() {
        int index = subjects.getSelectedIndex();
        return index < 0 ? null : currentSubjects.get(index);
    }

    private String selectedSubjectId() {
        return subjects.getSelectedItem() instanceof SubjectChoice choice ? choice.id() : null;
    }

    private static JPanel metric(String title, String value, String name) {
        JPanel metric = transparent(new BorderLayout(0, 1));
        JLabel label = ViewStyle.muted(title);
        JLabel number = ViewStyle.subheading(value);
        number.setName(name);
        number.getAccessibleContext().setAccessibleName(title);
        metric.add(label, BorderLayout.PAGE_START);
        metric.add(number, BorderLayout.CENTER);
        return metric;
    }

    private static String percentage(double value) {
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(1);
        return format.format(value) + "%";
    }

    private static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static void addRow(JPanel panel, JComponent child, int row, int bottomGap) {
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = 0;
        cell.gridy = row;
        cell.weightx = 1;
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.insets = new Insets(0, 0, bottomGap, 0);
        panel.add(child, cell);
    }

    private record SubjectChoice(String id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static final class HistoryModel extends AbstractTableModel {
        private List<AttendanceEntry> rows = List.of();

        void setRows(List<AttendanceEntry> entries) {
            rows = List.copyOf(entries);
            fireTableDataChanged();
        }

        AttendanceEntry rowAt(int index) {
            return rows.get(index);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Class date" : "Outcome";
        }

        @Override
        public Object getValueAt(int row, int column) {
            AttendanceEntry entry = rows.get(row);
            return column == 0 ? entry.date().toString() : entry.attended() ? "Attended" : "Absent";
        }
    }
}
