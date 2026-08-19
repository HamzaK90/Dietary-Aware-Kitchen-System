package com.cookmgmt.bdd;

import com.cookmgmt.domain.Invoice;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Money;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Steps for {@code meal_pricing_and_invoice.feature}. */
public class MealPricingAndInvoiceSteps {

    private final TestContext context;

    public MealPricingAndInvoiceSteps(TestContext context) {
        this.context = context;
    }

    @Given("the meal {string} contains {int} {string} at {double} and {int} {string} at {double}")
    public void theMealContainsTwoPricedIngredients(String mealName,
                                                    Integer firstQty, String first, Double firstPrice,
                                                    Integer secondQty, String second, Double secondPrice) {
        context.app().inventoryService().addIngredient(first, 100, Money.of(firstPrice));
        context.app().inventoryService().addIngredient(second, 100, Money.of(secondPrice));

        Map<String, Integer> recipe = new LinkedHashMap<>();
        recipe.put(first.toLowerCase(), firstQty);
        recipe.put(second.toLowerCase(), secondQty);
        context.setMeal(context.app().catalogService().addMeal(mealName, recipe, 15));
    }

    @Given("the meal {string} contains {int} {string} at {double}")
    public void theMealContainsOnePricedIngredient(String mealName,
                                                   Integer quantity, String ingredient, Double price) {
        context.app().inventoryService().addIngredient(ingredient, 100, Money.of(price));
        context.setMeal(context.app().catalogService()
                .addMeal(mealName, Map.of(ingredient.toLowerCase(), quantity), 20));
    }

    @And("{string} is available at {double}")
    public void ingredientIsAvailableAt(String ingredient, Double price) {
        context.app().inventoryService().addIngredient(ingredient, 100, Money.of(price));
    }

    @When("the system calculates the base price")
    public void theSystemCalculatesTheBasePrice() {
        context.setPrice(context.app().pricingService().basePriceOf(context.meal()));
    }

    @When("the customer replaces {string} with {string} and the price is recalculated")
    public void theCustomerReplacesAndRecalculates(String original, String replacement) {
        Map<String, String> substitutions = Map.of(original.toLowerCase(), replacement.toLowerCase());
        context.setSubstitutions(substitutions);
        Meal meal = context.meal();
        context.setPrice(context.app().pricingService().priceOf(meal.recipeWith(substitutions)));
    }

    @Given("the customer {string} placed an order for {string}")
    public void theCustomerPlacedAnOrderFor(String customerName, String mealName) {
        context.app().inventoryService().addIngredient("tofu", 10, Money.of("3.50"));
        context.app().inventoryService().addIngredient("rice", 20, Money.of("1.50"));

        Map<String, Integer> recipe = new LinkedHashMap<>();
        recipe.put("tofu", 1);
        recipe.put("rice", 2);

        context.setMeal(context.app().catalogService().addMeal(mealName, recipe, 15));
        context.givenCustomer(customerName, List.of(), List.of());
        context.givenChef("Chef Nora", "nora@kitchen.com");
        context.setOrder(context.app().orderService().place(context.customer(), context.meal()));
    }

    @And("the system completes the order")
    public void theSystemCompletesTheOrder() {
        context.setInvoice(context.app().kitchenService().complete(context.order()));
    }

    @When("the customer receives the invoice")
    public void theCustomerReceivesTheInvoice() {
        assertTrue(context.invoice().format().contains("Invoice"));
    }

    @Then("the total should equal the sum of ingredient quantities times their prices")
    public void theTotalShouldEqualTheSum() {
        Money expected = context.meal().getIngredients().entrySet().stream()
                .map(entry -> context.app().inventory().priceOf(entry.getKey()).times(entry.getValue()))
                .reduce(Money.ZERO, Money::plus);
        assertEquals(expected, context.price());
    }

    @And("the base price should be {double}")
    public void theBasePriceShouldBe(Double expected) {
        assertEquals(Money.of(expected), context.price());
    }

    @Then("it should use the price of {string} instead of {string} in the total")
    public void itShouldUseThePriceOfInsteadOf(String replacement, String original) {
        Money replacementPrice = context.app().inventory().priceOf(replacement);
        Money originalPrice = context.app().inventory().priceOf(original);
        assertEquals(replacementPrice, context.price(),
                "Total should be the replacement price");
        assertTrue(!originalPrice.equals(context.price()) || originalPrice.equals(replacementPrice),
                "Total still reflects the original ingredient price");
    }

    @And("the recalculated total should be {double}")
    public void theRecalculatedTotalShouldBe(Double expected) {
        assertEquals(Money.of(expected), context.price());
    }

    @Then("the invoice should include the meal name, order ID, and total price")
    public void theInvoiceShouldIncludeDetails() {
        Invoice invoice = context.invoice();
        String rendered = invoice.format();
        assertTrue(rendered.contains(context.meal().getName()), "Missing meal name:\n" + rendered);
        assertTrue(rendered.contains(String.valueOf(context.order().getOrderNumber())),
                "Missing order number:\n" + rendered);
        assertTrue(rendered.contains(invoice.total().toString()), "Missing total:\n" + rendered);
    }
}
