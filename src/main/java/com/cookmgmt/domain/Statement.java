package com.cookmgmt.domain;

import java.util.List;

/**
 * A customer's account across every order they have placed: what they owe, what is still cooking,
 * and what was withdrawn.
 *
 * <p>Where {@link Invoice} bills one completed order, a statement answers "what has this customer
 * spent in total". The distinction matters because the two must not be confused: only a
 * {@link OrderStatus#COMPLETED} order has a price the kitchen actually charged, frozen at the
 * moment it was served. An order that was cancelled or rejected was never cooked and must never
 * appear in a total - it is reported only as a count, so the customer can see that it was
 * deliberately excluded rather than lost.
 *
 * <p>Orders still moving through the kitchen sit in {@link #unbilled()} with an estimate. Adding
 * them to {@link #total()} would bill for food that has not been served and whose price can still
 * change if a chef rejects a substitution.
 *
 * @param customerName  who the statement is for
 * @param billed        completed orders, at the price frozen when each was served
 * @param unbilled      orders still in the kitchen, at their current estimate
 * @param total         the sum of {@link #billed()} only
 * @param excludedCount how many cancelled or rejected orders were left out
 */
public record Statement(String customerName,
                        List<Entry> billed,
                        List<Entry> unbilled,
                        Money total,
                        int excludedCount) {

    public Statement {
        billed = List.copyOf(billed);
        unbilled = List.copyOf(unbilled);
    }

    /**
     * One order's contribution to the statement.
     *
     * @param orderNumber the customer-facing order number
     * @param mealName    the dish ordered
     * @param status      the order's state, already rendered for display
     * @param amount      the frozen total for a billed order, or the running estimate for an
     *                    unbilled one
     */
    public record Entry(int orderNumber, String mealName, String status, Money amount) {
    }

    /** @return {@code true} if this customer has no orders worth showing at all */
    public boolean isEmpty() {
        return billed.isEmpty() && unbilled.isEmpty();
    }

    /** @return how many completed orders make up {@link #total()} */
    public int billedCount() {
        return billed.size();
    }

    /** Plain-text rendering, used by the console UI and by the GUI's statement dialog. */
    public String format() {
        String newLine = System.lineSeparator();
        StringBuilder out = new StringBuilder();

        out.append("Statement for ").append(customerName).append(newLine)
                .append("-".repeat(46)).append(newLine);

        if (billed.isEmpty()) {
            out.append("  No completed orders yet.").append(newLine);
        } else {
            for (Entry entry : billed) {
                out.append(String.format("  #%-4d %-24s %10s%s",
                        entry.orderNumber(), entry.mealName(), entry.amount(), newLine));
            }
        }

        out.append("-".repeat(46)).append(newLine)
                .append(String.format("  %-29s %10s%s",
                        billedCount() + (billedCount() == 1 ? " completed order" : " completed orders"),
                        total, newLine));

        if (!unbilled.isEmpty()) {
            out.append(newLine).append("Not yet billed:").append(newLine);
            for (Entry entry : unbilled) {
                out.append(String.format("  #%-4d %-24s %10s  (%s)%s",
                        entry.orderNumber(), entry.mealName(), entry.amount(),
                        entry.status(), newLine));
            }
        }

        if (excludedCount > 0) {
            out.append(newLine)
                    .append(excludedCount == 1
                            ? "1 cancelled or rejected order excluded from the total."
                            : excludedCount + " cancelled or rejected orders excluded from the total.")
                    .append(newLine);
        }

        return out.toString();
    }
}
