package com.cookmgmt.ui.fx;

/**
 * A screen that can rebuild itself from the services.
 *
 * <p>Exists so {@link ViewRefresher} can tell every screen that shared state changed without
 * knowing what any of them are. A new tab becomes part of the refresh cycle by implementing this
 * and registering itself - no existing view is touched.
 */
interface Refreshable {

    /** Re-reads everything this screen displays from the service layer. */
    void refreshAll();
}
