package com.studyflow.view.settings;

import com.studyflow.model.Task;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Lists optional services, connection state and the user actions supported by each provider. */
public final class IntegrationsPanel extends JPanel {
    public interface Actions {
        void connect();
        void disconnect();
        void testGoogle();
        void sync(List<String> taskIds);
        void removeEvent(String taskId);
        void export(Path path, List<String> taskIds);
        void addPlaylist(String url);
        void removePlaylist(String url);
        void openPlaylist(String url);
        void testAi();
    }

    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    private final Actions actions;
    private final JLabel googleStatus = ViewStyle.muted("Checking configuration…");
    private final JLabel aiStatus = ViewStyle.muted("Checking configuration…");
    private final JLabel spotifyStatus = ViewStyle.muted("Opens saved playlists in Spotify.");
    private final JButton connect = new JButton("Connect");
    private final JButton disconnect = new JButton("Disconnect");
    private final JButton testGoogle = new JButton("Test connection");
    private final JButton sync = ViewStyle.primary(new JButton("Sync selected"));
    private final JButton removeEvent = new JButton("Remove event");
    private final JButton export = new JButton("Export .ics");
    private final JButton testAi = new JButton("Test provider");
    private final DefaultListModel<Task> taskRows = new DefaultListModel<>();
    private final JList<Task> tasks = new JList<>(taskRows);
    private final DefaultListModel<String> playlistRows = new DefaultListModel<>();
    private final JList<String> playlists = new JList<>(playlistRows);
    private final JTextField playlistUrl = new JTextField();
    private final JButton addPlaylist = new JButton("Save link");
    private final JButton openPlaylist = new JButton("Open playlist");
    private final JButton removePlaylist = new JButton("Remove");
    private boolean googleConfigured;
    private boolean googleConnected;
    private boolean aiConfigured;
    private boolean busy;

    public IntegrationsPanel(Actions actions) {
        super(new BorderLayout());
        this.actions = Objects.requireNonNull(actions, "actions");
        setName("integrationsPanel");
        Page page = new Page();
        addCard(page, googleCard(), 0);
        addCard(page, spotifyCard(), 1);
        addCard(page, aiCard(), 2);
        JScrollPane scroll = ViewStyle.scroll(page);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        updateControls();
    }

    public void showData(List<Task> items, boolean googleConfigured, boolean googleConnected, boolean aiConfigured,
                         List<String> playlistLinks) {
        this.googleConfigured = googleConfigured;
        this.googleConnected = googleConnected;
        this.aiConfigured = aiConfigured;
        if (googleConnected) {
            googleStatus.setText("Connected for this StudyFlow session.");
        } else if (googleConfigured) {
            googleStatus.setText("Configured · Disconnected");
        } else {
            googleStatus.setText("Unconfigured · Set STUDYFLOW_GOOGLE_CLIENT_ID and STUDYFLOW_GOOGLE_CLIENT_SECRET.");
        }
        aiStatus.setText(aiConfigured ? "Configured · Test model access."
                : "Unconfigured · Set OPENAI_API_KEY to enable remote study requests.");
        spotifyStatus.setText(playlistLinks.isEmpty() ? "No playlist links saved."
                : playlistLinks.size() + (playlistLinks.size() == 1 ? " saved playlist" : " saved playlists"));
        refreshTasks(items);
        refreshPlaylists(playlistLinks);
        updateControls();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        updateControls();
    }

