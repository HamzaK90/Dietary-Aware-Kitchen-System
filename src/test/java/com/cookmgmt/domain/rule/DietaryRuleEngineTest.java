package com.cookmgmt.domain.rule;

import com.cookmgmt.domain.Conflict;
import com.cookmgmt.domain.ConflictType;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Meal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DietaryRuleEngine")
class DietaryRuleEngineTest {

    private final DietaryRuleEngine engine = DietaryRuleEngine.withDefaults();

    private static Customer customerWith(List<String> preferences, List<String> allergies) {
        return new Customer("Test Diner", "diner@example.com", preferences, allergies);
    }

    private static Meal mealOf(String ingredient) {
        return Meal.of("Test Meal", Map.of(ingredient, 1), 10);
    }

    @Nested
    @DisplayName("regression: ingredients that the capitalised tag table made invisible")
    class CapitalisationRegression {

        /*
         * The old table registered two entries with capital letters:
         *     Map.entry("Beef",   List.of("meat", "non-vegetarian", "non-vegan", "non-halal")),
         *     Map.entry("Shrimp", List.of("seafood", "non-vegetarian", "non-vegan")),
         * while the lookup was always:
         *     ingredientTags.getOrDefault(ing.toLowerCase(), List.of())
         *
         * Both lookups therefore returned the empty default and NO dietary rule fired for beef or
         * shrimp - the two ingredients the feature files use as their principal examples. An
         * ingredient with no tags simply looks acceptable, so nothing reported a problem.
         */

        @ParameterizedTest(name = "{0} conflicts with a {1} diet")
        @CsvSource({
                "beef,    vegan",
                "beef,    vegetarian",
                "shrimp,  vegan",
                "shrimp,  vegetarian",
                "shrimp,  kosher"
        })
        @DisplayName("beef and shrimp are now caught")
        void beefAndShrimpAreCaught(String ingredient, String preference) {
            List<Conflict> conflicts =
                    engine.check(mealOf(ingredient), customerWith(List.of(preference), List.of()));
            assertFalse(conflicts.isEmpty(),
                    ingredient + " should conflict with a " + preference + " diet");
        }

        @ParameterizedTest(name = "the tag table finds \"{0}\"")
        @ValueSource(strings = {"beef", "Beef", "BEEF", " Beef "})
        @DisplayName("tags resolve regardless of the casing used to look them up")
        void tagsResolveRegardlessOfCasing(String spelling) {
            assertTrue(engine.getCatalog().hasTag(spelling, "non-vegan"));
        }

        @Test
        @DisplayName("shrimp is non-vegetarian even though it is not tagged as meat")
        void shrimpIsNonVegetarianEvenThoughNotMeat() {
            // The old vegetarian check tested for the "meat" tag. Shrimp was tagged "seafood" and
            // "non-vegetarian" but not "meat", so it passed the vegetarian check unchallenged.
            assertFalse(engine.getCatalog().hasTag("shrimp", "meat"),
                    "precondition: shrimp is not tagged as meat");
            assertTrue(engine.getCatalog().hasTag("shrimp", "non-vegetarian"));

            assertFalse(engine.check(mealOf("shrimp"),
                    customerWith(List.of("vegetarian"), List.of())).isEmpty());
        }
    }

    @Nested
    @DisplayName("diet rules")
    class Diets {

        @ParameterizedTest(name = "{1} diet rejects {0}")
        @CsvSource({
                "milk,    vegan",
                "cheese,  vegan",
                "egg,     vegan",
                "honey,   vegan",
                "chicken, vegetarian",
                "pork,    halal",
                "pork,    kosher",
                "flour,   gluten-free",
                "bun,     gluten-free",
                "milk,    dairy-free"
        })
        @DisplayName("rejects unsuitable ingredients")
        void rejectsUnsuitableIngredients(String ingredient, String preference) {
            assertFalse(engine.check(mealOf(ingredient),
                    customerWith(List.of(preference), List.of())).isEmpty());
        }

        @ParameterizedTest(name = "{1} diet accepts {0}")
        @CsvSource({
                "tofu,    vegan",
                "rice,    vegan",
                "lettuce, vegetarian",
                "egg,     vegetarian",
                "beef,    halal",
                "rice,    gluten-free",
                "tofu,    dairy-free"
        })
        @DisplayName("accepts suitable ingredients")
        void acceptsSuitableIngredients(String ingredient, String preference) {
            assertTrue(engine.check(mealOf(ingredient),
                    customerWith(List.of(preference), List.of())).isEmpty());
        }

