package com.cookmgmt.ui.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Keeps every screen in step after a change to shared state.
 *
 * <p>The tabs all read the same in-memory services, so an action on one changes what the others
 * should be showing: cancelling an order returns its ingredients to stock, drops it out of the
 * chef's queue and changes its row in the admin report. Previously each view only refreshed itself,
 * and {@code FxApplication} built the views inline without keeping the references, so no view could
 * reach another even in principle - {@code CustomerView.refreshAll()} existed, documented as being
 * called by the other tabs, and was never called by anything.
 *
 * <p>The fix is deliberately not "let the views call each other", which would couple all three to
 * one another and force every new screen into the existing ones. Views publish here and this class
 * fans the notification out, so each view depends only on {@link Refreshable} - the observer
 * arrangement that lets a fourth tab join without editing the other three.
 *
 * <p>Confined to the JavaFX application thread: every caller is an event handler, so no
 * synchronisation is needed and none is implied.
 */
final class ViewRefresher {

    private final List<Refreshable> views = new ArrayList<>();

    /**
     * Guards against re-entry. Refreshing a screen calls {@code TableView.setItems}, which fires
     * the selection listeners, which run handlers that may ask for another refresh. Without this
     * flag a single cancellation could recurse until the stack overflowed.
     */
    private boolean refreshing;

    void register(Refreshable view) {
        views.add(Objects.requireNonNull(view, "view"));
    }

    /** Rebuilds every registered screen. Safe to call from inside a refresh; the nested call is ignored. */
    void refreshAll() {
        if (refreshing) {
            return;
        }
        refreshing = true;
        try {
            for (Refreshable view : views) {
                view.refreshAll();
            }
        } finally {
            refreshing = false;
        }
    }
}