    private JPanel googleCard() {
        JPanel card = card();
        JPanel inner = transparent(new BorderLayout(0, 9));
        card.add(inner, BorderLayout.CENTER);
        JPanel heading = transparent(new BorderLayout(0, 5));
        heading.add(ViewStyle.subheading("Google Calendar"), BorderLayout.PAGE_START);
        googleStatus.setName("integrationsGoogleStatus");
        heading.add(googleStatus, BorderLayout.CENTER);
        JPanel connection = actions();
        bind(connect, "integrationsGoogleConnect", actions::connect);
        bind(disconnect, "integrationsGoogleDisconnect", actions::disconnect);
        bind(testGoogle, "integrationsGoogleTest", actions::testGoogle);
        connection.add(connect);
        connection.add(disconnect);
        connection.add(testGoogle);
        heading.add(connection, BorderLayout.PAGE_END);
        inner.add(heading, BorderLayout.PAGE_START);

        JPanel taskArea = transparent(new BorderLayout(0, 5));
        JLabel hint = ViewStyle.muted("Dated tasks export as all-day events. Sync sends changes from StudyFlow to Google.");
        hint.setLabelFor(tasks);
        taskArea.add(hint, BorderLayout.PAGE_START);
        tasks.setName("integrationsCalendarTasks");
        tasks.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tasks.getAccessibleContext().setAccessibleName("Tasks for calendar sync or export");
        tasks.setCellRenderer(new PlainRenderer() {
            @Override
            String textFor(Object value) {
                if (!(value instanceof Task task)) {
                    return "";
                }
                String date = task.getDueDate() == null ? "No deadline" : DEADLINE.format(task.getDueDate());
                return task.getTitle() + "  ·  " + date;
            }
        });
        tasks.addListSelectionListener(event -> updateControls());
        JScrollPane taskScroll = ViewStyle.scroll(tasks);
        taskScroll.setPreferredSize(new Dimension(0, 108));
        taskArea.add(taskScroll, BorderLayout.CENTER);
        inner.add(taskArea, BorderLayout.CENTER);

        JPanel events = actions();
        bind(sync, "integrationsGoogleSync", () -> actions.sync(datedTaskIds()));
        bind(removeEvent, "integrationsGoogleRemove", this::removeSelectedEvent);
        bind(export, "integrationsCalendarExport", this::exportSelected);
        events.add(sync);
        events.add(removeEvent);
        events.add(export);
        inner.add(events, BorderLayout.PAGE_END);
        return card;
    }

