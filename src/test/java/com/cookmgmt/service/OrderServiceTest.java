package com.cookmgmt.service;

import com.cookmgmt.app.AppContext;
import com.cookmgmt.domain.Chef;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Invoice;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.OrderStatus;
import com.cookmgmt.domain.exception.InsufficientStockException;
import com.cookmgmt.notify.InMemoryNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderService")
class OrderServiceTest {

    private AppContext app;
    private InMemoryNotifier notifier;
    private Customer layla;
    private Chef nora;
    private Meal tofuBowl;

    @BeforeEach
    void setUp() {
        notifier = new InMemoryNotifier();
        app = new AppContext(notifier);

        app.inventoryService().addIngredient("tofu", 10, Money.of("3.50"));
        app.inventoryService().addIngredient("rice", 20, Money.of("1.50"));
        app.inventoryService().addIngredient("beef", 10, Money.of("6.00"));
        app.inventoryService().addIngredient("mushroom", 10, Money.of("2.80"));

        layla = app.customerService().register("Layla", "layla@example.com",
                List.of("Vegan"), List.of("milk"));
        nora = app.staffService().hire("Chef Nora", "nora@kitchen.com");
        tofuBowl = app.catalogService().addMeal("Tofu Bowl", Map.of("tofu", 1, "rice", 2), 15);
    }

    @Nested
    @DisplayName("placing an order")
    class Placing {

        @Test
        @DisplayName("regression: reserving ingredients actually reduces stock")
        void placingAnOrderReducesStock() {
            /*
             * The console's placeOrder built the Order directly and never touched inventory, while
             * the one method that did adjust stock (Customer.placeOrder) was never called by
             * anything. Quantities therefore only ever went up, and the "Low Stock Alerts" screen
             * could never fire.
             */
            int tofuBefore = app.inventory().stockOf("tofu");
            int riceBefore = app.inventory().stockOf("rice");

            app.orderService().place(layla, tofuBowl);

            assertEquals(tofuBefore - 1, app.inventory().stockOf("tofu"));
            assertEquals(riceBefore - 2, app.inventory().stockOf("rice"));
        }

        @Test
        @DisplayName("stores the order and assigns it to a chef")
        void storesAndAssignsTheOrder() {
            Order order = app.orderService().place(layla, tofuBowl);

            assertEquals(1, app.orderRepository().count());
            assertEquals(java.util.Optional.of(nora.getId()), order.getAssignedChefId());
            assertEquals(OrderStatus.PENDING, order.getStatus());
        }

        @Test
        @DisplayName("numbers orders from 1 within a fresh context")
        void numbersOrdersFromOne() {
            // Order numbers came from a static counter that was never reset, so they leaked
            // across scenarios and no test could assert on a specific value.
            assertEquals(1, app.orderService().place(layla, tofuBowl).getOrderNumber());
            assertEquals(2, app.orderService().place(layla, tofuBowl).getOrderNumber());

            AppContext fresh = new AppContext();
            fresh.inventoryService().addIngredient("rice", 10, Money.of("1.00"));
            Customer other = fresh.customerService()
                    .register("Other", "other@example.com", List.of(), List.of());
            Meal simple = fresh.catalogService().addMeal("Rice", Map.of("rice", 1), 5);

            assertEquals(1, fresh.orderService().place(other, simple).getOrderNumber());
        }

        @Test
        @DisplayName("refuses an order the kitchen cannot supply")
        void refusesAnOrderItCannotSupply() {
            Meal huge = app.catalogService().addMeal("Tofu Mountain", Map.of("tofu", 999), 60);

            assertThrows(InsufficientStockException.class,
                    () -> app.orderService().place(layla, huge));
        }

        @Test
        @DisplayName("does not deduct any stock when one ingredient is short")
        void deductsNothingWhenAnIngredientIsShort() {
            // Reservation is all-or-nothing; a partial deduction would leave the inventory wrong
            // with no record of what to put back.
            Meal partlyAvailable = app.catalogService()
                    .addMeal("Impossible Bowl", Map.of("rice", 2, "tofu", 999), 20);
            int riceBefore = app.inventory().stockOf("rice");

            assertThrows(InsufficientStockException.class,
                    () -> app.orderService().place(layla, partlyAvailable));

            assertEquals(riceBefore, app.inventory().stockOf("rice"));
            assertEquals(0, app.orderRepository().count(), "No order should have been stored");
        }

        @Test
        @DisplayName("refuses an order that still contains one of the customer's allergens")
        void refusesAnOrderContainingAnAllergen() {
            app.inventoryService().addIngredient("milk", 10, Money.of("1.50"));
            Meal milky = app.catalogService().addMeal("Milkshake", Map.of("milk", 1), 5);

            assertThrows(IllegalArgumentException.class,
                    () -> app.orderService().place(layla, milky));
            assertEquals(0, app.orderRepository().count());
        }

        @Test
        @DisplayName("an order with substitutions waits for chef approval")
        void orderWithSubstitutionsAwaitsApproval() {
            Meal burger = app.catalogService().addMeal("Beef Burger", Map.of("beef", 1), 20);

            Order order = app.orderService().place(layla, burger, Map.of("beef", "mushroom"));

            assertEquals(OrderStatus.NEEDS_APPROVAL, order.getStatus());
            assertTrue(order.requiresApproval());
            assertTrue(order.effectiveRecipe().containsKey("mushroom"));
            assertFalse(order.effectiveRecipe().containsKey("beef"));
        }

