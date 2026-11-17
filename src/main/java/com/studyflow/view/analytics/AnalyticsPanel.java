package com.studyflow.view.analytics;

import com.studyflow.service.AnalyticsService;
import com.studyflow.view.ViewStyle;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

/** Renders only computed analytics. A controller supplies new reports from the profile worker. */
public final class AnalyticsPanel extends JPanel {
    public record Range(LocalDate start, LocalDate end) { }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM");
    private final JLabel state = ViewStyle.muted("Preparing profile activity…");
    private final JComboBox<String> period = new JComboBox<>(new String[]{"Last 7 days", "Last 30 days",
            "Last 90 days", "Custom dates"});
    private final JTextField start = new JTextField(10);
    private final JTextField end = new JTextField(10);
    private final JButton apply = ViewStyle.primary(new JButton("Apply range"));
    private final JPanel cards = new JPanel();
    private LocalDate today;

    public AnalyticsPanel(LocalDate today, Consumer<Range> request) {
        super(new BorderLayout(0, 14));
        this.today = today;
        setName("analyticsPanel");
        setBorder(BorderFactory.createEmptyBorder(24, 24, 18, 24));
        JPanel heading = transparent(new BorderLayout(0, 8));
        JPanel intro = stacked();
        intro.add(ViewStyle.heading("Analytics"));
        intro.add(ViewStyle.muted("See where your time went and how your classes are tracking."));
        heading.add(intro, BorderLayout.PAGE_START);
        JPanel filters = transparent(new FlowLayout(FlowLayout.LEADING, 6, 3));
        field(filters, "Period", period);
        field(filters, "From", start);
        field(filters, "Through", end);
        start.setName("analyticsStart");
        end.setName("analyticsEnd");
        apply.setName("analyticsApply");
        period.setName("analyticsPeriod");
        filters.add(apply);
        heading.add(filters, BorderLayout.CENTER);
        state.setName("analyticsStatus");
        heading.add(state, BorderLayout.PAGE_END);
        add(heading, BorderLayout.PAGE_START);
        cards.setOpaque(false);
        cards.setLayout(new BoxLayout(cards, BoxLayout.PAGE_AXIS));
        JScrollPane scroll = ViewStyle.scroll(new Surface(cards));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        selectPreset();
        period.addActionListener(event -> {
            selectPreset();
            if (period.getSelectedIndex() != 3) submit(request);
        });
        apply.addActionListener(event -> submit(request));
        start.addActionListener(event -> submit(request));
        end.addActionListener(event -> submit(request));
    }

    public void setReferenceDate(LocalDate date) {
        if (!date.equals(today)) {
            today = date;
            selectPreset();
        }
    }

    public Range range() {
        try {
            LocalDate first = LocalDate.parse(start.getText().strip()), last = LocalDate.parse(end.getText().strip());
            long days = ChronoUnit.DAYS.between(first, last);
            if (days < 0 || days >= 366) throw new IllegalArgumentException();
            return new Range(first, last);
        } catch (RuntimeException invalid) {
            showError("Choose up to 366 days, with the earlier date first. Use YYYY-MM-DD.");
            return null;
        }
    }

    public void showLoading() { state.setText("Reading profile activity…"); }
    public void showError(String error) {
        state.putClientProperty("html.disable", Boolean.TRUE);
        state.setText(error);
        state.setToolTipText(error);
    }

    private void submit(Consumer<Range> request) {
        Range selected = range();
        if (selected != null) request.accept(selected);
    }

    private void selectPreset() {
        int index = period.getSelectedIndex();
        if (index != 3) {
            start.setText(today.minusDays(new int[]{6, 29, 89}[Math.max(0, index)]).toString());
            end.setText(today.toString());
        }
        start.setEditable(index == 3);
        end.setEditable(index == 3);
        start.setToolTipText("First included date, YYYY-MM-DD");
        end.setToolTipText("Last included date, YYYY-MM-DD");
    }

