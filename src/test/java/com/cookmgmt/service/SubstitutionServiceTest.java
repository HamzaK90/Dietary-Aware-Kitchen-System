package com.cookmgmt.service;

import com.cookmgmt.app.SampleData;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.rule.DietaryRuleEngine;
import com.cookmgmt.domain.rule.IngredientCatalog;
import com.cookmgmt.inventory.InMemoryInventory;
import com.cookmgmt.inventory.MutableInventory;
import com.cookmgmt.notify.InMemoryNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SubstitutionService")
class SubstitutionServiceTest {

    private MutableInventory inventory;
    private DietaryRuleEngine ruleEngine;
    private SubstitutionService substitutions;

    @BeforeEach
    void setUp() {
        inventory = new InMemoryInventory();
        SampleData.loadInventory(new InventoryService(inventory, new InMemoryNotifier()));
        ruleEngine = DietaryRuleEngine.withDefaults();
        substitutions = new SubstitutionService(ruleEngine, inventory);
    }

    private static Customer customerWith(List<String> preferences, List<String> allergies) {
        return new Customer("Diner", "diner@example.com", preferences, allergies);
    }

    @Test
    @DisplayName("regression: suggestions do the same culinary job as the original")
    void suggestionsShareTheOriginalRole() {
        /*
         * getAlternativeIngredients() returned every other ingredient in stock that was not the
         * original, so a customer allergic to milk could be offered "bun" as its replacement.
         */
        Customer milkAllergic = customerWith(List.of(), List.of("milk"));

        List<String> suggested = substitutions.suggestFor("milk", milkAllergic, 1);

        assertFalse(suggested.isEmpty());
        for (String candidate : suggested) {
            assertTrue(ruleEngine.getCatalog().hasTag(candidate, IngredientCatalog.ROLE_MILK),
                    "\"" + candidate + "\" is not a milk");
        }
        assertFalse(suggested.contains("bun"));
        assertFalse(suggested.contains("rice"));
    }

    @Test
    @DisplayName("regression: suggestions respect the customer's diet")
    void suggestionsRespectTheCustomersDiet() {
        /*
         * The old filter was `!customer.getDietaryPreferences().contains(ingredient)`, which
         * compares a diet label such as "Vegan" against an ingredient name such as "beef". It
         * never matched, so dietary preferences had no effect on suggestions whatsoever.
         */
        Customer vegan = customerWith(List.of("Vegan"), List.of());

        List<String> suggested = substitutions.suggestFor("beef", vegan, 1);

        assertFalse(suggested.isEmpty());
        for (String candidate : suggested) {
            assertTrue(ruleEngine.isAcceptable(candidate, vegan),
                    "\"" + candidate + "\" is not vegan");
        }
        assertFalse(suggested.contains("chicken"));
        assertFalse(suggested.contains("pork"));
        assertTrue(suggested.contains("tofu"));
    }

    @RepeatedTest(5)
    @DisplayName("regression: the same inputs always give the same suggestion")
    void suggestionsAreDeterministic() {
        // The old code iterated a HashMap key set and took alternatives.get(0), so the suggestion
        // a customer saw could differ between two runs of the same scenario.
        Customer vegan = customerWith(List.of("Vegan"), List.of());

        assertEquals(substitutions.suggestFor("beef", vegan, 1),
                substitutions.suggestFor("beef", vegan, 1));
        assertEquals("chickpeas", substitutions.bestFor("beef", vegan, 1).orElseThrow(),
                "cheapest acceptable protein should win consistently");
    }

    @Test
    @DisplayName("never suggests an ingredient that is out of stock")
    void neverSuggestsOutOfStockIngredients() {
        inventory.consume("tofu", inventory.stockOf("tofu"));
        Customer vegan = customerWith(List.of("Vegan"), List.of());

        assertFalse(substitutions.suggestFor("beef", vegan, 1).contains("tofu"));
    }

    @Test
    @DisplayName("never suggests an ingredient with too little stock for the recipe")
    void respectsTheQuantityNeeded() {
        Customer vegan = customerWith(List.of("Vegan"), List.of());

        assertTrue(substitutions.suggestFor("beef", vegan, 1).contains("tofu"));
        assertFalse(substitutions.suggestFor("beef", vegan, 500).contains("tofu"));
    }

    @Test
    @DisplayName("never suggests the ingredient it is replacing")
    void neverSuggestsTheOriginal() {
        Customer diner = customerWith(List.of(), List.of());

        assertFalse(substitutions.suggestFor("tofu", diner, 1).contains("tofu"));
    }

    @Test
    @DisplayName("proposes a complete substitution set for a conflicting meal")
    void proposesACompleteSubstitutionSet() {
        Customer vegan = customerWith(List.of("Vegan"), List.of());
        Meal burger = Meal.of("Beef Burger", Map.of("beef", 1, "cheese", 1, "bun", 2), 20);

        Map<String, String> proposal = substitutions.proposeFor(burger, vegan);

        assertTrue(proposal.containsKey("beef"), "beef should be replaced");
        for (Map.Entry<String, String> entry : proposal.entrySet()) {
            assertTrue(ruleEngine.isAcceptable(entry.getValue(), vegan),
                    "proposed " + entry.getValue() + " which is still not vegan");
        }
        assertFalse(proposal.containsKey("bun"), "bun is already vegan and needs no change");
    }

    @Test
    @DisplayName("leaves an ingredient out of the proposal when nothing suitable exists")
    void leavesUnresolvableIngredientsOut() {
        // Better to report the meal as still unsuitable than to swap in something arbitrary.
        Customer vegan = customerWith(List.of("Vegan"), List.of());
        inventory.remove("almond milk");
        inventory.remove("oat milk");
        Meal latte = Meal.of("Latte", Map.of("milk", 1), 5);

        assertTrue(substitutions.proposeFor(latte, vegan).isEmpty());
    }

    @Test
    @DisplayName("suggests an in-stock alternative for an ingredient that has run out")
    void suggestsAlternativeForAShortage() {
        inventory.consume("tofu", inventory.stockOf("tofu"));
        Customer diner = customerWith(List.of(), List.of());

        List<String> suggested = substitutions.suggestForShortage("tofu", diner, 1);

        assertFalse(suggested.isEmpty());
        assertFalse(suggested.contains("tofu"));
        for (String candidate : suggested) {
            assertTrue(inventory.stockOf(candidate) > 0);
        }
    }
}
