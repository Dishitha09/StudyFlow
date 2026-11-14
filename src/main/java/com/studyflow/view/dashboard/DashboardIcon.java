package com.studyflow.view.dashboard;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import javax.swing.Icon;

/** Small outline icons that share one stroke and follow their component's foreground. */
enum DashboardIcon implements Icon {
    TASKS, CHECK, CLOCK, ALERT, CALENDAR, PLUS, BOOK, NOTE, ATTENDANCE;

    @Override
    public int getIconWidth() {
        return 16;
    }

    @Override
    public int getIconHeight() {
        return 16;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.translate(x, y);
            canvas.setColor(component.getForeground());
            canvas.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            switch (this) {
                case TASKS -> {
                    line(canvas, 6, 4, 14, 4);
                    line(canvas, 6, 8, 14, 8);
                    line(canvas, 6, 12, 14, 12);
                    dot(canvas, 3, 4);
                    dot(canvas, 3, 8);
                    dot(canvas, 3, 12);
                }
                case CHECK -> {
                    canvas.drawOval(1, 1, 13, 13);
                    line(canvas, 4.5, 7.8, 7, 10.2);
                    line(canvas, 7, 10.2, 11.1, 5.7);
                }
                case CLOCK -> {
                    canvas.drawOval(1, 1, 13, 13);
                    line(canvas, 7.5, 4.4, 7.5, 7.9);
                    line(canvas, 7.5, 7.9, 10.3, 9.5);
                }
                case ALERT -> {
                    canvas.drawPolygon(new int[]{8, 1, 15}, new int[]{1, 14, 14}, 3);
                    line(canvas, 8, 5.6, 8, 9.2);
                    dot(canvas, 8, 11.8);
                }
                case CALENDAR -> {
                    canvas.drawRoundRect(2, 3, 12, 11, 3, 3);
                    line(canvas, 5, 1.5, 5, 4.5);
                    line(canvas, 11, 1.5, 11, 4.5);
                    line(canvas, 2, 6.5, 14, 6.5);
                }
                case PLUS -> {
                    line(canvas, 3, 8, 13, 8);
                    line(canvas, 8, 3, 8, 13);
                }
                case BOOK -> {
                    canvas.drawRoundRect(3, 2, 10, 12, 2, 2);
                    line(canvas, 6, 2, 6, 14);
                    line(canvas, 8.5, 5, 11, 5);
                }
                case NOTE -> {
                    canvas.drawPolygon(new int[]{3, 10, 13, 13, 3}, new int[]{1, 1, 4, 14, 14}, 5);
                    line(canvas, 10, 1, 10, 4);
                    line(canvas, 10, 4, 13, 4);
                    line(canvas, 6, 7.5, 10, 7.5);
                    line(canvas, 6, 10.5, 10, 10.5);
                }
                case ATTENDANCE -> {
                    canvas.drawRoundRect(2, 2, 12, 12, 3, 3);
                    line(canvas, 5, 8, 7, 10);
                    line(canvas, 7, 10, 11.2, 5.8);
                }
            }
        } finally {
            canvas.dispose();
        }
    }

    private static void line(Graphics2D canvas, double x1, double y1, double x2, double y2) {
        canvas.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    private static void dot(Graphics2D canvas, double x, double y) {
        canvas.fill(new Ellipse2D.Double(x - 0.7, y - 0.7, 1.4, 1.4));
    }
}