    public void showReport(AnalyticsService.Report report) {
        var inventory = report.inventory();
        var totals = report.totals();
        state.setText(report.range().start().format(DAY) + " through " + report.range().end().format(DAY)
                + "  ·  Activity uses saved records; task inventory reflects the current list.");
        state.setToolTipText(null);
        cards.removeAll();

        JPanel tasks = card("Current task inventory");
        JPanel inventoryBody = transparent(new BorderLayout(8, 0));
        inventoryBody.add(new Donut(inventory), BorderLayout.LINE_START);
        JPanel legend = stacked();
        JLabel completed = value(inventory.completed() + " of " + inventory.total());
        completed.setName("analyticsCompletedTotal");
        legend.add(completed);
        legend.add(ViewStyle.muted("completed"));
        legend.add(ViewStyle.muted(inventory.overdue() + " overdue"));
        legend.add(ViewStyle.muted(Math.max(0, inventory.pending() - inventory.overdue()) + " other open"));
        inventoryBody.add(legend, BorderLayout.CENTER);
        tasks.add(inventoryBody, BorderLayout.CENTER);
        if (inventory.unknownCompletionDates() > 0) {
            tasks.add(ViewStyle.muted(inventory.unknownCompletionDates() + " older completions have no date."),
                    BorderLayout.PAGE_END);
        }
        JPanel focused = metric("Focus time in range", time(totals.focusTime()),
                totals.focusSessions() + (totals.focusSessions() == 1 ? " session saved" : " sessions saved"));
        JPanel classes = metric("Attendance in range",
                totals.classes() == 0 ? "No class records" : totals.attended() + " of " + totals.classes(),
                totals.attendancePercentage().isPresent()
                        ? percent(totals.attendancePercentage().getAsDouble()) + " attended"
                        : "Add class records on Attendance");
        JPanel overview = transparent(new GridLayout(1, 3, 10, 0));
        overview.add(tasks);
        overview.add(focused);
        overview.add(classes);
        row(overview);

        JPanel trends = transparent(new GridLayout(1, 2, 10, 0));
        trends.add(trend("Task completion trend", report.days(), false));
        trends.add(trend("Focus time trend", report.days(), true));
        row(trends);

        JPanel subjects = transparent(new GridLayout(1, 2, 10, 0));
        subjects.add(subjectFocus(report.focusBySubject()));
        subjects.add(subjectAttendance(report.attendanceBySubject()));
        row(subjects);
        cards.revalidate();
        cards.repaint();
    }

    private static JPanel trend(String title, List<AnalyticsService.DailyActivity> data, boolean focus) {
        JPanel panel = card(title);
        boolean present = data.stream().anyMatch(day -> focus ? !day.focusTime().isZero() : day.taskCompletions() > 0);
        if (present) {
            panel.add(new Bars(data, focus), BorderLayout.CENTER);
            String total = focus ? time(data.stream().map(AnalyticsService.DailyActivity::focusTime)
                    .reduce(Duration.ZERO, Duration::plus)) + " recorded"
                    : data.stream().mapToLong(AnalyticsService.DailyActivity::taskCompletions).sum()
                    + " recorded completions";
            panel.add(ViewStyle.muted(total), BorderLayout.PAGE_END);
        }
        else {
            JPanel empty = stacked();
            empty.add(ViewStyle.muted(focus ? "No focus time was recorded in this range."
                    : "No task completions with known dates in this range."));
            empty.add(Box.createVerticalStrut(76));
            panel.add(empty, BorderLayout.CENTER);
        }
        return panel;
    }

