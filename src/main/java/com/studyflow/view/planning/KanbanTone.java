package com.studyflow.view.planning;

import com.formdev.flatlaf.FlatLaf;
import com.studyflow.model.TaskStatus;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;

/** Pairs status and urgency colors with accessible text labels. */
enum KanbanTone {
    TODO(0x607092, 0xBBC8E6, 0xEDF1F8, 0x323A50, 0x8493B9, 0x7D8FB9),
    ACTIVE(0x4E3CCF, 0xBAB0FF, 0xEDEAFF, 0x373052, 0x7965EE, 0x8E7BFF),
    DONE(0x216348, 0x99E0BB, 0xE6F5ED, 0x263C34, 0x39946F, 0x62C498),
    URGENT(0xA52342, 0xFFACB6, 0xFCEBF0, 0x482B38, 0xBC4564, 0xEB7392),
    MEDIUM(0x854600, 0xFFD0A0, 0xFDF0DF, 0x44362C, 0x9F5A22, 0xD69765);

    private final int inkLight;
    private final int inkDark;
    private final int fillLight;
    private final int fillDark;
    private final int accentLight;
    private final int accentDark;

    KanbanTone(int inkLight, int inkDark, int fillLight, int fillDark, int accentLight, int accentDark) {
        this.inkLight = inkLight;
        this.inkDark = inkDark;
        this.fillLight = fillLight;
        this.fillDark = fillDark;
        this.accentLight = accentLight;
        this.accentDark = accentDark;
    }

    static KanbanTone forStatus(TaskStatus status) {
        return switch (status) {
            case TODO -> TODO;
            case IN_PROGRESS -> ACTIVE;
            case COMPLETED -> DONE;
        };
    }

    Color ink() {
        return color(inkLight, inkDark);
    }

    Color fill() {
        return color(fillLight, fillDark);
    }

    Color accent() {
        return color(accentLight, accentDark);
    }

    Icon dot() {
        return new Icon() {
            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D canvas = (Graphics2D) graphics.create();
                try {
                    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    canvas.setColor(accent());
                    canvas.fillOval(x, y, 8, 8);
                } finally {
                    canvas.dispose();
                }
            }

            @Override
            public int getIconWidth() {
                return 8;
            }

            @Override
            public int getIconHeight() {
                return 8;
            }
        };
    }

    JLabel pill(String text) {
        JLabel badge = new JLabel(text) {
            @Override
            public Color getForeground() {
                return ink();
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D canvas = (Graphics2D) graphics.create();
                try {
                    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    canvas.setColor(fill());
                    canvas.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                } finally {
                    canvas.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        badge.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        badge.setFont(badge.getFont().deriveFont(Font.BOLD));
        return badge;
    }

    private static Color color(int light, int dark) {
        return new Color(FlatLaf.isLafDark() ? dark : light);
    }
}
