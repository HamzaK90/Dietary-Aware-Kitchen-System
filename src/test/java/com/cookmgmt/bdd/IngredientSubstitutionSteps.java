package com.cookmgmt.bdd;

import com.cookmgmt.app.SampleData;
import com.cookmgmt.domain.Conflict;
import com.cookmgmt.domain.ConflictType;
import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.OrderStatus;
import com.cookmgmt.domain.rule.IngredientCatalog;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Steps for {@code ingredient_substitution.feature}. */
public class IngredientSubstitutionSteps {

    private final TestContext context;
    private int stockBeforeOrder;
    private String trackedIngredient;

    public IngredientSubstitutionSteps(TestContext context) {
        this.context = context;
    }

    // ------------------------------------------------------------------ given

    @Given("the customer has a dietary preference {string}")
    public void theCustomerHasADietaryPreference(String preference) {
        SampleData.loadInventory(context.app().inventoryService());
        context.givenCustomer("Sara", List.of(preference), List.of());
    }

    @Given("the customer has an allergy to {string}")
    public void theCustomerHasAnAllergyTo(String allergen) {
        SampleData.loadInventory(context.app().inventoryService());
        context.givenCustomer("Ali", List.of(), List.of(allergen));
    }

    @And("the meal {string} includes {string} as an ingredient")
    public void theMealIncludesAsAnIngredient(String mealName, String ingredient) {
        context.setMeal(context.app().catalogService()
                .addMeal(mealName, Map.of(ingredient.toLowerCase(), 1), 15));
    }

    @Given("a customer submitted a modified meal replacing {string} with {string}")
    public void aCustomerSubmittedAModifiedMeal(String original, String replacement) {
        placeOrderWithSubstitution(original, replacement, "Dana", List.of("Vegetarian"));
    }

    @Given("a customer replaces {string} with {string} in a meal")
    public void aCustomerReplacesInAMeal(String original, String replacement) {
        placeOrderWithSubstitution(original, replacement, "Zara", List.of("Halal"));
    }

    private void placeOrderWithSubstitution(String original, String replacement,
                                            String customerName, List<String> preferences) {
        SampleData.loadInventory(context.app().inventoryService());
        context.givenCustomer(customerName, preferences, List.of());
        context.givenChef("Chef Lina", "lina@kitchen.com");
        context.setMeal(context.app().catalogService()
                .addMeal("Modified Meal", Map.of(original.toLowerCase(), 1), 15));

        trackedIngredient = replacement.toLowerCase();
        stockBeforeOrder = context.app().inventory().stockOf(trackedIngredient);

        Map<String, String> substitutions = Map.of(original.toLowerCase(), replacement.toLowerCase());
        context.setSubstitutions(substitutions);
        context.setOrder(context.app().orderService()
                .place(context.customer(), context.meal(), substitutions));
    }

    @Given("the ingredient {string} is out of stock")
    public void theIngredientIsOutOfStock(String ingredient) {
        SampleData.loadInventory(context.app().inventoryService());
        context.app().inventory().consume(ingredient,
                context.app().inventory().stockOf(ingredient));
        context.givenCustomer("Sam", List.of(), List.of());
    }

    // ------------------------------------------------------------------- when

    @When("the customer attempts to order the {string}")
    public void theCustomerAttemptsToOrderThe(String mealName) {
        context.setConflicts(context.app().ruleEngine()
                .check(context.meal(), context.customer()));
        context.setSuggestions(List.of());
    }

    @When("a customer needs a replacement for {string}")
    public void aCustomerNeedsAReplacementFor(String ingredient) {
        context.setSuggestions(context.app().substitutionService()
                .suggestFor(ingredient.toLowerCase(), context.customer(), 1));
    }

    @When("the chef approves the substitutions")
    public void theChefApprovesTheSubstitutions() {
        context.app().kitchenService().approve(context.order());
    }

    @When("the chef rejects the substitutions")
    public void theChefRejectsTheSubstitutions() {
        context.app().kitchenService().reject(context.order());
    }

    // ------------------------------------------------------------------- then

    @Then("the system should warn that {string} is not vegan")
    public void theSystemShouldWarnThatIsNotVegan(String ingredient) {
        assertTrue(conflictExistsFor(ingredient),
                "No conflict reported for " + ingredient + "; conflicts were " + context.conflicts());
    }

    @Then("the system should warn about the milk allergy")
    public void theSystemShouldWarnAboutTheMilkAllergy() {
        assertTrue(context.conflicts().stream()
                        .anyMatch(conflict -> conflict.type() == ConflictType.ALLERGY),
                "Expected an allergy conflict; conflicts were " + context.conflicts());
    }

    @Then("the system should report a dietary conflict for {string}")
    public void theSystemShouldReportADietaryConflictFor(String ingredient) {
        // Beef and shrimp were the two ingredients the capitalised catalogue keys made invisible,
        // so these two scenarios are the direct regression test for that defect.
        assertTrue(context.conflicts().stream()
                        .anyMatch(conflict -> conflict.type() == ConflictType.DIETARY
                                && conflict.ingredient().equalsIgnoreCase(ingredient)),
                "No dietary conflict reported for " + ingredient
                        + "; conflicts were " + context.conflicts());
    }

