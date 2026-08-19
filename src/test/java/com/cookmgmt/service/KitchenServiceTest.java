package com.cookmgmt.service;

import com.cookmgmt.app.AppContext;
import com.cookmgmt.domain.Chef;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Invoice;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.OrderStatus;
import com.cookmgmt.domain.exception.OrderStateException;
import com.cookmgmt.notify.InMemoryNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("KitchenService")
class KitchenServiceTest {

    private AppContext app;
    private InMemoryNotifier notifier;
    private Customer customer;
    private Chef nora;
    private Meal burger;

    @BeforeEach
    void setUp() {
        notifier = new InMemoryNotifier();
        app = new AppContext(notifier);

        app.inventoryService().addIngredient("beef", 10, Money.of("6.00"));
        app.inventoryService().addIngredient("tofu", 10, Money.of("3.50"));
        app.inventoryService().addIngredient("bun", 10, Money.of("1.00"));

        customer = app.customerService()
                .register("Salma", "salma@example.com", List.of("Vegetarian"), List.of());
        nora = app.staffService().hire("Chef Nora", "nora@kitchen.com");
        burger = app.catalogService().addMeal("Beef Burger", Map.of("beef", 1, "bun", 2), 20);
    }

    private Order placeWithSubstitution() {
        return app.orderService().place(customer, burger, Map.of("beef", "tofu"));
    }

    @Test
    @DisplayName("a chef's queue lists their unfinished orders")
    void queueListsUnfinishedOrders() {
        Order order = app.orderService().place(customer, burger, Map.of("beef", "tofu"));

        List<Order> queue = app.kitchenService().queueFor(nora);

        assertEquals(1, queue.size());
        assertEquals(order.getOrderNumber(), queue.get(0).getOrderNumber());
    }

    @Test
    @DisplayName("regression: reading the queue does not consume it")
    void readingTheQueueDoesNotConsumeIt() {
        // The console used Queue.poll() to look at the next order, which removed it. If the chef
        // then rejected it, the order was unreachable from anywhere in the system - including the
        // customer's own "Modify Pending Order" screen, which went looking for exactly it.
        placeWithSubstitution();

        assertEquals(1, app.kitchenService().queueFor(nora).size());
        assertEquals(1, app.kitchenService().queueFor(nora).size());
        assertTrue(app.kitchenService().nextFor(nora).isPresent());
        assertEquals(1, app.kitchenService().queueFor(nora).size());
    }

    @Test
    @DisplayName("approving a substitution moves the order to APPROVED")
    void approvingMovesToApproved() {
        Order order = placeWithSubstitution();

        app.kitchenService().approve(order);

        assertEquals(OrderStatus.APPROVED, order.getStatus());
        assertFalse(order.requiresApproval());
        assertTrue(notifier.anyContaining("were approved"));
    }

    @Test
    @DisplayName("regression: rejecting an order returns its ingredients to stock")
    void rejectingReturnsIngredientsToStock() {
        // Nothing released reserved stock on rejection, so those ingredients were lost outright.
        int tofuBefore = app.inventory().stockOf("tofu");
        int bunBefore = app.inventory().stockOf("bun");

        Order order = placeWithSubstitution();
        assertEquals(tofuBefore - 1, app.inventory().stockOf("tofu"), "precondition: stock reserved");

        app.kitchenService().reject(order);

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        assertEquals(tofuBefore, app.inventory().stockOf("tofu"));
        assertEquals(bunBefore, app.inventory().stockOf("bun"));
    }

    @Test
    @DisplayName("a rejected order remains retrievable")
    void rejectedOrderRemainsRetrievable() {
        Order order = placeWithSubstitution();
        app.kitchenService().reject(order);

        assertTrue(app.orderRepository().findByOrderNumber(order.getOrderNumber()).isPresent());
        assertTrue(app.orderRepository().findByCustomer(customer).contains(order));
    }

    @Test
    @DisplayName("completing an order issues an invoice and notifies the customer")
    void completingIssuesAnInvoice() {
        Order order = app.orderService().place(customer, burger, Map.of("beef", "tofu"));
        app.kitchenService().approve(order);

        Invoice invoice = app.kitchenService().complete(order);

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals(Money.of("5.50"), invoice.total());   // tofu 3.50 + 2 x bun 1.00
        assertTrue(notifier.anyContaining("is ready"));
    }

    @Test
    @DisplayName("the invoice marks which ingredient was substituted")
    void invoiceMarksSubstitutedIngredient() {
        Order order = placeWithSubstitution();
        app.kitchenService().approve(order);
        Invoice invoice = app.kitchenService().complete(order);

        assertTrue(invoice.lines().stream()
                        .anyMatch(line -> line.ingredient().equals("tofu") && line.substituted()),
                "Invoice lines were " + invoice.lines());
    }

    @Test
    @DisplayName("a completed order cannot be completed again")
    void completedOrderCannotBeCompletedAgain() {
        // Status used to be writable from anywhere through a public setter, so illegal sequences
        // were possible and went unnoticed.
        Order order = app.orderService().place(customer, burger);
        app.kitchenService().complete(order);

        assertThrows(OrderStateException.class, () -> app.kitchenService().complete(order));
    }

    @Test
    @DisplayName("a rejected order cannot then be cooked")
    void rejectedOrderCannotBeCooked() {
        Order order = placeWithSubstitution();
        app.kitchenService().reject(order);

        assertThrows(OrderStateException.class, () -> app.kitchenService().startCooking(order));
    }

    @Test
    @DisplayName("orders awaiting approval are listed for the kitchen")
    void listsOrdersAwaitingApproval() {
        placeWithSubstitution();
        app.orderService().place(customer, burger, Map.of("beef", "tofu"));

        assertEquals(2, app.kitchenService().awaitingApproval().size());
    }
}
