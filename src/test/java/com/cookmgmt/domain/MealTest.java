package com.cookmgmt.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Meal")
class MealTest {

    @Test
    @DisplayName("normalises ingredient names so casing cannot split an ingredient in two")
    void normalisesIngredientNames() {
        Meal meal = Meal.of("Burger", Map.of("  BEEF  ", 1), 20);

        assertTrue(meal.contains("beef"));
        assertTrue(meal.contains("Beef"));
        assertEquals(1, meal.quantityOf("BEEF"));
    }

    @Test
    @DisplayName("rejects a meal with no ingredients")
    void rejectsAnEmptyRecipe() {
        // The old edit screen started from an empty map and wrote it back if the user typed
        // "done" straight away, silently leaving a meal on the menu with no recipe at all.
        assertThrows(IllegalArgumentException.class, () -> Meal.of("Ghost", Map.of(), 10));
        assertThrows(IllegalArgumentException.class,
                () -> Meal.of("Burger", Map.of("beef", 1), 20).setIngredients(new HashMap<>()));
    }

    @Test
    @DisplayName("rejects non-positive quantities and cooking times")
    void rejectsInvalidNumbers() {
        assertThrows(IllegalArgumentException.class, () -> Meal.of("Burger", Map.of("beef", 0), 20));
        assertThrows(IllegalArgumentException.class, () -> Meal.of("Burger", Map.of("beef", -1), 20));
        assertThrows(IllegalArgumentException.class, () -> Meal.of("Burger", Map.of("beef", 1), 0));
        assertThrows(IllegalArgumentException.class, () -> Meal.of("Burger", Map.of("beef", 1), 999));
    }

    @Test
    @DisplayName("rejects a blank name")
    void rejectsABlankName() {
        assertThrows(IllegalArgumentException.class, () -> Meal.of("  ", Map.of("beef", 1), 20));
    }

    @Test
    @DisplayName("the recipe cannot be modified through the getter")
    void recipeGetterIsUnmodifiable() {
        Meal meal = Meal.of("Burger", Map.of("beef", 1), 20);

        assertThrows(UnsupportedOperationException.class,
                () -> meal.getIngredients().put("cheese", 1));
    }

    @Test
    @DisplayName("mutating the map passed in does not change the meal")
    void defensivelyCopiesTheRecipeOnTheWayIn() {
        Map<String, Integer> source = new HashMap<>();
        source.put("beef", 1);
        Meal meal = Meal.of("Burger", source, 20);

        source.put("cheese", 5);

        assertFalse(meal.contains("cheese"));
    }

    @Test
    @DisplayName("applies substitutions to produce the recipe that will really be cooked")
    void appliesSubstitutions() {
        Meal burger = Meal.of("Burger", Map.of("beef", 1, "bun", 2), 20);

        Map<String, Integer> effective = burger.recipeWith(Map.of("beef", "tofu"));

        assertEquals(Map.of("tofu", 1, "bun", 2), effective);
        assertTrue(burger.contains("beef"), "the meal itself is unchanged");
    }

    @Test
    @DisplayName("merges quantities when substituting onto an ingredient already present")
    void mergesQuantitiesOnCollision() {
        Meal mixed = Meal.of("Mixed", Map.of("beef", 1, "tofu", 2), 20);

        assertEquals(Map.of("tofu", 3), mixed.recipeWith(Map.of("beef", "tofu")));
    }

    @Test
    @DisplayName("an empty substitution map leaves the recipe unchanged")
    void emptySubstitutionsLeaveRecipeUnchanged() {
        Meal burger = Meal.of("Burger", Map.of("beef", 1), 20);

        assertEquals(burger.getIngredients(), burger.recipeWith(Map.of()));
        assertEquals(burger.getIngredients(), burger.recipeWith(null));
    }

    @Test
    @DisplayName("the builder assembles a meal step by step and validates once at the end")
    void builderAssemblesStepByStep() {
        Meal.Builder builder = Meal.builder().name("Falafel Plate");
        assertFalse(builder.hasIngredients());

        Meal meal = builder
                .ingredient("Chickpeas", 2)
                .ingredient("Lettuce", 1)
                .cookingTimeMinutes(20)
                .build();

        assertEquals("Falafel Plate", meal.getName());
        assertEquals(2, meal.quantityOf("chickpeas"));
        assertEquals(20, meal.getCookingTimeMinutes());
    }

    @Test
    @DisplayName("the builder refuses to produce a meal with no ingredients")
    void builderRefusesEmptyMeal() {
        assertThrows(IllegalArgumentException.class,
                () -> Meal.builder().name("Ghost").cookingTimeMinutes(10).build());
    }
}
