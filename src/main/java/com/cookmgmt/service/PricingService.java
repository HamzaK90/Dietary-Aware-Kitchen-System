package com.cookmgmt.service;

import com.cookmgmt.domain.Invoice;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.OrderStatus;
import com.cookmgmt.domain.Statement;
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

    /**
     * Sums a customer's orders into a single account {@link Statement}.
     *
     * <p>Only {@link OrderStatus#COMPLETED} orders reach the total, and they contribute the price
     * frozen when they were served rather than a fresh calculation - so restocking an ingredient at
     * a new price cannot retroactively change what a customer already paid. Orders still in the
     * kitchen are listed separately at their current estimate, and cancelled or rejected orders are
     * counted but never priced: nothing was cooked, so there is nothing to charge for.
     *
     * <p>Takes the orders as an argument rather than reaching for a repository, which keeps pricing
     * a pure calculation over data it is handed and lets a test pin down every case without
     * building a repository first.
     *
     * @param customerName who the statement is addressed to
     * @param orders       every order belonging to that customer, in any state
     */
    public Statement statementFor(String customerName, List<Order> orders) {
        Objects.requireNonNull(customerName, "customerName");
        Objects.requireNonNull(orders, "orders");

        List<Statement.Entry> billed = new ArrayList<>();
        List<Statement.Entry> unbilled = new ArrayList<>();
        Money total = Money.ZERO;
        int excluded = 0;

        for (Order order : orders) {
            if (order.isCompleted()) {
                // orElseGet rather than orElseThrow: a completed order always has a frozen price,
                // but falling back to a quote is harmless and avoids an exception in the UI if that
                // invariant ever breaks.
                Money amount = order.getFinalPrice().orElseGet(() -> quote(order));
                billed.add(entryFor(order, amount));
                total = total.plus(amount);
            } else if (order.getStatus().isActive()) {
                unbilled.add(entryFor(order, quote(order)));
            } else {
                excluded++;
            }
        }

        return new Statement(customerName, billed, unbilled, total, excluded);
    }

    private static Statement.Entry entryFor(Order order, Money amount) {
        return new Statement.Entry(
                order.getOrderNumber(),
                order.getMeal().getName(),
                order.getStatus().displayName(),
                amount);
    }
}