    @And("it should suggest a vegan replacement for {string}")
    public void itShouldSuggestAVeganReplacementFor(String ingredient) {
        List<String> suggestions = context.app().substitutionService()
                .suggestFor(ingredient.toLowerCase(), context.customer(), 1);
        assertFalse(suggestions.isEmpty(), "No replacement offered for " + ingredient);
        // Every suggestion must genuinely satisfy the customer's diet - the old code offered any
        // other ingredient in stock without checking.
        for (String suggestion : suggestions) {
            assertTrue(context.app().ruleEngine().isAcceptable(suggestion, context.customer()),
                    "Suggested \"" + suggestion + "\" is not acceptable for this customer");
        }
        context.setSuggestions(suggestions);
    }

    @And("it should suggest {string} as a replacement for {string}")
    public void itShouldSuggestAsAReplacementFor(String expected, String ingredient) {
        List<String> suggestions = context.app().substitutionService()
                .suggestFor(ingredient.toLowerCase(), context.customer(), 1);
        assertTrue(suggestions.contains(expected.toLowerCase()),
                "Suggestions for " + ingredient + " were " + suggestions);
        context.setSuggestions(suggestions);
    }

    @Then("every suggested replacement for {string} should also be a milk")
    public void everySuggestedReplacementShouldAlsoBeAMilk(String ingredient) {
        List<String> suggestions = context.app().substitutionService()
                .suggestFor(ingredient.toLowerCase(), context.customer(), 1);
        assertFalse(suggestions.isEmpty(), "No suggestions to check");
        IngredientCatalog catalog = context.app().ruleEngine().getCatalog();
        for (String suggestion : suggestions) {
            // Guards against the old behaviour, which would happily offer "bun" for "milk".
            assertTrue(catalog.hasTag(suggestion, IngredientCatalog.ROLE_MILK),
                    "\"" + suggestion + "\" is not a milk");
        }
    }

    @Then("the chef should see a substitution alert")
    public void theChefShouldSeeASubstitutionAlert() {
        Order order = context.order();
        assertTrue(order.requiresApproval(), "Order should be awaiting approval");
        assertEquals(OrderStatus.NEEDS_APPROVAL, order.getStatus());
        assertTrue(context.notifier().anyContaining("needs your approval"),
                "Chef was not notified; notifications were " + context.notifier().all());
    }

    @Then("the order should be approved and ready to cook")
    public void theOrderShouldBeApprovedAndReadyToCook() {
        assertEquals(OrderStatus.APPROVED, context.order().getStatus());
        assertFalse(context.order().requiresApproval());
    }

    @Then("the order should be rejected")
    public void theOrderShouldBeRejected() {
        assertEquals(OrderStatus.REJECTED, context.order().getStatus());
        assertTrue(context.order().isRejected());
    }

    @And("the reserved {string} should have been returned to stock")
    public void theReservedShouldHaveBeenReturnedToStock(String ingredient) {
        // Nothing released reserved stock on rejection, so those ingredients used to be lost.
        assertEquals(stockBeforeOrder, context.app().inventory().stockOf(ingredient.toLowerCase()),
                "Stock of " + ingredient + " was not restored after rejection");
    }

    @Then("the system should suggest an alternative that is in stock")
    public void theSystemShouldSuggestAnAlternativeInStock() {
        List<String> suggestions = context.suggestions();
        assertFalse(suggestions.isEmpty(), "No alternatives suggested");
        for (String suggestion : suggestions) {
            assertTrue(context.app().inventory().stockOf(suggestion) > 0,
                    "Suggested \"" + suggestion + "\" but it is out of stock");
        }
    }

    @And("{string} should not be among the suggestions")
    public void shouldNotBeAmongTheSuggestions(String ingredient) {
        assertFalse(context.suggestions().contains(ingredient.toLowerCase()),
                "Out-of-stock ingredient was suggested as its own replacement");
    }

    @Then("the order should proceed with {string} instead of {string}")
    public void theOrderShouldProceedWith(String replacement, String original) {
        Map<String, String> substitutions = context.order().getSubstitutions();
        assertEquals(replacement.toLowerCase(), substitutions.get(original.toLowerCase()));
        Map<String, Integer> recipe = context.order().effectiveRecipe();
        assertTrue(recipe.containsKey(replacement.toLowerCase()),
                "Effective recipe was " + recipe);
        assertFalse(recipe.containsKey(original.toLowerCase()),
                "Effective recipe still contains " + original);
    }

    @And("the order should be priced using {string}")
    public void theOrderShouldBePricedUsing(String ingredient) {
        Money expected = context.app().inventory().priceOf(ingredient.toLowerCase());
        assertEquals(expected, context.app().pricingService().quote(context.order()));
    }

    private boolean conflictExistsFor(String ingredient) {
        return context.conflicts().stream()
                .map(Conflict::ingredient)
                .anyMatch(name -> name.equalsIgnoreCase(ingredient));
    }
}
