package com.cookmgmt.inventory;

import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.exception.InsufficientStockException;

/**
 * Write access to ingredient stock, on top of the {@link ReadableInventory} view.
 *
 * <p>The original class offered a single {@code addIngredient(name, quantity, price)} that both
 * created an ingredient and topped one up, silently overwriting the stored price every time it was
 * called, plus an {@code updateStock(name, delta)} that accepted negative deltas without ever
 * checking whether the stock existed to remove. Stock could therefore go negative unnoticed, and a
 * routine restock could quietly reprice the menu.
 *
 * <p>Those responsibilities are now four explicit operations.
 */
public interface MutableInventory extends ReadableInventory {

    /**
     * Registers a new ingredient, or tops up an existing one and updates its price.
     *
     * @throws IllegalArgumentException if the quantity is negative or the price is negative
     */
    void addIngredient(String ingredient, int quantity, Money unitPrice);

    /**
     * Adds stock without touching the price.
     *
     * @throws IllegalArgumentException if {@code quantity} is negative
     */
    void restock(String ingredient, int quantity);

    /**
     * Changes the unit price without touching stock.
     *
     * @throws IllegalArgumentException if the ingredient is unknown or the price is negative
     */
    void reprice(String ingredient, Money unitPrice);

    /**
     * Removes stock, as when an order reserves its ingredients.
     *
     * @throws InsufficientStockException if fewer than {@code quantity} units are available.
     *         The old code applied the change anyway and let stock fall below zero.
     */
    void consume(String ingredient, int quantity);

    /**
     * Returns previously consumed stock, as when a chef rejects an order or a customer cancels.
     *
     * <p>Nothing in the original system did this, so ingredients reserved by a rejected order were
     * lost from inventory permanently.
     */
    void release(String ingredient, int quantity);

    /** @return {@code true} if the ingredient existed and was removed */
    boolean remove(String ingredient);
}
