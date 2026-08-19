package com.cookmgmt.bdd;

import com.cookmgmt.domain.Invoice;
import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.OrderStatus;
import com.cookmgmt.domain.Statement;
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
    private Map<String, Integer> stockBeforeCancelling = new LinkedHashMap<>();
    private Order secondOrder;

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

    // --------------------------------------------------------- cancellation

    @When("the customer cancels the order")
    public void theCustomerCancelsTheOrder() {
        stockBeforeCancelling = snapshotOf(context.order());
        context.app().orderService().cancel(context.order());
    }

    @Given("a chef has started cooking the order")
    public void aChefHasStartedCookingTheOrder() {
        context.app().kitchenService().startCooking(context.order());
    }

    @When("the customer tries to cancel the order")
    public void theCustomerTriesToCancelTheOrder() {
        stockBeforeCancelling = snapshotOf(context.order());
        context.attempt(() -> context.app().orderService().cancel(context.order()));
    }

    @Then("the cancellation should be refused")
    public void theCancellationShouldBeRefused() {
        assertNotNull(context.thrownException(),
                "Cancelling an order that is already being cooked should have been refused");
        assertEquals(OrderStatus.IN_PROGRESS, context.order().getStatus());
    }

    @Then("the ingredients should be back in stock")
    public void theIngredientsShouldBeBackInStock() {
        stockBeforeCancelling.forEach((ingredient, before) ->
                assertEquals(before + context.order().effectiveRecipe().get(ingredient),
                        context.app().inventory().stockOf(ingredient),
                        "\"" + ingredient + "\" was not returned to stock"));
    }

    @Then("the ingredients should stay out of stock")
    public void theIngredientsShouldStayOutOfStock() {
        // Returning ingredients that are already in the pan would invent food that no longer
        // exists, so a refused cancellation must leave stock exactly as it was.
        stockBeforeCancelling.forEach((ingredient, before) ->
                assertEquals(before, context.app().inventory().stockOf(ingredient),
                        "\"" + ingredient + "\" should not have been returned"));
    }

    @Then("the order should no longer appear in the chef's queue")
    public void theOrderShouldNoLongerAppearInTheQueue() {
        assertFalse(context.app().kitchenService().queueFor(context.chef()).contains(context.order()),
                "A cancelled order must leave the kitchen queue");
    }

    @Given("the customer places a second order for {string}")
    public void theCustomerPlacesASecondOrderFor(String mealName) {
        secondOrder = context.app().orderService().place(context.customer(), context.meal());
        assertEquals(mealName, secondOrder.getMeal().getName());
    }

    @Given("the customer cancels the second order")
    public void theCustomerCancelsTheSecondOrder() {
        context.app().orderService().cancel(secondOrder);
    }

    @Then("the order should not appear on the customer's statement")
    public void theOrderShouldNotAppearOnTheStatement() {
        Statement statement = statement();
        assertTrue(statement.billed().stream()
                        .noneMatch(entry -> entry.orderNumber() == context.order().getOrderNumber()),
                "A cancelled order must never be billed");
        assertEquals(1, statement.excludedCount());
    }

    @Then("the statement total should be {string}")
    public void theStatementTotalShouldBe(String expected) {
        assertEquals(expected, statement().total().toString());
    }

    @Then("the statement should bill {int} order")
    public void theStatementShouldBillOrders(int expected) {
        assertEquals(expected, statement().billedCount());
    }

    @Then("the statement should report {int} excluded order")
    public void theStatementShouldReportExcluded(int expected) {
        assertEquals(expected, statement().excludedCount());
    }

    @Then("the statement total should equal the completed order's invoice")
    public void theStatementTotalShouldEqualTheInvoice() {
        assertEquals(context.invoice().total(), statement().total());
    }

    private Statement statement() {
        return context.app().pricingService().statementFor(
                context.customer().getName(),
                context.app().orderRepository().findByCustomer(context.customer()));
    }

    /** Stock levels for every ingredient an order holds, taken before the cancellation is tried. */
    private Map<String, Integer> snapshotOf(Order order) {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        order.effectiveRecipe().keySet().forEach(ingredient ->
                snapshot.put(ingredient, context.app().inventory().stockOf(ingredient)));
        return snapshot;
    }
}