        @Test
        @DisplayName("reserves the substituted ingredient, not the original")
        void reservesTheSubstitutedIngredient() {
            Meal burger = app.catalogService().addMeal("Beef Burger", Map.of("beef", 1), 20);
            int beefBefore = app.inventory().stockOf("beef");
            int mushroomBefore = app.inventory().stockOf("mushroom");

            app.orderService().place(layla, burger, Map.of("beef", "mushroom"));

            assertEquals(beefBefore, app.inventory().stockOf("beef"),
                    "The replaced ingredient should not have been taken");
            assertEquals(mushroomBefore - 1, app.inventory().stockOf("mushroom"));
        }

        @Test
        @DisplayName("warns the customer when no chef is available")
        void warnsWhenNoChefAvailable() {
            app.staffService().remove(nora);

            Order order = app.orderService().place(layla, tofuBowl);

            assertTrue(order.getAssignedChefId().isEmpty());
            assertTrue(notifier.anyContaining("no chef is available"));
        }
    }

    @Nested
    @DisplayName("preview")
    class Preview {

        @Test
        @DisplayName("reports conflicts and a proposed substitution without placing anything")
        void reportsConflictsWithoutPlacing() {
            app.inventoryService().addIngredient("milk", 10, Money.of("1.50"));
            app.inventoryService().addIngredient("oat milk", 10, Money.of("2.10"));
            Meal milky = app.catalogService().addMeal("Latte", Map.of("milk", 1), 5);

            OrderService.OrderPreview preview = app.orderService().preview(layla, milky);

            assertTrue(preview.hasConflicts());
            assertTrue(preview.hasAllergyConflict());
            assertEquals("oat milk", preview.substitutions().get("milk"));
            assertEquals(0, app.orderRepository().count(), "preview must not place an order");
        }

        @Test
        @DisplayName("quotes a price for a meal with no conflicts")
        void quotesAPrice() {
            OrderService.OrderPreview preview = app.orderService().preview(layla, tofuBowl);

            assertFalse(preview.hasConflicts());
            assertEquals(Money.of("6.50"), preview.price());   // 3.50 + 2 x 1.50
            assertTrue(preview.canProceed());
            assertFalse(preview.hasUnresolvedConflicts());
        }

        @Test
        @DisplayName("reports conflicts the substitutions cannot resolve")
        void reportsUnresolvedConflicts() {
            // A vegan meal containing an ingredient with no acceptable replacement in stock must
            // not be presented as though the proposed changes fixed everything.
            app.inventoryService().addIngredient("cheese", 10, Money.of("2.50"));
            Meal burger = app.catalogService()
                    .addMeal("Cheeseburger", Map.of("beef", 1, "cheese", 1), 20);

            OrderService.OrderPreview preview = app.orderService().preview(layla, burger);

            assertTrue(preview.substitutions().containsKey("beef"), "beef is replaceable");
            assertTrue(preview.hasUnresolvedConflicts(),
                    "cheese has no vegan alternative in stock and should be reported");
            assertTrue(preview.remainingConflicts().stream()
                            .anyMatch(conflict -> conflict.ingredient().equals("cheese")),
                    "Remaining conflicts were " + preview.remainingConflicts());
            // A dietary clash does not block the order outright; only an allergen does.
            assertFalse(preview.hasUnresolvedAllergen());
            assertTrue(preview.canProceed());
        }

        @Test
        @DisplayName("an unresolvable allergen blocks the order")
        void unresolvableAllergenBlocksTheOrder() {
            app.inventoryService().addIngredient("milk", 10, Money.of("1.50"));
            Meal latte = app.catalogService().addMeal("Latte", Map.of("milk", 1), 5);

            OrderService.OrderPreview preview = app.orderService().preview(layla, latte);

            assertTrue(preview.hasUnresolvedAllergen());
            assertFalse(preview.canProceed());
        }
    }

    @Nested
    @DisplayName("cancelling")
    class Cancelling {

        @Test
        @DisplayName("returns reserved ingredients to stock")
        void returnsIngredientsToStock() {
            int tofuBefore = app.inventory().stockOf("tofu");
            Order order = app.orderService().place(layla, tofuBowl);

            app.orderService().cancel(order);

            assertEquals(OrderStatus.CANCELLED, order.getStatus());
            assertEquals(tofuBefore, app.inventory().stockOf("tofu"));
        }
    }

    @Nested
    @DisplayName("order history")
    class History {

        @Test
        @DisplayName("a completed order stays visible in the customer's history")
        void completedOrderStaysVisible() {
            /*
             * Completing an order used to clear Customer.currentOrder, after which the admin's
             * orders report showed "Current Order: None" for an order that had just been served,
             * and history depended on a separate list that only receiveInvoice() ever appended to.
             */
            Order order = app.orderService().place(layla, tofuBowl);
            app.kitchenService().complete(order);

            List<Order> history = app.orderService().historyFor(layla);

            assertEquals(1, history.size());
            assertEquals(order.getOrderNumber(), history.get(0).getOrderNumber());
            assertTrue(app.orderService().activeFor(layla).isEmpty());
        }

        @Test
        @DisplayName("the invoice total is not zero for a completed order")
        void invoiceTotalIsNotZero() {
            Order order = app.orderService().place(layla, tofuBowl);
            Invoice invoice = app.kitchenService().complete(order);

            assertEquals(Money.of("6.50"), invoice.total());
            assertFalse(invoice.total().isZero());
        }
    }
}
