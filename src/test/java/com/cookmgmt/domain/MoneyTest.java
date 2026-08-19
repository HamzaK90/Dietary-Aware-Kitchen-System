package com.cookmgmt.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Money")
class MoneyTest {

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("adds amounts exactly")
        void addsExactly() {
            assertEquals(Money.of("5.75"), Money.of("3.50").plus(Money.of("2.25")));
        }

        @Test
        @DisplayName("multiplies by a whole quantity")
        void multipliesByQuantity() {
            assertEquals(Money.of("4.50"), Money.of("1.50").times(3));
        }

        @Test
        @DisplayName("subtracts amounts")
        void subtracts() {
            assertEquals(Money.of("1.25"), Money.of("3.50").minus(Money.of("2.25")));
        }
    }

    @Test
    @DisplayName("does not accumulate binary floating point error")
    void doesNotAccumulateFloatingPointError() {
        // The original code totalled invoices as double. Adding 0.10 ten times that way yields
        // 0.9999999999999999, so an invoice total could disagree with the sum of its own lines.
        double naive = 0.0;
        for (int i = 0; i < 10; i++) {
            naive += 0.10;
        }
        assertNotEquals(1.0, naive, "precondition: double addition is inexact here");

        Money accurate = Money.ZERO;
        for (int i = 0; i < 10; i++) {
            accurate = accurate.plus(Money.of("0.10"));
        }
        assertEquals(Money.of("1.00"), accurate);
    }

    @Test
    @DisplayName("rounds half up to two decimal places")
    void roundsHalfUp() {
        assertEquals(Money.of("0.13"), Money.of(new BigDecimal("0.125")));
        assertEquals(Money.of("0.12"), Money.of(new BigDecimal("0.124")));
    }

    @Test
    @DisplayName("builds from a double without the new BigDecimal(double) expansion")
    void buildsFromDoubleSafely() {
        assertEquals("0.10", Money.of(0.1).toBigDecimal().toPlainString());
    }

    @Test
    @DisplayName("compares by amount, not identity")
    void comparesByValue() {
        assertEquals(Money.of("2.00"), Money.of(2.0));
        assertTrue(Money.of("3.00").compareTo(Money.of("2.00")) > 0);
    }

    @Test
    @DisplayName("recognises zero and negative amounts")
    void recognisesZeroAndNegative() {
        assertTrue(Money.ZERO.isZero());
        assertFalse(Money.of("0.01").isZero());
        assertTrue(Money.ZERO.minus(Money.of("1.00")).isNegative());
    }

    @Test
    @DisplayName("renders with a currency symbol and two decimals")
    void rendersForDisplay() {
        assertEquals("$7.50", Money.of("7.5").toString());
    }
}
