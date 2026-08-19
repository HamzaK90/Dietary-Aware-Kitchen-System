package com.cookmgmt.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An immutable currency amount.
 *
 * <p>Prices and invoice totals used to be {@code double}. Binary floating point cannot represent
 * values like {@code 0.1} exactly, so repeated addition across a recipe accumulates error and
 * invoice totals can disagree with the sum of their own line items. All money is now a
 * {@link BigDecimal} scaled to two decimals with {@link RoundingMode#HALF_UP}.
 *
 * <p>This is a value object: equality is by amount, never by identity.
 */
public final class Money implements Comparable<Money> {

    /** Number of decimal places every amount is normalised to. */
    public static final int SCALE = 2;

    public static final Money ZERO = Money.of(BigDecimal.ZERO);

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        this.amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount) {
        return new Money(Objects.requireNonNull(amount, "amount"));
    }

    public static Money of(double amount) {
        // BigDecimal.valueOf goes via Double.toString, avoiding the surprising
        // new BigDecimal(0.1) == 0.1000000000000000055511151231257827... expansion.
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    /** Multiplies by a whole quantity, as when pricing {@code quantity} units of an ingredient. */
    public Money times(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public BigDecimal toBigDecimal() {
        return amount;
    }

    public double toDouble() {
        return amount.doubleValue();
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Money money && amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }

    /** Renders the amount for display, for example {@code $12.50}. */
    @Override
    public String toString() {
        return "$" + amount.toPlainString();
    }
}