        @Test
        @DisplayName("beef is halal, so a halal customer is not blocked from beef dishes")
        void beefIsHalal() {
            // The original data tagged beef "non-halal", which blocked every halal customer from
            // every beef dish. Only pork and gelatin are inherently not halal.
            assertFalse(engine.getCatalog().hasTag("beef", "non-halal"));
            assertTrue(engine.check(mealOf("beef"),
                    customerWith(List.of("halal"), List.of())).isEmpty());
        }
    }

    @Nested
    @DisplayName("allergies")
    class Allergies {

        @Test
        @DisplayName("reports an allergy conflict for a listed allergen")
        void reportsAllergyConflict() {
            List<Conflict> conflicts = engine.check(mealOf("nuts"),
                    customerWith(List.of(), List.of("Nuts")));

            assertEquals(1, conflicts.size());
            assertEquals(ConflictType.ALLERGY, conflicts.get(0).type());
            assertEquals("nuts", conflicts.get(0).ingredient());
        }

        @Test
        @DisplayName("matches allergens regardless of the casing the customer typed")
        void matchesAllergensCaseInsensitively() {
            assertFalse(engine.check(mealOf("milk"),
                    customerWith(List.of(), List.of("  MILK "))).isEmpty());
        }
    }

    @Test
    @DisplayName("reports every applicable conflict, not just the first")
    void reportsEveryConflict() {
        /*
         * The old implementation was an else-if chain:
         *     if (pref.equals("vegan") && ...) { }
         *     else if (pref.equals("gluten-free") && ...) { }
         * so a customer holding both diets was told about only one problem. Here "bun" is both
         * non-vegan (it is not) and gluten-bearing - it should surface as a gluten conflict, and
         * milk should surface separately as a vegan one.
         */
        Customer customer = customerWith(List.of("vegan", "gluten-free"), List.of());
        Meal meal = Meal.of("Milky Bun", Map.of("milk", 1, "bun", 1), 10);

        List<Conflict> conflicts = engine.check(meal, customer);

        assertTrue(conflicts.stream().anyMatch(c -> c.ingredient().equals("milk")),
                "vegan conflict on milk missing from " + conflicts);
        assertTrue(conflicts.stream().anyMatch(c -> c.ingredient().equals("bun")),
                "gluten conflict on bun missing from " + conflicts);
    }

    @Test
    @DisplayName("a customer with no restrictions has no conflicts")
    void unrestrictedCustomerHasNoConflicts() {
        Customer customer = customerWith(List.of(), List.of());
        assertTrue(engine.check(Meal.of("Anything", Map.of("pork", 1, "milk", 2), 10), customer)
                .isEmpty());
        assertTrue(engine.applicableRules(customer).isEmpty());
    }

    @Test
    @DisplayName("an uncatalogued ingredient raises no dietary conflict")
    void uncataloguedIngredientRaisesNoConflict() {
        assertTrue(engine.check(mealOf("unobtainium"),
                customerWith(List.of("vegan"), List.of())).isEmpty());
    }

    @Test
    @DisplayName("a new diet is added by supplying a rule, without touching existing code")
    void newDietIsAddedBySupplyingARule() {
        // The Open/Closed claim, executed: PescatarianRule is defined in this test file only, and
        // the engine applies it with no change to any production class.
        DietaryRule pescatarian = new TagBasedDietaryRule(
                "pescatarian", "meat", "it contains land meat") {
        };

        DietaryRuleEngine extended = new DietaryRuleEngine(
                IngredientCatalog.defaultCatalog(),
                List.of(new AllergyRule(), pescatarian));

        Customer customer = customerWith(List.of("pescatarian"), List.of());
        assertFalse(extended.check(mealOf("beef"), customer).isEmpty(),
                "beef is land meat and should conflict");
        assertTrue(extended.check(mealOf("shrimp"), customer).isEmpty(),
                "shrimp is seafood and should be acceptable");
    }

    @Test
    @DisplayName("isAcceptable filters candidate replacements")
    void isAcceptableFiltersCandidates() {
        Customer vegan = customerWith(List.of("vegan"), List.of());
        assertTrue(engine.isAcceptable("tofu", vegan));
        assertFalse(engine.isAcceptable("milk", vegan));
    }
}
