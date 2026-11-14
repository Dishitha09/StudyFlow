package com.studyflow.view.settings;

import com.studyflow.model.Theme;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

/** Selects the desktop appearance and reflects the stored preference. */
public final class SettingsPanel extends JPanel {
    private final JRadioButton light = new JRadioButton("Light");
    private final JRadioButton dark = new JRadioButton("Dark");

    public SettingsPanel(Consumer<Theme> changeTheme) {
        super(new BorderLayout(0, 20));
        setBorder(BorderFactory.createEmptyBorder(26, 28, 24, 28));
        add(ViewStyle.heading("Settings"), BorderLayout.PAGE_START);

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
        card.add(themes, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(card, BorderLayout.PAGE_START);
        add(body, BorderLayout.CENTER);
    }

    public void showTheme(Theme theme) {
        light.setSelected(theme == Theme.LIGHT);
        dark.setSelected(theme == Theme.DARK);
    }

    public void setBusy(boolean busy) {
        light.setEnabled(!busy);
        dark.setEnabled(!busy);
    }
}