    private JPanel spotifyCard() {
        JPanel card = card();
        JPanel heading = transparent(new BorderLayout(0, 5));
        heading.add(ViewStyle.subheading("Spotify"), BorderLayout.PAGE_START);
        spotifyStatus.setName("integrationsSpotifyStatus");
        heading.add(spotifyStatus, BorderLayout.CENTER);
        JPanel add = transparent(new BorderLayout(6, 0));
        playlistUrl.setName("integrationsPlaylistUrl");
        playlistUrl.getAccessibleContext().setAccessibleName("Spotify playlist link");
        playlistUrl.putClientProperty("JTextField.placeholderText", "Spotify playlist link");
        playlistUrl.addActionListener(event -> addSelectedPlaylist());
        add.add(playlistUrl, BorderLayout.CENTER);
        bind(addPlaylist, "integrationsPlaylistAdd", this::addSelectedPlaylist);
        add.add(addPlaylist, BorderLayout.LINE_END);
        heading.add(add, BorderLayout.PAGE_END);
        card.add(heading, BorderLayout.PAGE_START);

        playlists.setName("integrationsPlaylists");
        playlists.getAccessibleContext().setAccessibleName("Saved Spotify playlists");
        playlists.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playlists.setCellRenderer(new PlainRenderer());
        playlists.addListSelectionListener(event -> updateControls());
        JScrollPane playlistScroll = ViewStyle.scroll(playlists);
        playlistScroll.setPreferredSize(new Dimension(0, 72));
        card.add(playlistScroll, BorderLayout.CENTER);
        JPanel controls = actions();
        bind(openPlaylist, "integrationsPlaylistOpen", () -> actions.openPlaylist(playlists.getSelectedValue()));
        bind(removePlaylist, "integrationsPlaylistRemove", () -> actions.removePlaylist(playlists.getSelectedValue()));
        controls.add(openPlaylist);
        controls.add(removePlaylist);
        card.add(controls, BorderLayout.PAGE_END);
        playlistUrl.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateControls();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateControls();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateControls();
            }
        });
        return card;
    }

    private JPanel aiCard() {
        JPanel card = card();
        card.add(ViewStyle.subheading("AI provider"), BorderLayout.PAGE_START);
        aiStatus.setName("integrationsAiStatus");
        card.add(aiStatus, BorderLayout.CENTER);
        JPanel controls = actions();
        bind(testAi, "integrationsAiTest", actions::testAi);
        controls.add(testAi);
        card.add(controls, BorderLayout.PAGE_END);
        return card;
    }

    private void refreshTasks(List<Task> items) {
        var previous = new HashSet<>(tasks.getSelectedValuesList().stream().map(Task::getId).toList());
        taskRows.clear();
        taskRows.addAll(items);
        for (int index = 0; index < taskRows.size(); index++) {
            if (previous.contains(taskRows.get(index).getId())) {
                tasks.addSelectionInterval(index, index);
            }
        }
    }

    private void refreshPlaylists(List<String> links) {
        String selected = playlists.getSelectedValue();
        playlistRows.clear();
        playlistRows.addAll(links);
        playlists.setSelectedValue(selected, true);
        if (playlists.isSelectionEmpty() && !playlistRows.isEmpty()) {
            playlists.setSelectedIndex(0);
        }
    }

    private void removeSelectedEvent() {
        Task selected = singleTask();
        if (!busy && googleConnected && selected != null && JOptionPane.showConfirmDialog(this,
                "Remove the corresponding event from Google Calendar?", "Remove calendar event",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
            actions.removeEvent(selected.getId());
        }
    }

    private void exportSelected() {
        List<String> selected = datedTaskIds();
        if (busy || selected.isEmpty()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setName("integrationsCalendarExportChooser");
        chooser.setDialogTitle("Export calendar to .ics");
        chooser.setFileFilter(new FileNameExtensionFilter("iCalendar files", "ics"));
        chooser.setSelectedFile(new java.io.File("studyflow.ics"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path destination = chooser.getSelectedFile().toPath();
            if (!destination.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ics")) {
                destination = destination.resolveSibling(destination.getFileName() + ".ics");
            }
            if (!java.nio.file.Files.exists(destination) || JOptionPane.showConfirmDialog(this,
                    "Replace the selected calendar file?", "Export calendar", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
                actions.export(destination, selected);
            }
        }
    }

    private void addSelectedPlaylist() {
        String link = playlistUrl.getText().strip();
        if (!busy && !link.isEmpty()) {
            actions.addPlaylist(link);
        }
    }

    private List<String> datedTaskIds() {
        return tasks.getSelectedValuesList().stream().filter(task -> task.getDueDate() != null)
                .map(Task::getId).toList();
    }

    private Task singleTask() {
        List<Task> selected = tasks.getSelectedValuesList();
        return selected.size() == 1 ? selected.getFirst() : null;
    }

    private void updateControls() {
        boolean dated = !datedTaskIds().isEmpty();
        boolean selectedPlaylist = playlists.getSelectedValue() != null;
        connect.setEnabled(!busy && googleConfigured && !googleConnected);
        disconnect.setEnabled(!busy && googleConnected);
        testGoogle.setEnabled(!busy && googleConnected);
        sync.setEnabled(!busy && googleConnected && dated);
        removeEvent.setEnabled(!busy && googleConnected && singleTask() != null);
        export.setEnabled(!busy && dated);
        testAi.setEnabled(!busy && aiConfigured);
        playlistUrl.setEnabled(!busy);
        addPlaylist.setEnabled(!busy && !playlistUrl.getText().isBlank());
        openPlaylist.setEnabled(!busy && selectedPlaylist);
        removePlaylist.setEnabled(!busy && selectedPlaylist);
    }

    private static JPanel card() {
        JPanel card = ViewStyle.card(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        return card;
    }

    private static JPanel actions() {
        return transparent(new FlowLayout(FlowLayout.LEADING, 6, 0));
    }

    private static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static void bind(JButton button, String name, Runnable action) {
        button.setName(name);
        button.addActionListener(event -> action.run());
    }

    private static void addCard(JPanel page, JComponent card, int row) {
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = 0;
        cell.gridy = row;
        cell.weightx = 1;
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.insets = new Insets(0, 0, 12, 0);
        page.add(card, cell);
    }

    private static class PlainRenderer extends DefaultListCellRenderer {
        PlainRenderer() {
            putClientProperty("html.disable", Boolean.TRUE);
        }

        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                               boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, textFor(value), index, selected, focused);
            setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return this;
        }

        String textFor(Object value) {
            return value == null ? "" : value.toString();
        }
    }

    private static final class Page extends JPanel implements Scrollable {
        Page() {
            super(new GridBagLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 9));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(18, visible.height - 30);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
