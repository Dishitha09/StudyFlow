package com.studyflow.view.settings;

import com.studyflow.config.AppConfig;
import com.studyflow.model.Theme;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

/** Selects the desktop appearance and reflects the stored preference. */
public final class SettingsPanel extends JPanel {
    private final JRadioButton light = new JRadioButton("Light");
    private final JRadioButton dark = new JRadioButton("Dark");
    private final JCheckBox notifications = new JCheckBox("Show task reminders");
    private final javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();

    public SettingsPanel(Consumer<Theme> changeTheme, Consumer<Boolean> changeNotifications,
                         IntegrationsPanel integrations) {
        super(new BorderLayout(0, 20));
        setBorder(BorderFactory.createEmptyBorder(26, 28, 24, 28));
        JPanel heading = new JPanel(new BorderLayout(8, 0));
        heading.setOpaque(false);
        heading.add(ViewStyle.heading("Settings"), BorderLayout.LINE_START);
        heading.add(ViewStyle.muted("StudyFlow " + AppConfig.VERSION), BorderLayout.LINE_END);
        add(heading, BorderLayout.PAGE_START);

        JPanel card = ViewStyle.card(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JPanel description = new JPanel(new BorderLayout(0, 4));
        description.setOpaque(false);
        description.add(ViewStyle.subheading("Appearance"), BorderLayout.PAGE_START);
        description.add(ViewStyle.muted("Your theme is saved automatically."), BorderLayout.CENTER);
        card.add(description, BorderLayout.PAGE_START);

        JPanel themes = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        themes.setOpaque(false);
        light.setName("themeLight");
        dark.setName("themeDark");
        light.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 24));
        ButtonGroup group = new ButtonGroup();
        group.add(light);
        group.add(dark);
        light.addActionListener(event -> changeTheme.accept(Theme.LIGHT));
        dark.addActionListener(event -> changeTheme.accept(Theme.DARK));
        themes.add(light);
        themes.add(dark);
        JPanel preferences = new JPanel(new java.awt.GridLayout(0, 1, 0, 9));
        preferences.setOpaque(false);
        preferences.add(themes);
        notifications.setName("reminderPreference");
        notifications.addActionListener(event -> changeNotifications.accept(notifications.isSelected()));
        preferences.add(notifications);
        card.add(preferences, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        JPanel appearance = new JPanel(new BorderLayout());
        appearance.add(card, BorderLayout.PAGE_START);
        tabs.addTab("Appearance", appearance);
        tabs.addTab("Integrations", integrations);
        body.add(tabs, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
    }

    public void showTheme(Theme theme) {
        light.setSelected(theme == Theme.LIGHT);
        dark.setSelected(theme == Theme.DARK);
    }

    public void openIntegrations() {
        tabs.setSelectedIndex(1);
    }

    public void setBusy(boolean busy) {
        light.setEnabled(!busy);
        dark.setEnabled(!busy);
        notifications.setEnabled(!busy);
    }

    public void showNotifications(boolean enabled) {
        notifications.setSelected(enabled);
    }
}
