package com.cookmgmt.service;

import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Invoice;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.Order;
import com.cookmgmt.inventory.InMemoryInventory;
import com.cookmgmt.inventory.MutableInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
