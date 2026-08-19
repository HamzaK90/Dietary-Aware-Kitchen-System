package com.cookmgmt.ui.fx;

import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Objects;

/**
 * The application icon, loaded once and applied to every window the GUI opens.
 *
 * <p>A JavaFX dialog is a window in its own right, with its own icon list - setting the icon on the
 * main {@link Stage} does nothing for the alerts, confirmations and invoice panes that open on top
 * of it, which would otherwise show the default Java cup. Every window therefore goes through here.
 *
 * <p>Loaded from the classpath rather than a file path so it keeps working from a packaged jar, and
 * held in a static field so the image is decoded once instead of on every dialog.
 */
final class AppIcon {

    private static final String RESOURCE = "/fx/icon.png";

    private static Image image;

    private AppIcon() {
    }

    static Image image() {
        if (image == null) {
            image = new Image(Objects.requireNonNull(
                    AppIcon.class.getResourceAsStream(RESOURCE), RESOURCE + " is missing"));
        }
        return image;
    }

    /** Puts the icon on a stage, unless it already carries one. */
    static void applyTo(Stage stage) {
        if (stage != null && stage.getIcons().isEmpty()) {
            stage.getIcons().add(image());
        }
    }

    /**
     * Puts the icon on a dialog's own window.
     *
     * <p>Silently does nothing if the dialog has not been given a window yet. That is a display
     * detail, and failing to decorate a dialog is never a reason to stop the action it is reporting.
     */
    static void applyTo(Dialog<?> dialog) {
        if (dialog.getDialogPane().getScene() == null) {
            return;
        }
        Window window = dialog.getDialogPane().getScene().getWindow();
        if (window instanceof Stage stage) {
            applyTo(stage);
        }
    }
}