    private static JPanel subjectFocus(List<AnalyticsService.SubjectFocus> data) {
        JPanel panel = card("Focus by subject");
        JPanel items = stacked();
        double highest = data.stream().mapToDouble(row -> row.focusTime().toMillis()).max().orElse(0);
        if (highest <= 0) items.add(ViewStyle.muted("Saved focus sessions will appear here."));
        for (var item : data) {
            items.add(breakdown(item.name(), time(item.focusTime()),
                    highest <= 0 ? 0 : item.focusTime().toMillis() / highest, -1, false));
        }
        panel.add(items, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel subjectAttendance(List<AnalyticsService.SubjectAttendance> data) {
        JPanel panel = card("Attendance vs target");
        JPanel items = stacked();
        if (data.isEmpty()) items.add(ViewStyle.muted("Add a subject to set an attendance target."));
        for (var item : data) {
            var rate = item.attendancePercentage();
            String label = rate.isPresent() ? percent(rate.getAsDouble()) + " · " + item.attended() + "/"
                    + item.classes() : "No classes";
            items.add(breakdown(item.name(), label + " · target " + item.target() + "%",
                    rate.isPresent() ? rate.getAsDouble() / 100 : 0, item.target() / 100.0,
                    rate.isPresent() && rate.getAsDouble() < item.target()));
        }
        panel.add(items, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel breakdown(String subject, String metadata, double fill, double target, boolean below) {
        JPanel row = transparent(new BorderLayout(6, 5));
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 7, 0));
        JLabel name = new JLabel();
        name.putClientProperty("html.disable", Boolean.TRUE);
        name.setText(subject);
        name.setToolTipText("Subject: " + subject + " · " + metadata);
        row.setToolTipText(name.getToolTipText());
        row.add(name, BorderLayout.CENTER);
        row.add(ViewStyle.muted(metadata), BorderLayout.LINE_END);
        JComponent track = new JComponent() {
            @Override protected void paintComponent(Graphics raw) {
                Graphics2D g = (Graphics2D) raw.create();
                try {
                    g.setColor(ViewStyle.color("border"));
                    g.fillRoundRect(0, 1, getWidth(), 6, 6, 6);
                    g.setColor(ViewStyle.color(below ? "danger" : "accent"));
                    g.fillRoundRect(0, 1, (int) Math.round(getWidth() * fill), 6, 6, 6);
                    if (target >= 0) {
                        g.setColor(ViewStyle.color("text"));
                        int x = Math.min(getWidth() - 1, (int) (getWidth() * target));
                        g.drawLine(x, 0, x, getHeight());
                    }
                } finally { g.dispose(); }
            }
        };
        track.setPreferredSize(new Dimension(0, 8));
        row.add(track, BorderLayout.PAGE_END);
        return row;
    }

    private static final class Donut extends JComponent {
        private final AnalyticsService.TaskInventory inventory;
        Donut(AnalyticsService.TaskInventory inventory) {
            this.inventory = inventory;
            setPreferredSize(new Dimension(64, 64));
        }
        @Override protected void paintComponent(Graphics raw) {
            Graphics2D g = (Graphics2D) raw.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = Math.min(getWidth() - 4, getHeight() - 4);
                if (size < 18) return;
                int top = (getHeight() - size) / 2;
                g.setColor(ViewStyle.color("border"));
                g.fillOval(0, top, size, size);
                if (inventory.total() > 0) {
                    int completed = (int) Math.round(360.0 * inventory.completed() / inventory.total());
                    int overdue = (int) Math.round(360.0 * inventory.overdue() / inventory.total());
                    g.setColor(ViewStyle.color("success"));
                    g.fillArc(0, top, size, size, 90, -completed);
                    g.setColor(ViewStyle.color("danger"));
                    g.fillArc(0, top, size, size, 90 - completed, -overdue);
                }
                g.setColor(ViewStyle.color("surface"));
                g.fillOval(7, top + 7, size - 14, size - 14);
            } finally { g.dispose(); }
        }
    }

    private record Bar(LocalDate day, double value) { }

    private static final class Bars extends JComponent {
        private final List<Bar> series;
        private final String unit;
        private final int interval;
        Bars(List<AnalyticsService.DailyActivity> records, boolean focus) {
            interval = records.size() > 31 ? 7 : 1;
            unit = focus ? "minutes" : "tasks";
            series = new ArrayList<>();
            for (int first = 0; first < records.size(); first += interval) {
                double total = 0;
                for (int i = first; i < Math.min(first + interval, records.size()); i++) {
                    var item = records.get(i);
                    total += focus ? item.focusTime().toMillis() / 60_000.0 : item.taskCompletions();
                }
                series.add(new Bar(records.get(first).date(), total));
            }
            setPreferredSize(new Dimension(0, 160));
            setMinimumSize(new Dimension(0, 125));
            setToolTipText(unit);
        }

        @Override public String getToolTipText(java.awt.event.MouseEvent event) {
            int index = (int) ((event.getX() - 42) * (double) series.size() / Math.max(1, getWidth() - 52));
            if (event.getX() < 42 || index < 0 || index >= series.size()) return unit;
            Bar row = series.get(index);
            String amount = unit.equals("minutes") && row.value() > 0 && row.value() < 1 ? "under 1 minute"
                    : number(row.value()) + " " + unit;
            return row.day().format(DAY) + (interval == 7 ? " week · " : " · ") + amount;
        }

        @Override protected void paintComponent(Graphics raw) {
            Graphics2D g = (Graphics2D) raw.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int left = 42, right = getWidth() - 10, baseline = getHeight() - 28, top = 24;
                if (baseline <= top || right <= left || series.isEmpty()) return;
                double max = Math.max(1, Math.ceil(series.stream().mapToDouble(Bar::value).max().orElse(1)));
                int height = baseline - top;
                g.setFont(getFont().deriveFont(11f));
                g.setColor(ViewStyle.color("muted"));
                g.drawString(unit + (interval == 7 ? " per week" : " per day"), left, 13);
                for (int tick = 0; tick <= 2; tick++) {
                    int y = baseline - height * tick / 2;
                    g.setColor(ViewStyle.color("border"));
                    g.drawLine(left, y, right, y);
                    g.setColor(ViewStyle.color("muted"));
                    g.drawString(number(max * tick / 2), 3, y + 4);
                }
                double step = (right - left) / (double) series.size();
                int count = Math.max(1, (right - left) / 63);
                int labelEvery = Math.max(1, (series.size() + count - 1) / count);
                for (int i = 0; i < series.size(); i++) {
                    Bar bar = series.get(i);
                    int x = left + (int) Math.round(i * step + step * .19);
                    int h = bar.value() <= 0 ? 0 : Math.max(2, (int) Math.round(height * bar.value() / max));
                    g.setColor(ViewStyle.color("accent"));
                    if (h > 0) g.fillRoundRect(x, baseline - h, Math.max(1, (int) (step * .62)), h, 5, 5);
                    if (i % labelEvery == 0 && x + 37 <= getWidth()) {
                        g.setColor(ViewStyle.color("muted"));
                        g.drawString(bar.day().format(DAY), x, baseline + 16);
                    }
                }
            } finally { g.dispose(); }
        }
    }

