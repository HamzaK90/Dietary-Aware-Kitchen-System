package com.cookmgmt.domain;

/**
 * A chef who prepares orders.
 *
 * <p>Deliberately small. The previous version carried an {@code Inventory} field it never read and
 * exposed its own live {@code Queue<Order>} to callers, letting anyone mutate the work queue
 * without going through any business rule. It also opened a {@link java.util.Scanner} on
 * {@code System.in} to ask for approval, which put console I/O inside the domain and forced a
 * {@code setTestAutoApprove} flag to exist in production code purely so tests could run.
 *
 * <p>Assignment now lives on the order itself ({@link Order#getAssignedChefId()}), so a chef's
 * queue is derived from the order repository rather than duplicated here, and the approve/reject
 * decision is made by the user interface and handed to
 * {@link com.cookmgmt.service.KitchenService}.
 */
public class Chef extends Person {

    public Chef(String name, String email) {
        super(name, email);
    }
}
