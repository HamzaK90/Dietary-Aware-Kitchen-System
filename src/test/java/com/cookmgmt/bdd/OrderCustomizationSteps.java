package com.cookmgmt.bdd;

import com.cookmgmt.app.SampleData;
import com.cookmgmt.domain.Conflict;
import com.cookmgmt.domain.ConflictType;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.OrderStatus;
import com.cookmgmt.service.OrderService;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Steps for {@code order_customization.feature}.
 *
 * <p>Customer names are quoted in the Gherkin so that each sentence has exactly one matching step
 * definition. An unquoted name would also match a {@code {word}} parameter, and two definitions
 * competing for the same sentence leaves it ambiguous which one actually runs.
 */
public class OrderCustomizationSteps {

    private final TestContext context;
    private OrderService.OrderPreview preview;

    public OrderCustomizationSteps(TestContext context) {
        this.context = context;
    }

    // ------------------------------------------------------------------ given

    @Given("a meal {string} contains {string}")
    public void aMealContains(String mealName, String ingredient) {
        SampleData.loadInventory(context.app().inventoryService());
        context.givenChef("Chef On Duty", "duty@kitchen.com");
        context.setMeal(context.app().catalogService()
                .addMeal(mealName, Map.of(ingredient.toLowerCase(), 1), 20));
    }

    @And("the customer {string} is allergic to {string}")
    public void theCustomerIsAllergicTo(String name, String allergy) {
        context.givenCustomer(name, List.of(), List.of(allergy));
    }

    @And("the customer {string} is vegetarian")
    public void theCustomerIsVegetarian(String name) {
        context.givenCustomer(name, List.of("Vegetarian"), List.of());
    }

    @Given("a meal was modified by the customer")
    public void aMealWasModifiedByTheCustomer() {
        SampleData.loadInventory(context.app().inventoryService());
        context.givenChef("Chef Test", "test@kitchen.com");
        context.givenCustomer("Test Diner", List.of("Vegetarian"), List.of());
        context.setMeal(context.app().catalogService()
                .addMeal("Burger", Map.of("beef", 1), 15));
        context.setOrder(context.app().orderService().place(
                context.customer(), context.meal(), Map.of("beef", "tofu")));
    }

    @Given("the customer {string} orders a meal with no substitutions")
    public void theCustomerOrdersAMealWithNoSubstitutions(String name) {
        SampleData.loadInventory(context.app().inventoryService());
        context.givenChef("Chef Plain", "plain@kitchen.com");
        context.givenCustomer(name, List.of(), List.of());
        context.setMeal(context.app().catalogService()
                .addMeal("Rice Bowl", Map.of("rice", 2), 10));
        context.setOrder(context.app().orderService().place(context.customer(), context.meal()));
    }

    // ------------------------------------------------------------------- when

    @When("{string} tries to order {string}")
    public void triesToOrder(String customerName, String mealName) {
        preview = context.app().orderService().preview(context.customer(), context.meal());
        context.setConflicts(preview.conflicts());
    }

    @When("{string} tries to place the order for {string} without changes")
    public void triesToPlaceTheOrderWithoutChanges(String customerName, String mealName) {
        context.attempt(() -> context.app().orderService()
                .place(context.customer(), context.meal()));
    }

    @When("{string} modifies the meal and replaces {string} with {string}")
    public void modifiesTheMealAndReplaces(String customerName, String original, String replacement) {
        context.setSubstitutions(Map.of(original.toLowerCase(), replacement.toLowerCase()));
    }

    @When("the chef reviews the order")
    public void theChefReviewsTheOrder() {
        // The chef's decision arrives from the interface layer. The domain no longer opens a
        // Scanner on System.in, so no setTestAutoApprove flag is needed to get past this step.
        assertTrue(context.order().requiresApproval(),
                "Order should be waiting for chef review");
    }

    @And("approves the substitutions")
    public void approvesTheSubstitutions() {
        context.app().kitchenService().approve(context.order());
    }

    // ------------------------------------------------------------------- then

    @Then("the system should warn about the allergy")
    public void theSystemShouldWarnAboutTheAllergy() {
        assertNotNull(preview, "No order preview was produced");
        assertTrue(preview.hasAllergyConflict(),
                "Expected an allergy warning; conflicts were " + preview.conflicts());
        assertTrue(preview.conflicts().stream()
                        .anyMatch(conflict -> conflict.type() == ConflictType.ALLERGY
                                && conflict.ingredient().equalsIgnoreCase("shrimp")),
                "Expected the warning to name the allergen");
    }

    @And("the system should suggest modifying the meal")
    public void theSystemShouldSuggestModifyingTheMeal() {
        assertFalse(preview.substitutions().isEmpty(),
                "No substitution was proposed for a meal the customer cannot eat");
        for (Map.Entry<String, String> entry : preview.substitutions().entrySet()) {
            assertTrue(context.app().ruleEngine()
                            .isAcceptable(entry.getValue(), context.customer()),
                    "Proposed \"" + entry.getValue() + "\" which the customer still cannot eat");
        }
    }

    @Then("the order should be refused because of the allergy")
    public void theOrderShouldBeRefusedBecauseOfTheAllergy() {
        Exception thrown = context.thrownException();
        assertNotNull(thrown, "An allergic customer was allowed to order the meal unmodified");
        assertInstanceOf(IllegalArgumentException.class, thrown);
        assertTrue(context.app().orderService().repository().findAll().isEmpty(),
                "A refused order should not be stored");
    }

    @Then("the modified meal should no longer conflict")
    public void theModifiedMealShouldNoLongerConflict() {
        Map<String, Integer> recipe = context.meal().recipeWith(context.substitutions());
        List<Conflict> remaining = context.app().ruleEngine()
                .check(recipe.keySet(), context.customer());
        assertTrue(remaining.isEmpty(),
                "Modified meal still conflicts: " + remaining);
    }

    @And("the order should be placed with the new ingredients")
    public void theOrderShouldBePlacedWithTheNewIngredients() {
        Order order = context.app().orderService()
                .place(context.customer(), context.meal(), context.substitutions());
        context.setOrder(order);

        Map<String, Integer> recipe = order.effectiveRecipe();
        for (Map.Entry<String, String> substitution : context.substitutions().entrySet()) {
            assertTrue(recipe.containsKey(substitution.getValue()),
                    "Order was cooked without the replacement ingredient; recipe was " + recipe);
            assertFalse(recipe.containsKey(substitution.getKey()),
                    "Order still contains the ingredient that was replaced");
        }
        assertTrue(order.getAssignedChefId().isPresent(), "Order was not given to a chef");
    }

    @Then("the system should mark the order as ready to cook")
    public void theSystemShouldMarkTheOrderAsReadyToCook() {
        assertEquals(OrderStatus.APPROVED, context.order().getStatus());
        assertFalse(context.order().requiresApproval());
    }

    @Then("the order should not require chef approval")
    public void theOrderShouldNotRequireChefApproval() {
        assertFalse(context.order().requiresApproval());
        assertEquals(OrderStatus.PENDING, context.order().getStatus());
    }
}
