package com.cookmgmt.bdd;

import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.exception.InsufficientStockException;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Steps for {@code inventory_management.feature}. */
public class InventoryManagementSteps {

    private final TestContext context;
    private List<String> lowStock = List.of();

    public InventoryManagementSteps(TestContext context) {
        this.context = context;
    }

    @Given("the inventory has {string} with quantity {int}")
    public void theInventoryHasQuantity(String ingredient, Integer quantity) {
        context.app().inventoryService()
                .addIngredient(ingredient, quantity, Money.of("2.00"));
    }

    @And("the meal {string} needs {int} {string} and {int} {string}")
    public void theMealNeeds(String mealName, Integer firstQty, String first,
                             Integer secondQty, String second) {
        Map<String, Integer> recipe = new LinkedHashMap<>();
        recipe.put(first.toLowerCase(), firstQty);
        recipe.put(second.toLowerCase(), secondQty);
        context.setMeal(context.app().catalogService().addMeal(mealName, recipe, 15));
    }

    @When("a chef checks stock levels")
    public void aChefChecksStockLevels() {
        lowStock = context.app().inventoryService().lowStock();
        context.app().inventoryService().warnAboutLowStock();
    }

    @When("the customer {string} orders the {string}")
    public void theCustomerOrdersThe(String customerName, String mealName) {
        context.givenCustomer(customerName, List.of(), List.of());
        context.givenChef("Chef On Duty", "duty@kitchen.com");
        context.setOrder(context.app().orderService()
                .place(context.customer(), context.meal()));
    }

    @When("{int} units of {string} are consumed")
    public void unitsAreConsumed(Integer quantity, String ingredient) {
        context.attempt(() -> context.app().inventory().consume(ingredient, quantity));
    }

    @Then("the system should report that {string} is low")
    public void theSystemShouldReportThatIsLow(String ingredient) {
        assertTrue(lowStock.contains(ingredient.toLowerCase()),
                "Low stock list was " + lowStock);
    }

    @Then("the system should not report that {string} is low")
    public void theSystemShouldNotReportThatIsLow(String ingredient) {
        assertFalse(lowStock.contains(ingredient.toLowerCase()),
                "Low stock list unexpectedly contained " + ingredient);
    }

    @Then("a restock warning should have been raised for {string}")
    public void aRestockWarningShouldHaveBeenRaised(String ingredient) {
        // Asserts on the Notifier rather than on captured console output, which is what the
        // old design forced - business logic printed its own warnings.
        assertTrue(context.notifier().anyContaining("Low stock: \"" + ingredient.toLowerCase() + "\""),
                "Notifications were " + context.notifier().all());
    }

    @Then("the stock of {string} should be {int}")
    public void theStockOfShouldBe(String ingredient, Integer expected) {
        // The console order flow never touched inventory, so this quantity never moved.
        assertEquals(expected.intValue(), context.app().inventory().stockOf(ingredient));
    }

    @Then("the operation should be refused for insufficient stock")
    public void theOperationShouldBeRefused() {
        Exception thrown = context.thrownException();
        assertNotNull(thrown, "Consuming more stock than exists was allowed");
        assertInstanceOf(InsufficientStockException.class, thrown);
    }
}
