package com.cookmgmt.domain;

import java.util.List;

/**
 * An itemised bill for a completed order.
 *
 * <p>Invoicing used to be a single {@code String.format} on {@link Order} that printed only a
 * grand total, so a customer could not see how it was reached and nothing could verify that the
 * total matched its parts. The invoice is now a value object carrying its line items, and
 * {@link #format()} is only one way of rendering it - the GUI renders the same data as a table.
 *
 * @param orderNumber  the customer-facing order number
 * @param customerName who the invoice is addressed to
 * @param mealName     the dish ordered
 * @param lines        one entry per ingredient actually used
 * @param total        the sum of every line, frozen at completion
 */
public record Invoice(int orderNumber,
                      String customerName,
                      String mealName,
                      List<Line> lines,
                      Money total) {

    public Invoice {
        lines = List.copyOf(lines);
    }

    /**
     * A single priced ingredient on the invoice.
     *
     * @param ingredient   the ingredient actually used, after any substitution
     * @param substituted  whether it replaced a different ingredient from the original recipe
     * @param quantity     units consumed
     * @param unitPrice    price of one unit
     * @param lineTotal    {@code unitPrice * quantity}
     */
    public record Line(String ingredient,
                       boolean substituted,
                       int quantity,
                       Money unitPrice,
                       Money lineTotal) {
    }

    /** Plain-text rendering, used by the console UI. */
    public String format() {
        StringBuilder out = new StringBuilder();
        out.append("Invoice #").append(orderNumber)
                .append(" for ").append(mealName)
                .append(System.lineSeparator())
                .append("Customer: ").append(customerName)
                .append(System.lineSeparator());
        for (Line line : lines) {
            out.append(String.format("  %-16s %3d x %-7s %8s%s",
                            line.ingredient() + (line.substituted() ? " *" : ""),
                            line.quantity(),
                            line.unitPrice(),
                            line.lineTotal(),
                            System.lineSeparator()));
        }
        if (lines.stream().anyMatch(Line::substituted)) {
            out.append("  * substituted ingredient").append(System.lineSeparator());
        }
        out.append("Total: ").append(total);
        return out.toString();
    }
}
