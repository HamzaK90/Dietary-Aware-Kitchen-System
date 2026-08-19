package com.cookmgmt.ui.fx;

/**
 * Plain launcher for the JavaFX application.
 *
 * <p>A class that extends {@link javafx.application.Application} cannot be used as the main class
 * of an ordinary (non-modular) jar: the JavaFX runtime refuses to start with
 * "JavaFX runtime components are missing" unless the modules are on the module path. Launching
 * from a class that does <em>not</em> extend {@code Application} sidesteps that check, which is why
 * this indirection exists - it is the difference between the GUI starting on someone else's machine
 * and not.
 *
 * <p>{@code ./mvnw javafx:run} uses {@link FxApplication} directly; the packaged jar uses this.
 */
public final class AppLauncher {

    private AppLauncher() {
    }

    public static void main(String[] args) {
        FxApplication.main(args);
    }
}
