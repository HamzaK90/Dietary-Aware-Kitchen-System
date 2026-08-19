package com.cookmgmt.service;

import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.exception.InsufficientStockException;
import com.cookmgmt.inventory.MutableInventory;
import com.cookmgmt.inventory.ReadableInventory;
import com.cookmgmt.notify.Notifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stock operations, including the all-or-nothing reservation an order depends on.
 *
 * <p>The most consequential defect in the original system lived here by omission: the console's
 * order flow built an {@code Order} directly and never touched inventory, so <em>stock was never
 * decremented by placing an order</em>. Quantities only ever went up, "Low Stock Alerts" could
 * never fire, and the unused {@code Customer.placeOrder} method - the one path that did adjust
 * stock - sat dead alongside it.
 *
 * <p>There is now one reservation path, used by both user interfaces.
 */
public class InventoryService {

    private final MutableInventory inventory;
    private final Notifier notifier;

    public InventoryService(MutableInventory inventory, Notifier notifier) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    // ---------------------------------------------------------------- stock

    /**
     * Takes every ingredient in a recipe out of stock, or takes none of them.
     *
     * <p>Every quantity is checked before any is removed. Doing it in one pass would leave the
     * inventory half-deducted when the last ingredient turned out to be short, with no record of
     * what to put back.
     *
     * @throws InsufficientStockException naming the first ingredient that is short
     */
    public void reserve(Map<String, Integer> recipe) {
        for (Map.Entry<String, Integer> entry : recipe.entrySet()) {
            int available = inventory.stockOf(entry.getKey());
            if (available < entry.getValue()) {
                throw new InsufficientStockException(entry.getKey(), entry.getValue(), available);
            }
        }
        recipe.forEach(inventory::consume);
        warnAboutLowStock();
    }

    /** Returns a reservation to stock, as when an order is rejected or cancelled. */
    public void release(Map<String, Integer> recipe) {
        recipe.forEach(inventory::release);
    }

    /** @return {@code true} if every ingredient in the recipe is available in the quantity needed */
    public boolean canFulfil(Map<String, Integer> recipe) {
        return shortages(recipe).isEmpty();
    }

    /** @return the ingredients that are short, with how many are missing */
    public List<Shortage> shortages(Map<String, Integer> recipe) {
        List<Shortage> shortages = new ArrayList<>();
        recipe.forEach((ingredient, required) -> {
            int available = inventory.stockOf(ingredient);
            if (available < required) {
                shortages.add(new Shortage(ingredient, required, available));
            }
        });
        return shortages;
    }

    // ------------------------------------------------------------ management

    public void addIngredient(String ingredient, int quantity, Money unitPrice) {
        inventory.addIngredient(ingredient, quantity, unitPrice);
        warnAboutLowStock();
    }

    public void restock(String ingredient, int quantity) {
        inventory.restock(ingredient, quantity);
    }

    public void reprice(String ingredient, Money unitPrice) {
        inventory.reprice(ingredient, unitPrice);
    }

    public boolean remove(String ingredient) {
        return inventory.remove(ingredient);
    }

    // ------------------------------------------------------------- reporting

    /** @return ingredients below {@link ReadableInventory#LOW_STOCK_THRESHOLD} */
    public List<String> lowStock() {
        return inventory.lowStock();
    }

    public List<String> lowStock(int threshold) {
        return inventory.lowStock(threshold);
    }

    /**
     * Raises a restock warning for each ingredient running low.
     *
     * <p>Called after any operation that reduces stock, so the kitchen is told at the moment it
     * happens rather than only if someone remembers to open the stock screen.
     */
    public void warnAboutLowStock() {
        for (String ingredient : inventory.lowStock()) {
            notifier.broadcast("Low stock: \"" + ingredient + "\" is down to "
                    + inventory.stockOf(ingredient) + " units - consider restocking");
        }
    }

    /** Read-only view, for pricing and for the screens that only display stock. */
    public ReadableInventory inventory() {
        return inventory;
    }

    /**
     * An ingredient that cannot currently be supplied in the quantity a recipe needs.
     *
     * @param ingredient the ingredient that is short
     * @param required   how many units the recipe asks for
     * @param available  how many units are in stock
     */
    public record Shortage(String ingredient, int required, int available) {

        public int missing() {
            return required - available;
        }

        @Override
        public String toString() {
            return ingredient + " (need " + required + ", have " + available + ")";
        }
    }
}