    private static String time(Duration value) {
        long minutes = value.toMinutes();
        if (minutes == 0) return value.isZero() ? "0 min" : "< 1 min";
        return minutes >= 60 ? minutes / 60 + "h " + minutes % 60 + "m" : minutes + " min";
    }
    private static String percent(double value) { return number(value) + "%"; }
    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value)
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
    private static JLabel value(String text) {
        JLabel label = ViewStyle.subheading(text);
        label.setFont(label.getFont().deriveFont(23f));
        return label;
    }
    private static JPanel card(String title) {
        JPanel card = ViewStyle.card(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        card.add(ViewStyle.subheading(title), BorderLayout.PAGE_START);
        return card;
    }
    private static JPanel metric(String title, String number, String caption) {
        JPanel card = card(title);
        JPanel metrics = stacked();
        metrics.add(value(number));
        metrics.add(ViewStyle.muted(caption));
        card.add(metrics, BorderLayout.CENTER);
        return card;
    }
    private static void field(JPanel panel, String name, JComponent input) {
        JLabel label = ViewStyle.muted(name);
        label.setLabelFor(input);
        panel.add(label);
        panel.add(input);
    }
    private void row(JPanel content) {
        content.setAlignmentX(LEFT_ALIGNMENT);
        int preferredHeight = content.getPreferredSize().height;
        content.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredHeight));
        cards.add(content);
        cards.add(Box.createVerticalStrut(10));
    }
    private static JPanel stacked() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }
    private static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }
    private static final class Surface extends JPanel implements Scrollable {
        Surface(JPanel cards) {
            super(new BorderLayout());
            setOpaque(false);
            add(cards, BorderLayout.PAGE_START);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int axis, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int axis, int direction) { return 144; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
