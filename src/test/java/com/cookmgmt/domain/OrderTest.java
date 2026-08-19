package com.cookmgmt.domain;

import com.cookmgmt.domain.exception.OrderStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Order")
class OrderTest {

    private Customer customer;
    private Meal burger;

    @BeforeEach
    void setUp() {
        customer = new Customer("Salma", "salma@example.com");
        burger = Meal.of("Beef Burger", Map.of("beef", 1, "bun", 2), 20);
    }

    private Order newOrder() {
        return new Order(1, customer, burger);
    }

    @Nested
    @DisplayName("state machine")
    class StateMachine {

        @Test
        @DisplayName("a new order is PENDING and needs no approval")
        void newOrderIsPending() {
            Order order = newOrder();

            assertEquals(OrderStatus.PENDING, order.getStatus());
            assertFalse(order.requiresApproval());
            assertFalse(order.hasSubstitutions());
        }

        @Test
        @DisplayName("applying substitutions moves the order to NEEDS_APPROVAL")
        void substitutionsRequireApproval() {
            Order order = newOrder();

            order.applySubstitutions(Map.of("beef", "tofu"));

            assertEquals(OrderStatus.NEEDS_APPROVAL, order.getStatus());
            assertTrue(order.requiresApproval());
        }

        @Test
        @DisplayName("runs the full approved path")
        void runsTheApprovedPath() {
            Order order = newOrder();
            order.applySubstitutions(Map.of("beef", "tofu"));

            order.approveSubstitutions();
            assertEquals(OrderStatus.APPROVED, order.getStatus());

            order.markInProgress();
            assertEquals(OrderStatus.IN_PROGRESS, order.getStatus());

            order.complete(Money.of("5.50"));
            assertEquals(OrderStatus.COMPLETED, order.getStatus());
            assertEquals(Money.of("5.50"), order.getFinalPrice().orElseThrow());
        }

        @Test
        @DisplayName("regression: illegal transitions are refused instead of silently applied")
        void refusesIllegalTransitions() {
            // Status was previously writable from anywhere via setStatus(), so any sequence was
            // possible - a rejected order could be marked completed and nothing would object.
            Order order = newOrder();
            order.applySubstitutions(Map.of("beef", "tofu"));
            order.rejectSubstitutions();

            assertEquals(OrderStatus.REJECTED, order.getStatus());
            assertThrows(OrderStateException.class, order::markInProgress);
            assertThrows(OrderStateException.class, () -> order.complete(Money.of("1.00")));
            assertThrows(OrderStateException.class, order::approveSubstitutions);
        }

        @Test
        @DisplayName("an order cannot be modified after approval")
        void cannotBeModifiedAfterApproval() {
            Order order = newOrder();
            order.applySubstitutions(Map.of("beef", "tofu"));
            order.approveSubstitutions();

            assertThrows(OrderStateException.class,
                    () -> order.applySubstitutions(Map.of("bun", "rice")));
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"COMPLETED", "REJECTED", "CANCELLED"})
        @DisplayName("terminal states allow no further transition")
        void terminalStatesAreFinal(OrderStatus terminal) {
            assertTrue(terminal.isTerminal());
            assertFalse(terminal.isActive());
            for (OrderStatus target : OrderStatus.values()) {
                assertFalse(terminal.canTransitionTo(target),
                        terminal + " should not be able to become " + target);
            }
        }
    }

    @Nested
    @DisplayName("price")
    class Price {

        @Test
        @DisplayName("regression: a pending order has no price rather than a price of zero")
        void pendingOrderHasNoPrice() {
            // getFinalPrice() used to return the double 0.0 before completion, which the console
            // printed as a confident "Total: $0.00" on an order that had not been priced at all.
            Order order = newOrder();

            assertTrue(order.getFinalPrice().isEmpty());
        }

        @Test
        @DisplayName("the price is frozen at completion")
        void priceIsFrozenAtCompletion() {
            Order order = newOrder();
            order.markInProgress();
            order.complete(Money.of("8.00"));

            assertEquals(Money.of("8.00"), order.getFinalPrice().orElseThrow());
            assertTrue(order.getCompletedAt().isPresent());
        }
    }

    @Nested
    @DisplayName("encapsulation")
    class Encapsulation {

        @Test
        @DisplayName("regression: approval cannot be forced by writing to a field")
        void approvalCannotBeForced() {
            // substitutionsApproved was a public mutable field, so any caller could flip it and
            // bypass the review step entirely.
            Order order = newOrder();
            order.applySubstitutions(Map.of("beef", "tofu"));

            assertTrue(order.requiresApproval());
            // The only route to approval is the transition, which the state machine polices.
            order.approveSubstitutions();
            assertFalse(order.requiresApproval());
        }

        @Test
        @DisplayName("the substitution map cannot be modified through the getter")
        void substitutionsGetterIsUnmodifiable() {
            Order order = newOrder();
            order.applySubstitutions(Map.of("beef", "tofu"));

            assertThrows(UnsupportedOperationException.class,
                    () -> order.getSubstitutions().put("bun", "rice"));
        }

        @Test
        @DisplayName("substitution keys are normalised")
        void substitutionKeysAreNormalised() {
            Order order = newOrder();
            order.applySubstitutions(Map.of("  BEEF ", " Tofu "));

            assertEquals("tofu", order.getSubstitutions().get("beef"));
            assertEquals(Map.of("tofu", 1, "bun", 2), order.effectiveRecipe());
        }

        @Test
        @DisplayName("an empty substitution map leaves the order pending")
        void emptySubstitutionsLeaveOrderPending() {
            Order order = newOrder();
            order.applySubstitutions(Map.of());

            assertEquals(OrderStatus.PENDING, order.getStatus());
        }
    }

    @Test
    @DisplayName("rejects a non-positive order number")
    void rejectsNonPositiveOrderNumber() {
        assertThrows(IllegalArgumentException.class, () -> new Order(0, customer, burger));
    }

    @Test
    @DisplayName("every status has a human-readable label")
    void everyStatusHasALabel() {
        for (OrderStatus status : OrderStatus.values()) {
            assertFalse(status.displayName().isBlank(), status + " has no display name");
        }
    }
}
