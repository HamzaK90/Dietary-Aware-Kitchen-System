package com.cookmgmt.service;

import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Invoice;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.Statement;
import com.cookmgmt.inventory.InMemoryInventory;
import com.cookmgmt.inventory.MutableInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PricingService")
class PricingServiceTest {

    private MutableInventory inventory;
    private PricingService pricing;

    @BeforeEach
    void setUp() {
        inventory = new InMemoryInventory();
        inventory.addIngredient("tofu", 10, Money.of("3.00"));
        inventory.addIngredient("rice", 20, Money.of("1.50"));
        inventory.addIngredient("beef", 10, Money.of("6.00"));
        pricing = new PricingService(inventory);
    }

    private static Order orderOf(Meal meal, Map<String, String> substitutions) {
        Customer customer = new Customer("Test", "test@example.com");
        Order order = new Order(1, customer, meal);
        order.applySubstitutions(substitutions);
        return order;
    }

    @Test
    @DisplayName("base price is the sum of quantity times unit price")
    void basePriceSumsLineItems() {
        Meal bowl = Meal.of("Tofu Bowl", Map.of("tofu", 1, "rice", 2), 15);

        assertEquals(Money.of("6.00"), pricing.basePriceOf(bowl));   // 3.00 + 2 x 1.50
    }

    @Test
    @DisplayName("a substituted order is priced with the replacement ingredient")
    void pricesWithTheReplacementIngredient() {
        Meal burger = Meal.of("Beef Burger", Map.of("beef", 1), 20);
        Order order = orderOf(burger, Map.of("beef", "tofu"));

        assertEquals(Money.of("3.00"), pricing.quote(order));
        assertEquals(Money.of("6.00"), pricing.basePriceOf(burger),
                "The menu price of the meal itself is unchanged");
    }

    @Test
    @DisplayName("quotes a price before the order is completed")
    void quotesBeforeCompletion() {
        // The old model only calculated a price inside completeOrder(), so a pending order
        // reported 0.0 and the console printed a confident "Total: $0.00".
        Meal bowl = Meal.of("Tofu Bowl", Map.of("tofu", 1, "rice", 2), 15);
        Order order = orderOf(bowl, Map.of());

        assertTrue(order.getFinalPrice().isEmpty(), "no final price before completion");
        assertEquals(Money.of("6.00"), pricing.quote(order));
    }

    @Test
    @DisplayName("prices an unknown ingredient at zero rather than failing")
    void pricesUnknownIngredientsAtZero() {
        Meal exotic = Meal.of("Mystery Dish", Map.of("unobtainium", 3), 10);

        assertEquals(Money.ZERO, pricing.basePriceOf(exotic));
    }

    @Test
    @DisplayName("invoice line items sum exactly to the invoice total")
    void invoiceLinesSumToTotal() {
        Meal bowl = Meal.of("Tofu Bowl", Map.of("tofu", 1, "rice", 2), 15);
        Order order = orderOf(bowl, Map.of());
        order.markInProgress();
        order.complete(pricing.quote(order));

        Invoice invoice = pricing.invoiceFor(order);
        Money sum = invoice.lines().stream()
                .map(Invoice.Line::lineTotal)
                .reduce(Money.ZERO, Money::plus);

        assertEquals(invoice.total(), sum);
        assertEquals(2, invoice.lines().size());
    }

    @Test
    @DisplayName("an issued invoice is not rewritten by a later price change")
    void issuedInvoiceIsNotRewritten() {
        Meal bowl = Meal.of("Tofu Bowl", Map.of("tofu", 1, "rice", 2), 15);
        Order order = orderOf(bowl, Map.of());
        order.markInProgress();
        order.complete(pricing.quote(order));

        inventory.reprice("tofu", Money.of("99.00"));

        assertEquals(Money.of("6.00"), pricing.invoiceFor(order).total());
    }

    @Test
    @DisplayName("the rendered invoice names the order, meal and total")
    void renderedInvoiceCarriesTheDetails() {
        Meal bowl = Meal.of("Tofu Bowl", Map.of("tofu", 1, "rice", 2), 15);
        Order order = orderOf(bowl, Map.of());
        order.markInProgress();
        order.complete(pricing.quote(order));

        String rendered = pricing.invoiceFor(order).format();

        assertTrue(rendered.contains("Invoice #1"), rendered);
        assertTrue(rendered.contains("Tofu Bowl"), rendered);
        assertTrue(rendered.contains("$6.00"), rendered);
    }

    // ------------------------------------------------------------- statements

    /** Builds an order with a distinct number so statement entries can be told apart. */
    private Order numberedOrder(int number, Meal meal) {
        return new Order(number, new Customer("Layla", "layla@example.com"), meal);
    }

    private Order completedOrder(int number, Meal meal) {
        Order order = numberedOrder(number, meal);
        order.markInProgress();
        order.complete(pricing.quote(order));
        return order;
    }

