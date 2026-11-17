package com.studyflow.view.navigation;

import com.studyflow.view.ViewStyle;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.Path2D;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** Persistent navigation and active-page selection. */
public final class SidebarPanel extends JPanel {
    public enum Page {
        DASHBOARD("Dashboard", "navDashboard", 0),
        TASKS("My Tasks", "navTasks", 0),
        CALENDAR("Calendar", "navCalendar", 0),
        KANBAN("Kanban Board", "navKanban", 0),
        SUBJECTS("Subjects", "navSubjects", 0),
        WORKSPACES("Workspaces", "navWorkspaces", 0),
        NOTES("Notes", "navNotes", 0),
        SEARCH("Search", "navSearch", 0),
        FOCUS("Focus", "navFocus", 0),
        MATERIALS("Study Materials", "navMaterials", 0),
        ATTENDANCE("Attendance", "navAttendance", 0),
        ANALYTICS("Analytics", "navAnalytics", 0),
        SETTINGS("Settings", "navSettings", 0);

        private final String title;
        private final String componentName;
        private final int availableStage;

        Page(String title, String componentName, int availableStage) {
            this.title = title;
            this.componentName = componentName;
            this.availableStage = availableStage;
        }

        public String title() {
            return title;
        }

        public int availableStage() {
            return availableStage;
        }
    }

    private final Map<Page, JToggleButton> buttons = new EnumMap<>(Page.class);
    private int preferredWidth = 232;

