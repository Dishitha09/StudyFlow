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
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;

/** Visual defaults shared by the desktop pages. */
public final class ViewStyle {
    public static final int PAGE_PADDING = 24;
    public static final int SECTION_GAP = 16;
    public static final int CONTROL_GAP = 8;
    private static final String PRIMARY_BUTTON = "studyFlowPrimary";

    private ViewStyle() {
    }

    public static void applyTheme(Theme theme) {
        boolean installed = theme == Theme.DARK ? FlatDarkLaf.setup() : FlatLightLaf.setup();
        if (!installed) {
            throw new IllegalStateException("Could not initialize the StudyFlow theme.");
        }
        Font systemFont = UIManager.getLookAndFeelDefaults().getFont("defaultFont");
        if (systemFont != null) {
            UIManager.put("defaultFont", new FontUIResource(systemFont.deriveFont(Math.max(14f, systemFont.getSize2D()))));
        }
        useColor("background", "Panel.background", "ScrollPane.background", "Viewport.background");
        useColor("surface", "TextField.background", "TextArea.background", "ComboBox.background", "Table.background",
                "List.background", "Button.background");
        useColor("text", "Label.foreground", "TextField.foreground", "TextArea.foreground", "Table.foreground",
                "List.foreground", "Button.foreground", "ToggleButton.foreground", "ComboBox.foreground");
        useColor("border", "Component.borderColor", "Separator.foreground", "Table.gridColor");
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        useColor("accent", "Component.focusColor", "Component.focusedBorderColor");
        useColor("accentSoft", "Table.selectionBackground", "List.selectionBackground");
        useColor("text", "Table.selectionForeground", "List.selectionForeground");
        String primary = hex(color("primary"));
        UIManager.put("[style]Button." + PRIMARY_BUTTON,
                "arc: 12; background: " + primary + "; foreground: #ffffff; borderColor: " + primary
                        + "; hoverBackground: " + hex(color("primaryHover"))
                        + "; pressedBackground: " + hex(color("primaryPressed"))
                        + "; focusedBackground: " + primary);
        FlatLaf.updateUI();
    }

    public static Color color(String key) {
        boolean dark = FlatLaf.isLafDark();
        return switch (key) {
            case "background" -> new Color(dark ? 0x151720 : 0xF7F8FC);
            case "surface" -> new Color(dark ? 0x1E212D : 0xFFFFFF);
            case "raised" -> new Color(dark ? 0x272B39 : 0xF0F2F8);
            case "text" -> new Color(dark ? 0xF0F2F8 : 0x172033);
            case "border" -> new Color(dark ? 0x373E50 : 0xDDE2EF);
            case "accent" -> new Color(dark ? 0xBAB1FF : 0x5145CD);
            case "accentSoft" -> new Color(dark ? 0x343052 : 0xEDEAFF);
            case "primary" -> new Color(dark ? 0x6457D8 : 0x5145CD);
            case "primaryHover" -> new Color(dark ? 0x6D60DD : 0x574AD3);
            case "primaryPressed" -> new Color(dark ? 0x5244BC : 0x473AAF);
            case "muted" -> new Color(dark ? 0xB5BED0 : 0x505A70);
            case "success" -> new Color(dark ? 0x9BE4BF : 0x186348);
            case "successSoft" -> new Color(dark ? 0x203B33 : 0xE4F4EA);
            case "warning" -> new Color(dark ? 0xFAD28F : 0x805119);
            case "warningSoft" -> new Color(dark ? 0x403324 : 0xFFF0D9);
            case "danger" -> new Color(dark ? 0xFFACB6 : 0xA52342);
            case "dangerSoft" -> new Color(dark ? 0x462A38 : 0xFCE9EE);
            default -> throw new IllegalArgumentException("Unknown view color " + key);
        };
    }

    public static String hex(Color color) {
        return String.format(Locale.ROOT, "#%06x", color.getRGB() & 0xffffff);
    }

    private static void useColor(String role, String... defaults) {
        ColorUIResource value = new ColorUIResource(color(role));
        for (String name : defaults) {
            UIManager.put(name, value);
        }
    }

    public static JLabel heading(String text) {
        return label(text, Font.BOLD, 28);
    }

    public static JLabel subheading(String text) {
        return label(text, Font.BOLD, 20);
    }

    public static JLabel muted(String text) {
        return new JLabel(text) {
            @Override
            public Color getForeground() {
                return isEnabled() ? color("muted") : super.getForeground();
            }
        };
    }

    public static JPanel surface(LayoutManager layout) {
        return new JPanel(layout) {
            @Override
            public Color getBackground() {
                return color("surface");
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
                    canvas.fillRoundRect(0, 0, getWidth(), getHeight(), 17, 17);
                    canvas.setColor(color("border"));
                    canvas.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 17, 17);
                } finally {
                    canvas.dispose();
                }
            }
        };
        card.setOpaque(false);
        return card;
    }

    public static JButton primary(JButton button) {
        button.putClientProperty("FlatLaf.styleClass", PRIMARY_BUTTON);
        return button;
    }

    public static JScrollPane scroll(Component content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }
}
