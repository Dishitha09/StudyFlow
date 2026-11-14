package com.studyflow.view.dashboard;

import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Displays a named numeric measure from the current dashboard data. */
final class SummaryCard extends JPanel {
    private final JLabel value = new JLabel("—");
    private final String title;

    SummaryCard(String title, String componentName, DashboardIcon icon) {
        super(new BorderLayout());
        this.title = title;
        setOpaque(false);
        JPanel card = ViewStyle.card(new BorderLayout(4, 4));
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JLabel caption = ViewStyle.muted(title.equals("Upcoming assignments")
                ? "<html>Upcoming<br>assignments</html>" : title);
        caption.setToolTipText(title);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 28f));
        value.setName(componentName);
        value.getAccessibleContext().setAccessibleName(title);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel iconLabel = new JLabel(icon, SwingConstants.TRAILING) {
            @Override
            public Color getForeground() {
                return ViewStyle.color(icon == DashboardIcon.ALERT ? "danger" : "accent");
            }
        };
        header.add(value, BorderLayout.CENTER);
        header.add(iconLabel, BorderLayout.LINE_END);
        card.add(header, BorderLayout.PAGE_START);
        card.add(caption, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);
    }

    void setValue(int amount) {
        value.setText(Integer.toString(amount));
        value.getAccessibleContext().setAccessibleDescription(title + ": " + amount);
    }
}