    public SidebarPanel(String name, Consumer<Page> navigate) {
        super(new BorderLayout(0, 12));
        setName("sidebar");
        setBorder(BorderFactory.createEmptyBorder(18, 12, 14, 12));
        getAccessibleContext().setAccessibleName("StudyFlow navigation");

        JLabel brand = new JLabel(name, new NavigationIcon(null), SwingConstants.LEADING);
        brand.setIconTextGap(9);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 21f));
        JPanel header = new JPanel(new BorderLayout(0, 12));
        header.setOpaque(false);
        header.add(brand, BorderLayout.PAGE_START);

        ButtonGroup selection = new ButtonGroup();
        JToggleButton search = button(Page.SEARCH, selection, navigate);
        search.getAccessibleContext().setAccessibleName("Search StudyFlow");
        search.setToolTipText("Search StudyFlow (Ctrl+K)");
        search.putClientProperty("JButton.buttonType", null);
        Dimension searchSize = search.getPreferredSize();
        search.setPreferredSize(new Dimension(searchSize.width, Math.max(34, searchSize.height)));
        search.setLayout(new BorderLayout());
        JLabel shortcut = ViewStyle.muted("Ctrl+K");
        shortcut.setFont(shortcut.getFont().deriveFont(11f));
        shortcut.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
        search.add(shortcut, BorderLayout.LINE_END);
        header.add(search, BorderLayout.CENTER);
        add(header, BorderLayout.PAGE_START);

        JPanel items = new JPanel();
        items.setOpaque(false);
        items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
        addGroup(items, "Plan", selection, navigate, Page.DASHBOARD, Page.TASKS, Page.CALENDAR, Page.KANBAN);
        addGroup(items, "Study", selection, navigate, Page.SUBJECTS, Page.WORKSPACES, Page.NOTES, Page.MATERIALS);
        addGroup(items, "Track", selection, navigate, Page.FOCUS, Page.ATTENDANCE, Page.ANALYTICS);
        addGroup(items, "Utilities", selection, navigate, Page.SETTINGS);
        JPanel menu = new JPanel(new BorderLayout());
        menu.setOpaque(false);
        menu.add(items, BorderLayout.PAGE_START);
        JScrollPane scroll = ViewStyle.scroll(menu);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        applyColors();
    }

    private void addGroup(JPanel items, String group, ButtonGroup selection, Consumer<Page> navigate, Page... pages) {
        JLabel label = ViewStyle.muted(group);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setBorder(BorderFactory.createEmptyBorder(4, 8, 5, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        items.add(label);
        for (Page page : pages) {
            items.add(button(page, selection, navigate));
        }
        items.add(javax.swing.Box.createVerticalStrut(ViewStyle.CONTROL_GAP));
    }

    private JToggleButton button(Page page, ButtonGroup selection, Consumer<Page> navigate) {
        JToggleButton button = new JToggleButton(page.title, new NavigationIcon(page));
        button.setName(page.componentName);
        button.setToolTipText(page.title);
        button.setHorizontalAlignment(SwingConstants.LEADING);
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setIconTextGap(9);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.putClientProperty("JComponent.minimumHeight", 34);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        button.addActionListener(event -> navigate.accept(page));
        button.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                reveal(button);
            }
        });
        selection.add(button);
        buttons.put(page, button);
        return button;
    }

    public void fitWidth(int windowWidth) {
        int width = windowWidth >= 1240 ? 232 : windowWidth >= 1050 ? 206 : 192;
        if (preferredWidth != width) {
            preferredWidth = width;
            revalidate();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(preferredWidth, 0);
    }

    public void showPage(Page selected) {
        JToggleButton button = buttons.get(selected);
        button.setSelected(true);
        SwingUtilities.invokeLater(() -> reveal(button));
    }

    private static void reveal(JToggleButton button) {
        button.scrollRectToVisible(new Rectangle(0, 0, button.getWidth(), button.getHeight()));
    }

    public void applyColors() {
        setBackground(ViewStyle.color("surface"));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, ViewStyle.color("border")),
                BorderFactory.createEmptyBorder(18, 12, 14, 12)));
        String selected = ViewStyle.hex(ViewStyle.color("accentSoft"));
        String raised = ViewStyle.hex(ViewStyle.color("raised"));
        String accent = ViewStyle.hex(ViewStyle.color("accent"));
        buttons.forEach((page, button) -> button.putClientProperty("FlatLaf.style", page == Page.SEARCH
                ? "arc: 11; background: " + raised + "; selectedBackground: " + selected
                + "; selectedForeground: " + accent + "; hoverBackground: " + selected
                : "arc: 11; toolbar.spacingInsets: 0,0,0,0; toolbar.margin: 5,9,5,7; "
                + "toolbar.selectedBackground: " + selected + "; toolbar.selectedForeground: " + accent
                + "; toolbar.hoverBackground: " + raised + "; toolbar.pressedBackground: " + selected));
        repaint();
    }

    private record NavigationIcon(Page page) implements Icon {
        @Override
        public int getIconWidth() {
            return page == null ? 30 : 18;
        }

        @Override
        public int getIconHeight() {
            return getIconWidth();
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.translate(x, y);
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                canvas.setStroke(new BasicStroke(1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                canvas.setColor(component instanceof JToggleButton button && button.isSelected()
                        ? ViewStyle.color("accent") : ViewStyle.color("muted"));
                if (page == null) {
                    canvas.setColor(ViewStyle.color("primary"));
                    canvas.fillRoundRect(0, 0, 30, 30, 10, 10);
                    canvas.translate(6, 6);
                    canvas.setColor(java.awt.Color.WHITE);
                    book(canvas);
                    return;
                }
                switch (page) {
                    case DASHBOARD -> {
                        canvas.drawRoundRect(2, 2, 6, 6, 2, 2);
                        canvas.drawRoundRect(10, 2, 6, 6, 2, 2);
                        canvas.drawRoundRect(2, 10, 6, 6, 2, 2);
                        canvas.drawRoundRect(10, 10, 6, 6, 2, 2);
                    }
                    case TASKS -> {
                        canvas.drawRoundRect(2, 2, 14, 14, 4, 4);
                        path(canvas, 5, 9, 8, 12, 13, 6);
                    }
                    case CALENDAR -> {
                        canvas.drawRoundRect(2, 4, 14, 12, 3, 3);
                        canvas.drawLine(2, 8, 16, 8);
                        canvas.drawLine(6, 2, 6, 6);
                        canvas.drawLine(12, 2, 12, 6);
                    }
                    case KANBAN -> {
                        canvas.drawRoundRect(2, 2, 14, 14, 4, 4);
                        canvas.drawLine(7, 5, 7, 13);
                        canvas.drawLine(11, 5, 11, 13);
                    }
                    case SUBJECTS -> book(canvas);
                    case WORKSPACES, MATERIALS -> path(canvas, 2, 5, 7, 5, 9, 7, 16, 7, 16, 15, 2, 15, 2, 5);
                    case SEARCH -> {
                        canvas.drawOval(2, 2, 10, 10);
                        canvas.drawLine(11, 11, 16, 16);
                    }
                    case FOCUS -> {
                        canvas.drawOval(2, 2, 14, 14);
                        path(canvas, 9, 5, 9, 9, 12, 10);
                    }
                    case NOTES -> {
                        canvas.drawRoundRect(4, 2, 10, 14, 3, 3);
                        canvas.drawLine(6, 7, 12, 7);
                        canvas.drawLine(6, 10, 12, 10);
                    }
                    case ATTENDANCE -> {
                        canvas.drawOval(3, 3, 12, 12);
                        path(canvas, 6, 9, 8, 11, 12, 7);
                    }
                    case ANALYTICS -> {
                        path(canvas, 3, 3, 3, 15, 16, 15);
                        canvas.drawLine(6, 12, 6, 9);
                        canvas.drawLine(10, 12, 10, 7);
                        canvas.drawLine(14, 12, 14, 4);
                    }
                    case SETTINGS -> {
                        canvas.drawOval(4, 4, 10, 10);
                        canvas.drawOval(7, 7, 4, 4);
                        canvas.drawLine(9, 1, 9, 4);
                        canvas.drawLine(9, 14, 9, 17);
                        canvas.drawLine(1, 9, 4, 9);
                        canvas.drawLine(14, 9, 17, 9);
                    }
                }
            } finally {
                canvas.dispose();
            }
        }

        private static void book(Graphics2D canvas) {
            path(canvas, 2, 4, 6, 4, 9, 6, 12, 4, 16, 4, 16, 13, 12, 13, 9, 15, 6, 13, 2, 13, 2, 4);
            canvas.drawLine(9, 6, 9, 15);
        }

        private static void path(Graphics2D canvas, int... points) {
            Path2D line = new Path2D.Double();
            line.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) {
                line.lineTo(points[i], points[i + 1]);
            }
            canvas.draw(line);
        }
    }
}
