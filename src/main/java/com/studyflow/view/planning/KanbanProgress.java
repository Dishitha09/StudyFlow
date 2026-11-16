package com.studyflow.view.planning;

import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.text.NumberFormat;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** A proportional view of the three status lanes for the current tasks. */
final class KanbanProgress extends JPanel {
    private final JLabel total = ViewStyle.muted("");
    private final JLabel overdue = ViewStyle.muted("");
    private final JLabel completion = new JLabel();
    private final DistributionBar distribution = new DistributionBar();

    KanbanProgress() {
        super(new BorderLayout());
        setOpaque(false);
        setName("kanbanProgress");
        JPanel card = ViewStyle.card(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(12, 15, 13, 15));
        add(card);

        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);
        top.add(ViewStyle.subheading("Board progress"), BorderLayout.CENTER);
        completion.setFont(completion.getFont().deriveFont(Font.BOLD));
        completion.setName("kanbanCompletion");
        top.add(completion, BorderLayout.LINE_END);
        card.add(top, BorderLayout.PAGE_START);

        distribution.setPreferredSize(new Dimension(0, 9));
        distribution.setName("kanbanDistribution");
        card.add(distribution, BorderLayout.CENTER);

        JPanel metrics = new JPanel(new BorderLayout(10, 0));
        metrics.setOpaque(false);
        total.setName("kanbanTotal");
        overdue.setName("kanbanOverdue");
        overdue.setIcon(KanbanTone.URGENT.dot());
        overdue.setIconTextGap(6);
        metrics.add(total, BorderLayout.LINE_START);
        metrics.add(overdue, BorderLayout.LINE_END);
        card.add(metrics, BorderLayout.PAGE_END);
    }

    void showCounts(int toDo, int active, int completed, long overdueCount) {
        int count = toDo + active + completed;
        total.setText(count == 0 ? "No tasks yet"
                : completed + " of " + count + (count == 1 ? " task completed" : " tasks completed"));
        String percent = NumberFormat.getPercentInstance().format(count == 0 ? 0 : (double) completed / count);
        completion.setText(percent + " complete");
        overdue.setText(overdueCount + " overdue");
        overdue.setIcon(overdueCount > 0 ? KanbanTone.URGENT.dot() : null);
        distribution.setCounts(toDo, active, completed);
        distribution.getAccessibleContext().setAccessibleName(
                toDo + " to do, " + active + " in progress, " + completed + " completed");
    }

    private static final class DistributionBar extends JPanel {
        private int toDo;
        private int active;
        private int total;

        DistributionBar() {
            setOpaque(false);
        }

        void setCounts(int toDo, int active, int completed) {
            this.toDo = toDo;
            this.active = active;
            total = toDo + active + completed;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth();
                int height = getHeight();
                RoundRectangle2D track = new RoundRectangle2D.Float(0, 0, width, height, height, height);
                canvas.setColor(total == 0 ? ViewStyle.color("border") : KanbanTone.DONE.accent());
                canvas.fill(track);
                if (total > 0) {
                    canvas.clip(track);
                    int first = (int) Math.round((double) toDo * width / total);
                    int second = (int) Math.round(((double) toDo + active) * width / total);
                    canvas.setColor(KanbanTone.TODO.accent());
                    canvas.fillRect(0, 0, first, height);
                    canvas.setColor(KanbanTone.ACTIVE.accent());
                    canvas.fillRect(first, 0, second - first, height);
                }
            } finally {
                canvas.dispose();
            }
        }
    }
}
