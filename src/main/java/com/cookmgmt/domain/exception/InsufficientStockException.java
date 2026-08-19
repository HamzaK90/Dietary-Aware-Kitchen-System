package com.cookmgmt.domain.exception;

/**
 * Raised when an operation would take more of an ingredient than the inventory holds.
 *
 * <p>The old {@code Inventory.updateStock} applied any delta unconditionally, so stock could fall
 * below zero and the shortfall was never reported to the caller.
 */
public class InsufficientStockException extends RuntimeException {

    private final String ingredient;
    private final int requested;
    private final int available;

    public InsufficientStockException(String ingredient, int requested, int available) {
        super("Insufficient stock for \"" + ingredient + "\": requested " + requested
                + ", available " + available);
        this.ingredient = ingredient;
        this.requested = requested;
        this.available = available;
    }

    public String getIngredient() {
        return ingredient;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
