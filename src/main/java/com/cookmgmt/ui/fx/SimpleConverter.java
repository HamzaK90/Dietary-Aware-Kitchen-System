package com.cookmgmt.ui.fx;

import javafx.util.StringConverter;

import java.util.function.Function;

/**
 * A one-way {@link StringConverter} for combo boxes that display domain objects.
 *
 * <p>Only {@link #toString} is meaningful; the pickers here are selection-only, never editable, so
 * there is no text to convert back into an object.
 *
 * @param <T> the type shown in the combo box
 */
class SimpleConverter<T> extends StringConverter<T> {

    private final Function<T, String> label;

    SimpleConverter(Function<T, String> label) {
        this.label = label;
    }

    @Override
    public String toString(T value) {
        return value == null ? "" : label.apply(value);
    }

    @Override
    public T fromString(String text) {
        throw new UnsupportedOperationException("These pickers are selection-only");
    }
}
