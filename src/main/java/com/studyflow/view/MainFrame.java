package com.studyflow.view;

import com.studyflow.config.AppConfig;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/** Top-level StudyFlow window with navigation and a central workspace. */
public final class MainFrame extends JFrame {
    private static final int SIDEBAR_WIDTH = 214;
    private static final int NAVIGATION_ROW_HEIGHT = 36;

    public MainFrame(AppConfig config) {
        this(config, new InitialDashboardPanel(), true);
    }

    public MainFrame(AppConfig config, JComponent content) {
        this(config, content, false);
    }

    private MainFrame(AppConfig config, JComponent content, boolean showOverviewNavigation) {
        super(config.name());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(740, 480));
        setSize(1020, 650);
        setLayout(new BorderLayout());
        add(createSidebar(config, showOverviewNavigation), BorderLayout.LINE_START);
        add(content, BorderLayout.CENTER);
        setLocationRelativeTo(null);
    }

    private JPanel createSidebar(AppConfig config, boolean showOverviewNavigation) {
        JPanel sidebar = new JPanel(new BorderLayout(0, 26));
        sidebar.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        sidebar.setBackground(UIManager.getColor("TextField.background"));
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(24, 16, 18, 16)));
        sidebar.add(createBrand(config.name()), BorderLayout.PAGE_START);

        JPanel navigation = new JPanel(new BorderLayout());
        navigation.setOpaque(false);
        navigation.add(createNavigation(showOverviewNavigation), BorderLayout.PAGE_START);
        sidebar.add(navigation, BorderLayout.CENTER);

        JLabel version = new JLabel("Version " + config.version());
        version.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        version.setFont(version.getFont().deriveFont(12f));
        sidebar.add(version, BorderLayout.PAGE_END);
        return sidebar;
    }

    private JPanel createBrand(String name) {
        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 7, 0, 0));
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 19f));
        brand.add(new AppMark(), BorderLayout.LINE_START);
        brand.add(nameLabel, BorderLayout.CENTER);
        return brand;
    }

    private JPanel createNavigation(boolean showOverviewNavigation) {
        JPanel navigation = new JPanel(new GridLayout(0, 1, 0, 4));
        navigation.setOpaque(false);
        navigation.getAccessibleContext().setAccessibleName("StudyFlow navigation");

        navigation.add(currentPage(showOverviewNavigation ? "Overview" : "Academic workspace"));
        String[] placeholders = showOverviewNavigation
                ? new String[]{"Tasks", "Subjects", "Workspaces", "Notes", "Search", "Calendar"}
                : new String[]{"Search", "Calendar"};
        for (String title : placeholders) {
            JButton placeholder = new JButton(title);
            placeholder.setHorizontalAlignment(SwingConstants.LEADING);
            placeholder.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            placeholder.setContentAreaFilled(false);
            placeholder.setEnabled(false);
            placeholder.getAccessibleContext().setAccessibleDescription("Available in a later stage");
            navigation.add(placeholder);
        }
        return navigation;
    }

    private static JLabel currentPage(String title) {
        JLabel currentPage = new JLabel(title);
        currentPage.setPreferredSize(new Dimension(0, NAVIGATION_ROW_HEIGHT));
        currentPage.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        currentPage.setFont(currentPage.getFont().deriveFont(Font.BOLD));
        currentPage.setBackground(UIManager.getColor("List.selectionBackground"));
        currentPage.setForeground(UIManager.getColor("List.selectionForeground"));
        currentPage.setOpaque(true);
        currentPage.getAccessibleContext().setAccessibleDescription("Current page");
        return currentPage;
    }

    private static final class AppMark extends JComponent {
        private static final int SIZE = 34;
        private static final Color BACKGROUND = new Color(72, 63, 149);

        private AppMark() {
            setPreferredSize(new Dimension(SIZE, SIZE));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                canvas.setColor(BACKGROUND);
                canvas.fillRoundRect(0, 0, SIZE, SIZE, 12, 12);
                canvas.setColor(Color.WHITE);
                canvas.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                Path2D pages = new Path2D.Double();
                pages.moveTo(8.5, 10.5);
                pages.quadTo(13, 9.5, 17, 12.5);
                pages.quadTo(21, 9.5, 25.5, 10.5);
                pages.lineTo(25.5, 22.5);
                pages.quadTo(21, 21.5, 17, 24.5);
                pages.quadTo(13, 21.5, 8.5, 22.5);
                pages.closePath();
                canvas.draw(pages);
                canvas.drawLine(17, 13, 17, 24);
            } finally {
                canvas.dispose();
            }
        }
    }
}
