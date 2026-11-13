package com.studyflow;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.studyflow.config.AppConfig;
import com.studyflow.view.MainFrame;

import java.awt.GraphicsEnvironment;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Configures and starts the Swing desktop application. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.fromArgs(args);
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("StudyFlow needs a graphical desktop to open its window.");
        }
        SwingUtilities.invokeLater(() -> openWindow(config));
    }

    private static void openWindow(AppConfig config) {
        try {
            boolean installed = config.darkByDefault() ? FlatDarkLaf.setup() : FlatLightLaf.setup();
            if (!installed) {
                throw new IllegalStateException("Could not initialize the StudyFlow theme.");
            }
            new MainFrame(config).setVisible(true);
        } catch (RuntimeException error) {
            error.printStackTrace(System.err);
            JOptionPane.showMessageDialog(null, error.getMessage(), "StudyFlow startup error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
