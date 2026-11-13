package com.studyflow.view;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** First page of the application shell. */
public final class InitialDashboardPanel extends JPanel {
    public InitialDashboardPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(36, 36, 36, 36));

        JPanel introduction = new JPanel(new BorderLayout(0, 10));
        introduction.setBackground(UIManager.getColor("TextField.background"));
        introduction.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(26, 26, 28, 26)));

        JLabel heading = new JLabel("Academic workspace");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 28f));
        introduction.add(heading, BorderLayout.PAGE_START);

        JLabel description = new JLabel(
                "<html>Tasks, subjects, workspaces, and notes will be available from the sidebar.</html>");
        description.setFont(description.getFont().deriveFont(14f));
        introduction.add(description, BorderLayout.CENTER);

        add(introduction, BorderLayout.PAGE_START);
    }
}
