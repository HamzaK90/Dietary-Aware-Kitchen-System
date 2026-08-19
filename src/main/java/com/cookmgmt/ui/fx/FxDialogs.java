package com.cookmgmt.ui.fx;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;

import java.util.Optional;

/**
 * The dialogs the GUI uses to talk to the user.
 *
 * <p>{@link #confirm} is the JavaFX counterpart of the console's approve/reject prompt. Both exist
 * because the decision was moved out of {@code Chef.reviewOrder}, which used to open a
 * {@link java.util.Scanner} on {@code System.in} from inside a domain class - something a GUI could
 * never have satisfied.
 */
final class FxDialogs {

    private FxDialogs() {
    }

    static void info(String title, String message) {
        show(Alert.AlertType.INFORMATION, title, message);
    }

    static void error(String title, String message) {
        show(Alert.AlertType.ERROR, title, message);
    }

    static void warn(String title, String message) {
        show(Alert.AlertType.WARNING, title, message);
    }

    /** @return {@code true} if the user chose OK */
    static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(title);
        Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == ButtonType.OK;
    }

    /** Shows monospaced text, such as a rendered invoice, in a scrollable area. */
    static void text(String title, String body) {
        TextArea area = new TextArea(body);
        area.setEditable(false);
        area.getStyleClass().add("detail-pane");
        area.setPrefRowCount(Math.min(20, body.split("\n").length + 2));

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.getDialogPane().setContent(area);
        alert.getDialogPane().setPrefWidth(520);
        alert.showAndWait();
    }

    private static void show(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message == null ? "" : message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }
}