    @Test
    @DisplayName("a statement totals only the orders that were actually served")
    void statementTotalsCompletedOrdersOnly() {
        Meal bowl = Meal.of("Tofu Bowl", Map.of("tofu", 1, "rice", 2), 15);   // 6.00
        Meal burger = Meal.of("Beef Burger", Map.of("beef", 1), 20);          // 6.00

        Statement statement = pricing.statementFor("Layla", List.of(
                completedOrder(1, bowl),
                completedOrder(2, burger)));

        assertEquals(Money.of("12.00"), statement.total());
        assertEquals(2, statement.billedCount());
        assertEquals(0, statement.excludedCount());
    }

    @Test
    @DisplayName("a cancelled order is excluded from the total and reported as excluded")
    void statementExcludesCancelledOrders() {
        /*
         * This is the defect the statement exists to prevent. The GUI used to invoice whatever row
         * was selected, and invoiceFor falls back to a live quote when an order has no frozen
         * price - so a cancelled order rendered a complete, entirely fictional bill.
         */
        Meal bowl = Meal.of("Tofu Bowl", Map.of("tofu", 1, "rice", 2), 15);

        Order cancelled = numberedOrder(2, bowl);
        cancelled.cancel();

        Statement statement = pricing.statementFor("Layla", List.of(
                completedOrder(1, bowl),
                cancelled));

        assertEquals(Money.of("6.00"), statement.total(), "the cancelled order must not be charged");
        assertEquals(1, statement.billedCount());
        assertEquals(1, statement.excludedCount());
        assertTrue(statement.billed().stream().noneMatch(entry -> entry.orderNumber() == 2));
        assertTrue(statement.unbilled().stream().noneMatch(entry -> entry.orderNumber() == 2));
    }

    @Test
    @DisplayName("a rejected order is excluded too")
    void statementExcludesRejectedOrders() {
        Meal burger = Meal.of("Beef Burger", Map.of("beef", 1), 20);

        Order rejected = numberedOrder(1, burger);
        rejected.applySubstitutions(Map.of("beef", "tofu"));
        rejected.rejectSubstitutions();

        Statement statement = pricing.statementFor("Layla", List.of(rejected));

        assertEquals(Money.ZERO, statement.total());
        assertEquals(1, statement.excludedCount());
        assertTrue(statement.isEmpty());
    }

    @Test
    @DisplayName("an order still in the kitchen is listed unbilled and does not affect the total")
    void statementListsActiveOrdersSeparately() {
        Meal bowl = Meal.of("Tofu Bowl", Map.of("tofu", 1, "rice", 2), 15);

        Statement statement = pricing.statementFor("Layla", List.of(
                completedOrder(1, bowl),
                numberedOrder(2, bowl)));           // still PENDING

        assertEquals(Money.of("6.00"), statement.total(),
                "an order that has not been served yet is not billed");
        assertEquals(1, statement.billedCount());
        assertEquals(1, statement.unbilled().size());
        assertEquals(2, statement.unbilled().get(0).orderNumber());
        assertEquals(Money.of("6.00"), statement.unbilled().get(0).amount(),
                "but it is still quoted, so the customer knows what is coming");
    }

    @Test
    @DisplayName("a statement uses the frozen price, not a repriced one")
    void statementUsesFrozenPrices() {
        Meal bowl = Meal.of("Tofu Bowl", Map.of("tofu", 1, "rice", 2), 15);
        Order served = completedOrder(1, bowl);

        inventory.reprice("tofu", Money.of("99.00"));

        assertEquals(Money.of("6.00"), pricing.statementFor("Layla", List.of(served)).total());
    }

    @Test
    @DisplayName("a customer with no orders gets a zero statement rather than an error")
    void statementForNoOrdersIsZero() {
        Statement statement = pricing.statementFor("Layla", List.of());

        assertEquals(Money.ZERO, statement.total());
        assertEquals(0, statement.billedCount());
        assertTrue(statement.isEmpty());
        assertTrue(statement.format().contains("No completed orders yet"));
    }

    @Test
    @DisplayName("the rendered statement names the customer and the grand total")
    void renderedStatementCarriesTheDetails() {
        Meal bowl = Meal.of("Tofu Bowl", Map.of("tofu", 1, "rice", 2), 15);
        Order cancelled = numberedOrder(2, bowl);
        cancelled.cancel();

        String rendered = pricing.statementFor("Layla", List.of(completedOrder(1, bowl), cancelled))
                .format();

        assertTrue(rendered.contains("Layla"), rendered);
        assertTrue(rendered.contains("Tofu Bowl"), rendered);
        assertTrue(rendered.contains("$6.00"), rendered);
        assertTrue(rendered.contains("1 cancelled or rejected order excluded"), rendered);
    }

    @Test
    @DisplayName("substituting onto an ingredient already in the recipe adds the quantities")
    void substitutingOntoAnExistingIngredientAddsQuantities() {
        // Without merging, one of the two quantities would be silently dropped and the order
        // would be priced for less food than it actually consumes.
        Meal mixed = Meal.of("Mixed Bowl", Map.of("beef", 1, "tofu", 1), 20);
        Order order = orderOf(mixed, Map.of("beef", "tofu"));

        assertEquals(Map.of("tofu", 2), order.effectiveRecipe());
        assertEquals(Money.of("6.00"), pricing.quote(order));
    }
}
