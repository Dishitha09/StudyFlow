package com.studyflow.view.navigation;

import com.studyflow.view.ViewStyle;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

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

    public SidebarPanel(String name, Consumer<Page> navigate) {
        super(new BorderLayout(0, 24));
        setName("sidebar");
        setPreferredSize(new Dimension(202, 0));
        setBorder(BorderFactory.createEmptyBorder(24, 12, 18, 12));
        getAccessibleContext().setAccessibleName("StudyFlow navigation");

        JLabel brand = new JLabel(name, new NavigationIcon(null), SwingConstants.LEADING);
        brand.setIconTextGap(9);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 7, 0, 0));
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 21f));
        add(brand, BorderLayout.PAGE_START);

        JPanel items = new JPanel(new GridLayout(0, 1, 0, 3));
        items.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (Page page : Page.values()) {
            JToggleButton button = new JToggleButton(page.title, new NavigationIcon(page));
            button.setName(page.componentName);
            button.setHorizontalAlignment(SwingConstants.LEADING);
            button.setIconTextGap(10);
            button.putClientProperty("JButton.buttonType", "toolBarButton");
            button.putClientProperty("JComponent.minimumHeight", 35);
            button.addActionListener(event -> navigate.accept(page));
            if (page.availableStage > 0) {
                button.setToolTipText("Available in Stage " + page.availableStage);
            }
            group.add(button);
            buttons.put(page, button);
            items.add(button);
        }
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

    public void showPage(Page selected) {
        buttons.get(selected).setSelected(true);
    }

    public void applyColors() {
        setBackground(ViewStyle.color("surface"));
        String selected = String.format("#%06x", ViewStyle.color("accentSoft").getRGB() & 0xffffff);
        String accent = String.format("#%06x", ViewStyle.color("accent").getRGB() & 0xffffff);
        buttons.values().forEach(button -> button.putClientProperty("FlatLaf.style",
                "arc: 11; toolbar.spacingInsets: 0,0,0,0; toolbar.margin: 4,9,4,7; "
                        + "toolbar.selectedBackground: " + selected + "; toolbar.selectedForeground: " + accent));
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
                    canvas.setColor(new java.awt.Color(0x4E3CCF));
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
