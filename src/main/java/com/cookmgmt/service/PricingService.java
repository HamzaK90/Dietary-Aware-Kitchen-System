package com.cookmgmt.service;

import com.cookmgmt.domain.Invoice;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.Order;
import com.cookmgmt.inventory.ReadableInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The single place a price is calculated.
 *
 * <p>The same arithmetic used to exist twice: {@code Meal.calculateBasePrice} and the private
 * {@code Order.calculateFinalPrice}. The former was dead code that nothing ever called, so the two
 * could - and did - drift apart without anything noticing.
 *
 * <p>Takes a {@link ReadableInventory}, not a mutable one: pricing reads prices and must not be
 * able to change stock.
 */
public class PricingService {

    private final ReadableInventory inventory;

    public PricingService(ReadableInventory inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    /**
     * Price of a recipe: the sum of {@code quantity x unit price} over its ingredients.
     *
     * <p>An ingredient the inventory does not know prices at {@link Money#ZERO} rather than
     * throwing, matching the old behaviour so a newly invented ingredient does not break checkout.
     */
    public Money priceOf(Map<String, Integer> recipe) {
        Money total = Money.ZERO;
        for (Map.Entry<String, Integer> entry : recipe.entrySet()) {
            total = total.plus(inventory.priceOf(entry.getKey()).times(entry.getValue()));
        }
        return total;
    }

    /** Menu price of a meal, before any substitution. */
    public Money basePriceOf(Meal meal) {
        return priceOf(meal.getIngredients());
    }

    /**
     * What an order will cost as it currently stands, with its substitutions applied.
     *
     * <p>Lets the interfaces show a running total on a pending order. Previously the price was only
     * computed inside {@code completeOrder}, so an order that had not been served yet reported
     * {@code 0.0} and the console printed a confident {@code Total: $0.00}.
     */
    public Money quote(Order order) {
        return priceOf(order.effectiveRecipe());
    }

    /**
     * Builds the itemised bill for an order.
     *
     * <p>Uses the order's frozen final price when it has one, so a reprice in inventory after
     * service cannot retroactively change an issued invoice.
     */
    public Invoice invoiceFor(Order order) {
        Map<String, String> substitutions = order.getSubstitutions();
        List<String> replacements = substitutions.values().stream().toList();

        List<Invoice.Line> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : order.effectiveRecipe().entrySet()) {
            String ingredient = entry.getKey();
            int quantity = entry.getValue();
            Money unitPrice = inventory.priceOf(ingredient);
            lines.add(new Invoice.Line(
                    ingredient,
                    replacements.contains(ingredient),
                    quantity,
                    unitPrice,
                    unitPrice.times(quantity)));
        }

        Money total = order.getFinalPrice().orElseGet(() -> quote(order));
        return new Invoice(
                order.getOrderNumber(),
                order.getCustomer().getName(),
                order.getMeal().getName(),
                lines,
                total);
    }
}
