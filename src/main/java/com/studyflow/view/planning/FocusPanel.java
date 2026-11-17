package com.studyflow.view.planning;

import com.studyflow.model.FocusTimer;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.UserSettings;
import com.studyflow.utils.IdGenerator;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Duration;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
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
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/** Displays a focus countdown and sends completed focus intervals to the application for saving. */
public final class FocusPanel extends JPanel {
    public record Session(String id, String taskId, String subjectId, Duration planned, Duration actual) {}

    private final Consumer<Session> save;
    private final FocusTimer clock;
    private final Timer pulse;
    private final JLabel countdown = new JLabel("", SwingConstants.CENTER) {
        @Override
        public Color getForeground() {
            return ViewStyle.color("accent");
        }
    };
    private final JLabel interval = ViewStyle.subheading("");
    private final JLabel status = ViewStyle.muted("");
    private final JLabel total = ViewStyle.muted("");
    private final JProgressBar progress = new JProgressBar(0, 1000);
    private final JComboBox<Link> task = picker("focusTask");
    private final JComboBox<Link> subject = picker("focusSubject");
    private final JButton focus = button("Focus", "focusMode");
    private final JButton rest = button("Break", "focusBreakMode");
    private final JButton start = ViewStyle.primary(button("Start", "focusStart"));
    private final JButton pause = button("Pause", "focusPause");
    private final JButton finish = button("Finish early", "focusFinish");
    private final JButton reset = button("Reset", "focusReset");
    private final JSpinner minutes = new JSpinner(new SpinnerNumberModel(25, 1, 180, 1));
    private final JButton[] presets = {button("15 min", "focusPresetShort"), button("25 min", "focusPresetStandard"),
            button("50 min", "focusPresetLong")};
    private UserSettings settings = new UserSettings();
    private Duration focusOverride;
    private Duration breakOverride;
    private Map<String, String> taskSubjects = Map.of();
    private boolean refreshingInputs;
    private Session pending;
    private String currentTaskId;
    private String currentSubjectId;
    private boolean sending;
    private boolean failed;
    private boolean busy;
    private boolean closed;

    public FocusPanel(Consumer<Session> save) {
        this(save, new FocusTimer(System::nanoTime, FocusTimer.Mode.FOCUS, new UserSettings().getFocusDuration()));
    }

    FocusPanel(Consumer<Session> save, FocusTimer timer) {
        super(new BorderLayout(0, 18));
        this.save = Objects.requireNonNull(save);
        clock = timer;
        pulse = new Timer(150, event -> update());
        setName("focusPage");
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(25, 25, 24, 25));
        add(createHeader(), BorderLayout.PAGE_START);
        JScrollPane content = ViewStyle.scroll(createBody());
        content.setOpaque(false);
        content.getViewport().setOpaque(false);
        add(content, BorderLayout.CENTER);

