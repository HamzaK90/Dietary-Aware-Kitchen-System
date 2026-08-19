package com.cookmgmt.inventory;

import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.exception.InsufficientStockException;
import com.cookmgmt.support.Text;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * In-memory implementation of the inventory. The project intentionally has no database, so this is
 * the only storage there is.
 *
 * <p>Ingredient names are normalised through {@link Text#normalize(String)}, so {@code "Tofu"} and
 * {@code "tofu"} are one entry rather than two - previously they were two, which is how a meal
 * could report insufficient stock for an ingredient the kitchen had plenty of.
 */
public class InMemoryInventory implements MutableInventory {

    private final Map<String, Integer> stock = new LinkedHashMap<>();
    private final Map<String, Money> prices = new LinkedHashMap<>();

    @Override
    public void addIngredient(String ingredient, int quantity, Money unitPrice) {
        String key = requireIngredient(ingredient);
        requireNotNegative(quantity, "Quantity");
        requireValidPrice(unitPrice);
        stock.merge(key, quantity, Integer::sum);
        prices.put(key, unitPrice);
    }

    @Override
    public void restock(String ingredient, int quantity) {
        String key = requireIngredient(ingredient);
        requireNotNegative(quantity, "Quantity");
        stock.merge(key, quantity, Integer::sum);
        prices.putIfAbsent(key, Money.ZERO);
    }

    @Override
    public void reprice(String ingredient, Money unitPrice) {
        String key = requireKnown(ingredient);
        requireValidPrice(unitPrice);
        prices.put(key, unitPrice);
    }

    @Override
    public void consume(String ingredient, int quantity) {
        String key = requireIngredient(ingredient);
        requireNotNegative(quantity, "Quantity");
        int available = stock.getOrDefault(key, 0);
        if (available < quantity) {
            throw new InsufficientStockException(key, quantity, available);
        }
        stock.put(key, available - quantity);
    }

    @Override
    public void release(String ingredient, int quantity) {
        String key = requireIngredient(ingredient);
        requireNotNegative(quantity, "Quantity");
        stock.merge(key, quantity, Integer::sum);
    }

    @Override
    public boolean remove(String ingredient) {
        String key = Text.normalize(ingredient);
        prices.remove(key);
        return stock.remove(key) != null;
    }

    @Override
    public int stockOf(String ingredient) {
        return stock.getOrDefault(Text.normalize(ingredient), 0);
    }

    @Override
    public Money priceOf(String ingredient) {
        return prices.getOrDefault(Text.normalize(ingredient), Money.ZERO);
    }

    @Override
    public boolean hasStock(String ingredient, int quantity) {
        return stockOf(ingredient) >= quantity;
    }

    @Override
    public boolean isKnown(String ingredient) {
        return stock.containsKey(Text.normalize(ingredient));
    }

    @Override
    public Set<String> ingredients() {
        return Set.copyOf(stock.keySet());
    }

    @Override
    public Map<String, Integer> stockSnapshot() {
        return Map.copyOf(stock);
    }

    @Override
    public Map<String, Money> priceSnapshot() {
        return Map.copyOf(prices);
    }

    @Override
    public List<String> lowStock(int threshold) {
        return stock.entrySet().stream()
                .filter(entry -> entry.getValue() < threshold)
                // Sorted by quantity then name so the report is deterministic rather than
                // dependent on hash order, which made the old output vary between runs.
                .sorted(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static String requireIngredient(String ingredient) {
        String key = Text.normalize(ingredient);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Ingredient name must not be blank");
        }
        return key;
    }

    private String requireKnown(String ingredient) {
        String key = requireIngredient(ingredient);
        if (!stock.containsKey(key)) {
            throw new IllegalArgumentException("Unknown ingredient \"" + key + "\"");
        }
        return key;
    }

    private static void requireNotNegative(int quantity, String field) {
        if (quantity < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }

    private static void requireValidPrice(Money unitPrice) {
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (unitPrice.isNegative()) {
            throw new IllegalArgumentException("Price must not be negative");
        }
    }

    /** Sorted comparator used by the admin views. */
    public static Comparator<String> byName() {
        return Comparator.naturalOrder();
    }
}
