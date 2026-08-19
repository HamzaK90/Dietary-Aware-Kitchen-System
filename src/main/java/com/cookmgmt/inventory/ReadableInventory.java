package com.cookmgmt.inventory;

import com.cookmgmt.domain.Money;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only view of ingredient stock and prices.
 *
 * <p>This is the project's Interface Segregation demonstration. There used to be one concrete
 * {@code Inventory} class, and everything that touched stock received the whole thing - including
 * {@code Chef}, which held an {@code Inventory} field it never read, and the pricing code, which
 * only ever needed to look up a price yet was handed the ability to mutate every quantity in the
 * kitchen.
 *
 * <p>Splitting the interface means a collaborator asks for exactly the capability it uses:
 * {@link com.cookmgmt.service.PricingService} takes a {@code ReadableInventory} and is structurally
 * incapable of changing stock, while only {@link com.cookmgmt.service.InventoryService} and
 * {@link com.cookmgmt.service.OrderService} take a {@link MutableInventory}.
 */
public interface ReadableInventory {

    /** Quantity below which an ingredient is reported as running low. */
    int LOW_STOCK_THRESHOLD = 5;

    /** @return units in stock, or {@code 0} if the ingredient is unknown */
    int stockOf(String ingredient);

    /** @return unit price, or {@link Money#ZERO} if the ingredient is unknown */
    Money priceOf(String ingredient);

    /** @return {@code true} if at least {@code quantity} units are available */
    boolean hasStock(String ingredient, int quantity);

    boolean isKnown(String ingredient);

    /** @return every stocked ingredient name, in insertion order */
    Set<String> ingredients();

    /** @return an immutable snapshot of ingredient to quantity */
    Map<String, Integer> stockSnapshot();

    /** @return an immutable snapshot of ingredient to unit price */
    Map<String, Money> priceSnapshot();

    /** @return ingredients with fewer than {@code threshold} units, in ascending quantity order */
    List<String> lowStock(int threshold);

    /** Convenience for {@code lowStock(LOW_STOCK_THRESHOLD)}. */
    default List<String> lowStock() {
        return lowStock(LOW_STOCK_THRESHOLD);
    }
}