        focus.addActionListener(event -> changeMode(FocusTimer.Mode.FOCUS));
        rest.addActionListener(event -> changeMode(FocusTimer.Mode.BREAK));
        start.addActionListener(event -> startOrRetry());
        pause.addActionListener(event -> {
            clock.pause();
            update();
        });
        finish.addActionListener(event -> {
            clock.finish();
            update();
        });
        reset.addActionListener(event -> reset());
        minutes.setName("focusMinutes");
        minutes.getAccessibleContext().setAccessibleName("Interval length in minutes");
        ((JSpinner.DefaultEditor) minutes.getEditor()).getTextField().setColumns(3);
        minutes.addChangeListener(event -> editDuration());
        for (int index = 0; index < presets.length; index++) {
            int choice = index;
            presets[index].addActionListener(event -> minutes.setValue(presetMinutes(choice)));
        }
        task.addActionListener(event -> {
            String id = selectedId(task);
            if (!refreshingInputs && id != null && taskSubjects.containsKey(id)) {
                selectChoice(subject, taskSubjects.get(id));
            }
        });
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ctrl SPACE"), "focus-toggle");
        getActionMap().put("focus-toggle", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (pause.isEnabled()) {
                    pause.doClick();
                } else if (start.isEnabled()) {
                    start.doClick();
                }
            }
        });
        start.setToolTipText("Start or resume the timer (Ctrl+Space)");
        pause.setToolTipText("Pause the timer (Ctrl+Space)");
        status.setName("focusStatus");
        showData(List.of(), List.of(), settings, 0);
    }

    public void showData(List<Task> tasks, List<Subject> subjects, UserSettings settings, int completedSessions) {
        this.settings = settings.copy();
        taskSubjects = tasks.stream().filter(value -> value.getSubjectId() != null)
                .collect(Collectors.toMap(Task::getId, Task::getSubjectId));
        refreshingInputs = true;
        try {
            replaceChoices(task, "No task", tasks.stream().map(value -> new Link(value.getId(), value.getTitle())));
            replaceChoices(subject, "No subject",
                    subjects.stream().map(value -> new Link(value.getId(), value.getName())));
        } finally {
            refreshingInputs = false;
        }
        total.setText(completedSessions == 1 ? "1 completed session" : completedSessions + " completed sessions");
        if (clock.state() == FocusTimer.State.READY) {
            Duration next = duration(clock.mode());
            if (!clock.planned().equals(next)) {
                clock.reset(clock.mode(), next);
            }
        }
        showDurationControls();
        update();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        update();
    }

    /** Called once for every submitted session. A failed attempt leaves the same ID available for retry. */
    public void finishSave(boolean success) {
        if (!sending) {
            return;
        }
        sending = false;
        failed = !success;
        if (success) {
            pending = null;
        }
        update();
    }

    public void stop() {
        closed = true;
        pulse.stop();
    }

    public boolean hasUnfinishedInterval() {
        update();
        FocusTimer.State state = clock.state();
        return state == FocusTimer.State.RUNNING || state == FocusTimer.State.PAUSED
                || pending != null && !sending;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.add(ViewStyle.heading("Focus session"), BorderLayout.LINE_START);
        total.setName("focusCompleted");
        header.add(total, BorderLayout.LINE_END);
        return header;
    }

    private JPanel createBody() {
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = 0;
        cell.gridy = 0;
        cell.weightx = 1;
        cell.weighty = 1;
        cell.anchor = GridBagConstraints.NORTH;
        cell.fill = GridBagConstraints.HORIZONTAL;
        JPanel card = ViewStyle.card(new BorderLayout(0, 18));
        card.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        card.add(createTimerFace(), BorderLayout.PAGE_START);
        JPanel options = new JPanel(new BorderLayout(0, 10));
        options.setOpaque(false);
        JLabel length = ViewStyle.muted("Minutes:");
        length.setLabelFor(minutes);
        options.add(row(new FlowLayout(FlowLayout.CENTER, 6, 4), length, minutes,
                presets[0], presets[1], presets[2]), BorderLayout.PAGE_START);
        options.add(createLinks(), BorderLayout.CENTER);
        card.add(options, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setOpaque(false);
        JPanel actions = row(new FlowLayout(FlowLayout.CENTER, 7, 4), start, pause, finish, reset);
        footer.add(actions, BorderLayout.PAGE_START);
        status.setHorizontalAlignment(SwingConstants.CENTER);
        footer.add(status, BorderLayout.CENTER);
        card.add(footer, BorderLayout.PAGE_END);
        body.add(card, cell);
        return body;
    }

    private JPanel createTimerFace() {
        JPanel face = new JPanel(new BorderLayout(0, 6));
        face.setOpaque(false);
        face.add(row(new FlowLayout(FlowLayout.CENTER, 6, 4), focus, rest), BorderLayout.PAGE_START);
        countdown.setName("focusCountdown");
        countdown.getAccessibleContext().setAccessibleName("Time remaining");
        countdown.setFont(new Font(Font.MONOSPACED, Font.BOLD, 62));
        face.add(countdown, BorderLayout.CENTER);
        JPanel caption = new JPanel(new BorderLayout(0, 9));
        caption.setOpaque(false);
        interval.setHorizontalAlignment(SwingConstants.CENTER);
        caption.add(interval, BorderLayout.PAGE_START);
        progress.setName("focusProgress");
        progress.getAccessibleContext().setAccessibleName("Interval progress");
        JPanel track = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        track.setOpaque(false);
        progress.setPreferredSize(new Dimension(260, 8));
        track.add(progress);
        caption.add(track, BorderLayout.PAGE_END);
        face.add(caption, BorderLayout.PAGE_END);
        return face;
    }

    private JPanel createLinks() {
        JPanel filters = new JPanel(new GridBagLayout());
        filters.setOpaque(false);
        JLabel taskLabel = ViewStyle.muted("Task");
        JLabel subjectLabel = ViewStyle.muted("Subject");
        taskLabel.setLabelFor(task);
        subjectLabel.setLabelFor(subject);
        addLink(filters, 0, taskLabel, task);
        addLink(filters, 1, subjectLabel, subject);
        return filters;
    }

    private static void addLink(JPanel parent, int row, JLabel label, JComponent picker) {
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridy = row;
        cell.gridx = 0;
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.insets = new Insets(2, 0, 2, 8);
        parent.add(label, cell);
        cell.gridx = 1;
        cell.insets = new Insets(2, 0, 2, 0);
        parent.add(picker, cell);
    }

    private void startOrRetry() {
        update();
        if (closed || busy || sending) {
            return;
        }
        if (pending != null) {
            failed = false;
            update();
            return;
        }
        if (minutes.isEnabled()) {
            try {
                minutes.commitEdit();
            } catch (ParseException error) {
                status.setText("Enter a whole number from 1 to 180 minutes.");
                var entry = ((JSpinner.DefaultEditor) minutes.getEditor()).getTextField();
                entry.requestFocusInWindow();
                entry.selectAll();
                return;
            }
        }
        FocusTimer.State state = clock.state();
        if (state == FocusTimer.State.FINISHED) {
            clock.reset(clock.mode(), duration(clock.mode()));
            state = FocusTimer.State.READY;
        }
        if (state == FocusTimer.State.READY) {
            currentTaskId = selectedId(task);
            currentSubjectId = selectedId(subject);
        }
        if (clock.start()) {
            pulse.start();
        }
        update();
    }

    private void changeMode(FocusTimer.Mode mode) {
        update();
        if (pending == null && mode != clock.mode() && confirmDiscard()) {
            clock.reset(mode, duration(mode));
            showDurationControls();
            update();
        }
    }

    private void reset() {
        update();
        if (pending == null && confirmDiscard()) {
            clock.reset(clock.mode(), duration(clock.mode()));
            update();
        }
    }

    private boolean confirmDiscard() {
        FocusTimer.State state = clock.state();
        if (state != FocusTimer.State.RUNNING && state != FocusTimer.State.PAUSED) {
            return true;
        }
        return JOptionPane.showConfirmDialog(this, "Discard time from the current interval?",
                "Reset interval", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private Duration duration(FocusTimer.Mode mode) {
        if (mode == FocusTimer.Mode.FOCUS) {
            return focusOverride == null ? settings.getFocusDuration() : focusOverride;
        }
        return breakOverride == null ? settings.getBreakDuration() : breakOverride;
    }

    private int presetMinutes(int index) {
        return clock.mode() == FocusTimer.Mode.FOCUS ? switch (index) {
            case 0 -> 15;
            case 1 -> 25;
            default -> 50;
        } : switch (index) {
            case 0 -> 5;
            case 1 -> 10;
            default -> 15;
        };
    }

    private void showDurationControls() {
        refreshingInputs = true;
        try {
            minutes.setValue((int) Math.clamp(clock.planned().toMinutes(), 1, 180));
            for (int index = 0; index < presets.length; index++) {
                presets[index].setText(presetMinutes(index) + " min");
            }
        } finally {
            refreshingInputs = false;
        }
    }

    private void editDuration() {
        if (refreshingInputs || !minutes.isEnabled()) {
            return;
        }
        Duration selected = Duration.ofMinutes((int) minutes.getValue());
        if (clock.mode() == FocusTimer.Mode.FOCUS) {
            focusOverride = selected;
        } else {
            breakOverride = selected;
        }
        clock.reset(clock.mode(), selected);
        update();
    }

    private void update() {
        if (closed) {
            return;
        }
        FocusTimer.State state = clock.state();
        clock.consumeCompletion().filter(result -> result.mode() == FocusTimer.Mode.FOCUS)
                .ifPresent(result -> pending = new Session(IdGenerator.next(), currentTaskId, currentSubjectId,
                        result.planned(), result.actual()));
        if (state != FocusTimer.State.RUNNING) {
            pulse.stop();
        }
        if (pending != null && !busy && !sending && !failed) {
            sending = true;
            render(state);
            save.accept(pending);
        } else {
            render(state);
        }
    }

    private void render(FocusTimer.State state) {
        Duration remaining = clock.remaining();
        long seconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
        String time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        if (!time.equals(countdown.getText())) {
            countdown.setText(time);
        }
        interval.setText(clock.mode() == FocusTimer.Mode.FOCUS ? "Focus interval" : "Short break");
        double plannedSeconds = clock.planned().toSeconds() + clock.planned().getNano() / 1_000_000_000.0;
        double remainingSeconds = remaining.toSeconds() + remaining.getNano() / 1_000_000_000.0;
        progress.setValue((int) Math.round(1000 * (1 - remainingSeconds / plannedSeconds)));
        status.setText(status(state));

        boolean available = !busy && !sending && pending == null;
        boolean running = state == FocusTimer.State.RUNNING;
        boolean active = running || state == FocusTimer.State.PAUSED;
        boolean linkable = available && !active && clock.mode() == FocusTimer.Mode.FOCUS;
        minutes.setEnabled(available && !active);
        for (JButton preset : presets) {
            preset.setEnabled(minutes.isEnabled());
        }
        task.setEnabled(linkable);
        subject.setEnabled(linkable);
        focus.setEnabled(available && clock.mode() != FocusTimer.Mode.FOCUS);
        rest.setEnabled(available && clock.mode() != FocusTimer.Mode.BREAK);
        start.setText(failed ? "Retry save" : state == FocusTimer.State.PAUSED ? "Resume" : "Start");
        start.setEnabled(!busy && (failed || available && !running));
        pause.setEnabled(available && running);
        finish.setEnabled(available && active && clock.mode() == FocusTimer.Mode.FOCUS);
        reset.setEnabled(available && state != FocusTimer.State.READY);
    }

    private String status(FocusTimer.State state) {
        if (failed) {
            return "The session is ready to retry.";
        }
        if (pending != null) {
            return sending ? "Saving session…" : "Waiting to save session…";
        }
        return switch (state) {
            case READY -> "Choose a duration, then start. Ctrl+Space starts or pauses.";
            case RUNNING -> "In progress";
            case PAUSED -> "Paused";
            case FINISHED -> clock.mode() == FocusTimer.Mode.FOCUS
                    ? "Session saved. Take a break or start another focus interval."
                    : "Break finished. Switch to Focus when you're ready.";
        };
    }

    private static JComboBox<Link> picker(String name) {
        JComboBox<Link> result = new JComboBox<>();
        result.setName(name);
        result.setPrototypeDisplayValue(new Link(null, "XXXXXXXXXXXXXXXXXXXXXXX"));
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        result.setRenderer(renderer);
        return result;
    }

    private static void replaceChoices(JComboBox<Link> field, String none, Stream<Link> records) {
        String previous = selectedId(field);
        List<Link> options = new ArrayList<>();
        options.add(new Link(null, none));
        records.forEach(options::add);
        field.setModel(new DefaultComboBoxModel<>(options.toArray(Link[]::new)));
        selectChoice(field, previous);
    }

    private static void selectChoice(JComboBox<Link> field, String id) {
        for (int index = 0; index < field.getItemCount(); index++) {
            if (Objects.equals(field.getItemAt(index).id(), id)) {
                field.setSelectedIndex(index);
                return;
            }
        }
    }

    private static String selectedId(JComboBox<Link> picker) {
        return picker.getSelectedItem() instanceof Link value ? value.id() : null;
    }

    private static JPanel row(FlowLayout layout, Component... children) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        for (Component child : children) {
            panel.add(child);
        }
        return panel;
    }

    private static JButton button(String text, String name) {
        JButton button = new JButton(text);
        button.setName(name);
        return button;
    }

    private record Link(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
