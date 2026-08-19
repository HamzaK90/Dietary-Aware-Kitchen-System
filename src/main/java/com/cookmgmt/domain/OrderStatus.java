package com.cookmgmt.domain;

/**
 * Lifecycle state of an {@link Order}.
 *
 * <p>Three of these values ({@code NEEDS_APPROVAL}, {@code APPROVED}, {@code IN_PROGRESS}) existed
 * but were never assigned anywhere, while status was writable from anywhere through a public
 * setter. The legal transitions are now declared here, next to the states themselves, and
 * {@link Order} refuses anything else.
 *
 * <pre>
 *   PENDING ────────────────► IN_PROGRESS ──► COMPLETED
 *      │                          ▲
 *      ├──► NEEDS_APPROVAL ──► APPROVED
 *      │            └────────► REJECTED
 *      └──► CANCELLED
 * </pre>
 */
public enum OrderStatus {

    /** Placed and ready to cook; nothing needs a chef's sign-off. */
    PENDING,

    /** Placed with ingredient substitutions that a chef must review. */
    NEEDS_APPROVAL,

    /** A chef accepted the substitutions. */
    APPROVED,

    /** A chef has started cooking. */
    IN_PROGRESS,

    /** Served and invoiced. Terminal. */
    COMPLETED,

    /** A chef refused the substitutions. Terminal. */
    REJECTED,

    /** Withdrawn by the customer before cooking began. Terminal. */
    CANCELLED;

    /** @return {@code true} if this state can legally be followed by {@code target} */
    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == NEEDS_APPROVAL || target == IN_PROGRESS || target == CANCELLED;
            case NEEDS_APPROVAL -> target == APPROVED || target == REJECTED || target == CANCELLED;
            case APPROVED -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED;
            case COMPLETED, REJECTED, CANCELLED -> false;
        };
    }

    /** @return {@code true} if no further transition is possible */
    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED;
    }

    /** @return {@code true} while the order still occupies a chef and holds reserved stock */
    public boolean isActive() {
        return !isTerminal();
    }

    /** Label shown to customers, who should not have to read enum constants. */
    public String displayName() {
        return switch (this) {
            case PENDING -> "Pending";
            case NEEDS_APPROVAL -> "Awaiting chef approval";
            case APPROVED -> "Approved";
            case IN_PROGRESS -> "Being cooked";
            case COMPLETED -> "Ready for pickup";
            case REJECTED -> "Rejected by chef";
            case CANCELLED -> "Cancelled";
        };
    }
}
