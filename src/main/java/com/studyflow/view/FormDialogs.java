package com.studyflow.view;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Keyboard and cancellation behavior shared by the desktop forms. */
public final class FormDialogs {
    private FormDialogs() {
    }

    public static boolean confirmDiscard(Component owner) {
        String[] options = {"Keep editing", "Discard"};
        return JOptionPane.showOptionDialog(owner, "Discard your unsaved changes?", "Unsaved changes",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]) == 1;
    }

    public static void bindEscape(JComponent component, int scope, Runnable cancel) {
        component.getInputMap(scope).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelForm");
        component.getActionMap().put("cancelForm", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                cancel.run();
            }
        });
    }

    /** Displays validation beside a form's actions and makes the message available to assistive technology. */
    public static void feedback(JLabel label, String message) {
        String value = message == null || message.isBlank() ? "" : message;
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setText(value.isEmpty() ? " " : value);
        label.setToolTipText(value.isEmpty() ? null : value);
        label.getAccessibleContext().setAccessibleDescription(value);
    }

    /** Returns the validated result or null when a student cancels. */
    public static <T> T show(Component owner, String title, JComponent fields, JComponent firstField,
                             String submitText, BooleanSupplier dirty, Supplier<T> collect) {
        Window window = owner instanceof Window frame ? frame
                : owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(window, title, Dialog.ModalityType.APPLICATION_MODAL);
        AtomicReference<T> result = new AtomicReference<>();
        Runnable cancel = () -> {
            if (!dirty.getAsBoolean() || confirmDiscard(dialog)) {
                dialog.dispose();
            }
        };

        JLabel error = new JLabel(" ");
        error.setName("dialogValidation");
        error.setForeground(ViewStyle.color("danger"));
        JButton submit = ViewStyle.primary(new JButton(submitText));
        submit.addActionListener(event -> {
            try {
                T value = collect.get();
                if (value != null) {
                    result.set(value);
                    dialog.dispose();
                }
            } catch (IllegalArgumentException invalid) {
                feedback(error, invalid.getMessage());
            }
        });
        JButton dismiss = new JButton("Cancel");
        dismiss.addActionListener(event -> cancel.run());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        actions.add(dismiss);
        actions.add(submit);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.add(error, BorderLayout.PAGE_START);
        footer.add(actions, BorderLayout.PAGE_END);
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        body.add(fields, BorderLayout.CENTER);
        body.add(footer, BorderLayout.PAGE_END);

        dialog.setContentPane(body);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.getRootPane().setDefaultButton(submit);
        bindEscape(dialog.getRootPane(), JComponent.WHEN_IN_FOCUSED_WINDOW, cancel);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                cancel.run();
            }

            @Override
            public void windowOpened(WindowEvent event) {
                firstField.requestFocusInWindow();
            }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result.get();
    }
}
