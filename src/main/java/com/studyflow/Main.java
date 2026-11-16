package com.studyflow;

import com.studyflow.config.AppConfig;
import com.studyflow.config.AppLog;
import com.studyflow.config.StudyFlowContext;
import com.studyflow.controller.StudyFlowController;
import com.studyflow.model.Theme;
import com.studyflow.view.MainFrame;
import com.studyflow.view.ViewStyle;

import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Starts the Swing desktop application or runs a local data command. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws IOException {
        int commandIndex = args.length > 0 && "--data-dir".equals(args[0]) ? 2 : 0;
        if (args.length > commandIndex) {
            switch (args[commandIndex]) {
                case "--backup" -> {
                    backup(args, commandIndex);
                    return;
                }
                case "--restore" -> {
                    restore(args, commandIndex);
                    return;
                }
                default -> {
                    // Normal launch arguments are validated by AppConfig.
                }
            }
        }
        AppConfig config = AppConfig.fromArgs(args);
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("StudyFlow needs a graphical desktop to open its window.");
        }
        ExecutorService worker = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().name("studyflow-worker").factory());
        CompletableFuture<Void> finished = new CompletableFuture<>();
        worker.execute(() -> initialize(config, worker, finished));
        finished.join();
    }

    private static void backup(String[] args, int commandIndex) throws IOException {
        if (args.length != commandIndex + 2) {
            throw new IllegalArgumentException("Usage: StudyFlow [--data-dir <directory>] "
                    + "--backup <tasks|subjects|workspaces|notes|settings>");
        }
        AppConfig config = AppConfig.fromArgs(Arrays.copyOf(args, commandIndex));
        Path backup = StudyFlowContext.backup(config.dataDirectory(), args[commandIndex + 1]);
        System.out.println("Created backup " + backup);
    }

    private static void restore(String[] args, int restoreIndex) throws IOException {
        if (args.length != restoreIndex + 3) {
            throw new IllegalArgumentException("Usage: StudyFlow [--data-dir <directory>] "
                    + "--restore <tasks|subjects|workspaces|notes|settings> <backup-path>");
        }
        AppConfig config = AppConfig.fromArgs(Arrays.copyOf(args, restoreIndex));
        StudyFlowContext.restore(config.dataDirectory(), args[restoreIndex + 1], Path.of(args[restoreIndex + 2]));
        System.out.println("Restored " + args[restoreIndex + 1] + " in " + config.dataDirectory());
    }

    private static void initialize(AppConfig config, ExecutorService worker, CompletableFuture<Void> finished) {
        AppLog.event("application.start");
        try {
            Clock clock = Clock.systemDefaultZone();
            StudyFlowContext context = StudyFlowContext.open(config.dataDirectory(), clock);
            SwingUtilities.invokeLater(() -> openWindow(config, context, worker, finished, clock));
        } catch (IOException | RuntimeException error) {
            AppLog.failure("application.start.failed", error);
            worker.shutdown();
            SwingUtilities.invokeLater(() -> {
                showError("StudyFlow startup error", error);
                finished.completeExceptionally(error);
            });
        }
    }

    private static void openWindow(AppConfig config, StudyFlowContext context, ExecutorService worker,
                                   CompletableFuture<Void> finished, Clock clock) {
        try {
            Theme theme = context.settings().getTheme();
            ViewStyle.applyTheme(theme);
            MainFrame frame = new MainFrame(config, new StudyFlowController(context, worker, clock), theme);
            frame.addWindowListener(new WindowAdapter() {
                private boolean closing;

                @Override
                public void windowClosing(WindowEvent event) {
                    if (!closing && frame.confirmClose()) {
                        closing = true;
                        frame.setClosing();
                        finish(context, worker, finished, frame::dispose, null);
                    }
                }
            });
            frame.setVisible(true);
        } catch (RuntimeException error) {
            finish(context, worker, finished, () -> { }, error);
        }
    }

    /** Queue resource cleanup after any outstanding saves, then close the window on the EDT. */
    private static void finish(StudyFlowContext context, ExecutorService worker, CompletableFuture<Void> finished,
                               Runnable dispose, RuntimeException startupError) {
        worker.execute(() -> {
            Exception failure = startupError;
            try {
                context.close();
                AppLog.event("application.stop");
            } catch (IOException closeError) {
                if (failure == null) {
                    failure = closeError;
                } else {
                    failure.addSuppressed(closeError);
                }
            }
            Exception error = failure;
            SwingUtilities.invokeLater(() -> {
                dispose.run();
                if (error == null) {
                    finished.complete(null);
                } else {
                    showError(startupError == null ? "StudyFlow shutdown error" : "StudyFlow startup error", error);
                    finished.completeExceptionally(error);
                }
            });
        });
        worker.shutdown();
    }

    private static void showError(String title, Exception error) {
        System.err.println(title + ": " + error.getMessage());
        JOptionPane.showMessageDialog(null, error.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }
}
