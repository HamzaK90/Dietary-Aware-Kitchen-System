package com.cookmgmt.ui.fx;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;

/**
 * Helpers for filling read-only {@link javafx.scene.control.TableView} cells.
 *
 * <p>The tables here display values the services return rather than binding to mutable domain
 * properties, so a plain wrapper is all that is needed - and it keeps JavaFX property types out of
 * the domain classes, which would otherwise make them depend on the UI toolkit.
 */
final class FxBindings {

    private FxBindings() {
    }

    static ObservableValue<String> of(String value) {
        return new SimpleStringProperty(value == null ? "" : value);
    }

    static ObservableValue<String> of(Object value) {
        return new SimpleStringProperty(value == null ? "" : String.valueOf(value));
    }
}
