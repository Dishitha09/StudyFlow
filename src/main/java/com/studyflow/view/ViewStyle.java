package com.studyflow.view;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.studyflow.model.Theme;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

/** Visual defaults shared by the desktop pages. */
public final class ViewStyle {
    private ViewStyle() {
    }

    public static void applyTheme(Theme theme) {
        boolean installed = theme == Theme.DARK ? FlatDarkLaf.setup() : FlatLightLaf.setup();
        if (!installed) {
            throw new IllegalStateException("Could not initialize the StudyFlow theme.");
        }
        UIManager.put("Panel.background", new ColorUIResource(color("background")));
        UIManager.put("ScrollPane.background", new ColorUIResource(color("background")));
        UIManager.put("Viewport.background", new ColorUIResource(color("background")));
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("Component.focusColor", new ColorUIResource(color("accent")));
        UIManager.put("Table.selectionBackground", new ColorUIResource(color("accentSoft")));
        UIManager.put("List.selectionBackground", new ColorUIResource(color("accentSoft")));
        UIManager.put("Table.selectionForeground", UIManager.getColor("Label.foreground"));
        UIManager.put("List.selectionForeground", UIManager.getColor("Label.foreground"));
        FlatLaf.updateUI();
    }

    public static Color color(String key) {
        boolean dark = FlatLaf.isLafDark();
        return switch (key) {
            case "background" -> new Color(dark ? 0x191A23 : 0xF6F7FB);
            case "surface" -> new Color(dark ? 0x232530 : 0xFFFFFF);
            case "border" -> new Color(dark ? 0x373946 : 0xE0E4EF);
            case "accent" -> new Color(dark ? 0xBAB0FF : 0x4E3CCF);
            case "accentSoft" -> new Color(dark ? 0x373052 : 0xEDEAFF);
            case "muted" -> new Color(dark ? 0xB6BACD : 0x58617B);
            case "danger" -> new Color(dark ? 0xFFACB6 : 0xA52342);
            default -> throw new IllegalArgumentException("Unknown view color " + key);
        };
    }

    public static JLabel heading(String text) {
        return label(text, Font.BOLD, 28);
    }

    public static JLabel subheading(String text) {
        return label(text, Font.BOLD, 17);
    }

    public static JLabel muted(String text) {
        return new JLabel(text) {
            @Override
            public Color getForeground() {
                return isEnabled() ? color("muted") : super.getForeground();
            }
        };
    }

    private static JLabel label(String text, int style, float size) {
        JLabel label = new JLabel(text);
        Font font = label.getFont();
        label.setFont(new Font(font.getName(), style, font.getSize()).deriveFont(size));
        return label;
    }

    public static JPanel card(LayoutManager layout) {
        JPanel card = new JPanel(layout) {
            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D canvas = (Graphics2D) graphics.create();
                try {
                    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    canvas.setColor(color("surface"));
                    canvas.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                    canvas.setColor(color("border"));
                    canvas.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
                } finally {
                    canvas.dispose();
                }
            }
        };
        card.setOpaque(false);
        return card;
    }

    public static JButton primary(JButton button) {
        button.putClientProperty("FlatLaf.style",
                "arc: 12; background: #4e3ccf; foreground: #ffffff; borderColor: #4e3ccf; "
                        + "hoverBackground: #5d4be4; pressedBackground: #4130b7; focusedBackground: #4e3ccf");
        return button;
    }

    public static JScrollPane scroll(Component content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }
}
