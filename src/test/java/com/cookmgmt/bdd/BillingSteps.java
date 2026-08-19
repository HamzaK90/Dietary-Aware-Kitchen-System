package com.cookmgmt.bdd;

import com.cookmgmt.domain.Invoice;
import com.cookmgmt.domain.Money;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Steps for {@code billing.feature}. */
public class BillingSteps {

    private final TestContext context;
    private Money totalAtCompletion;

    public BillingSteps(TestContext context) {
        this.context = context;
    }

    @Given("a customer places an order for {string}")
    public void aCustomerPlacesAnOrderFor(String mealName) {
        context.app().inventoryService().addIngredient("tofu", 10, Money.of("3.50"));
        context.app().inventoryService().addIngredient("rice", 20, Money.of("1.50"));

        Map<String, Integer> recipe = new LinkedHashMap<>();
        recipe.put("tofu", 1);
        recipe.put("rice", 2);

        context.setMeal(context.app().catalogService().addMeal(mealName, recipe, 15));
        context.givenCustomer("Layla", List.of(), List.of());
        context.givenChef("Chef Zaid", "zaid@kitchen.com");
        context.setOrder(context.app().orderService().place(context.customer(), context.meal()));
    }

    @When("the order has been completed")
    public void theOrderHasBeenCompleted() {
        assertNotNull(context.order(), "Order must be placed before it can be completed");
        Invoice invoice = context.app().kitchenService().complete(context.order());
        context.setInvoice(invoice);
        totalAtCompletion = invoice.total();
    }

    @When("the price of {string} is doubled")
    public void thePriceOfIsDoubled(String ingredient) {
        Money doubled = context.app().inventory().priceOf(ingredient).times(2);
        context.app().inventoryService().reprice(ingredient, doubled);
    }

    @Then("the customer should receive an invoice")
    public void theCustomerShouldReceiveAnInvoice() {
        Invoice invoice = context.invoice();
        assertNotNull(invoice);
        assertTrue(invoice.format().contains("Invoice"));
        assertEquals(context.customer().getName(), invoice.customerName());
    }

    @Then("the invoice should include the total cost")
    public void theInvoiceShouldIncludeTheTotalCost() {
        Invoice invoice = context.invoice();
        assertFalse(invoice.total().isZero(),
                "A completed order should not be invoiced at zero");
        assertTrue(invoice.format().contains(invoice.total().toString()),
                "Rendered invoice was:\n" + invoice.format());
    }

    @Then("the invoice line items should add up to the invoice total")
    public void theInvoiceLineItemsShouldAddUp() {
        Invoice invoice = context.invoice();
        assertFalse(invoice.lines().isEmpty(), "Invoice had no line items");
        Money sum = invoice.lines().stream()
                .map(Invoice.Line::lineTotal)
                .reduce(Money.ZERO, Money::plus);
        // Exact equality is meaningful because money is BigDecimal, not double.
        assertEquals(invoice.total(), sum,
                "Line items " + invoice.lines() + " do not sum to " + invoice.total());
    }

    @Then("the invoice total should stay the same")
    public void theInvoiceTotalShouldStayTheSame() {
        // The price is frozen on the order at completion, so repricing an ingredient afterwards
        // cannot rewrite an invoice that has already been issued.
        Invoice reissued = context.app().pricingService().invoiceFor(context.order());
        assertEquals(totalAtCompletion, reissued.total());
    }
}
