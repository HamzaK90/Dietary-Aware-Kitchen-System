package com.cookmgmt.domain;

import com.cookmgmt.support.Text;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A dish on the menu, defined by its recipe: how much of each ingredient it needs.
 *
 * <p>Ingredient keys are normalised with {@link Text#normalize(String)} on the way in, so
 * {@code "Beef"}, {@code "beef"} and {@code " BEEF "} are the same ingredient everywhere in the
 * system. Inconsistent casing between the ingredient tag table and the lookups against it was the
 * cause of a defect where beef and shrimp silently passed every dietary check.
 *
 * <p>A meal always has at least one ingredient. Building one up field by field goes through
 * {@link #builder()} rather than leaving a half-formed object reachable.
 */
public class Meal extends Entity {

    /** Longest cooking time accepted, in minutes. */
    public static final int MAX_COOKING_MINUTES = 300;

    private String name;
    private Map<String, Integer> ingredients;
    private int cookingTimeMinutes;

    private Meal(String name, Map<String, Integer> ingredients, int cookingTimeMinutes) {
        this.name = Text.requireText(name, "Meal name");
        this.ingredients = validatedRecipe(ingredients);
        this.cookingTimeMinutes = validatedCookingTime(cookingTimeMinutes);
    }

    public static Meal of(String name, Map<String, Integer> ingredients, int cookingTimeMinutes) {
        return new Meal(name, ingredients, cookingTimeMinutes);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Map<String, Integer> validatedRecipe(Map<String, Integer> source) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("A meal must have at least one ingredient");
        }
        // LinkedHashMap keeps a stable iteration order, so prices, substitution suggestions and
        // on-screen listings are reproducible instead of varying with HashMap bucket layout.
        Map<String, Integer> recipe = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String ingredient = Text.normalize(entry.getKey());
            if (ingredient.isEmpty()) {
                throw new IllegalArgumentException("Ingredient name must not be blank");
            }
            Integer quantity = entry.getValue();
            if (quantity == null || quantity <= 0) {
                throw new IllegalArgumentException(
                        "Quantity for \"" + ingredient + "\" must be greater than zero");
            }
            recipe.put(ingredient, quantity);
        }
        return recipe;
    }

    private static int validatedCookingTime(int minutes) {
        if (minutes <= 0 || minutes > MAX_COOKING_MINUTES) {
            throw new IllegalArgumentException(
                    "Cooking time must be between 1 and " + MAX_COOKING_MINUTES + " minutes");
        }
        return minutes;
    }

    /**
     * This meal's recipe rewritten with the given substitutions applied.
     *
     * <p>Pricing and stock reservation both need the ingredients that will actually be used rather
     * than the ones originally listed. Keeping that single translation here stops the two from
     * drifting apart, which is how an order could previously be priced on one set of ingredients
     * while a different set was deducted from stock.
     *
     * @param substitutions original ingredient to replacement ingredient
     * @return quantities keyed by the ingredient that will really be consumed
     */
    public Map<String, Integer> recipeWith(Map<String, String> substitutions) {
        if (substitutions == null || substitutions.isEmpty()) {
            return getIngredients();
        }
        Map<String, Integer> effective = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : ingredients.entrySet()) {
            String replacement = substitutions.get(entry.getKey());
            String ingredient = replacement == null ? entry.getKey() : Text.normalize(replacement);
            // Merge, so substituting one ingredient onto another already in the recipe adds up
            // rather than silently discarding a quantity.
            effective.merge(ingredient, entry.getValue(), Integer::sum);
        }
        return effective;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Text.requireText(name, "Meal name");
    }

    /** @return an unmodifiable view of the recipe, keyed by normalised ingredient name */
    public Map<String, Integer> getIngredients() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(ingredients));
    }

    public void setIngredients(Map<String, Integer> ingredients) {
        this.ingredients = validatedRecipe(ingredients);
    }

    public Set<String> ingredientNames() {
        return getIngredients().keySet();
    }

    public int quantityOf(String ingredient) {
        return ingredients.getOrDefault(Text.normalize(ingredient), 0);
    }

    public boolean contains(String ingredient) {
        return ingredients.containsKey(Text.normalize(ingredient));
    }

    public int getCookingTimeMinutes() {
        return cookingTimeMinutes;
    }

    public void setCookingTimeMinutes(int cookingTimeMinutes) {
        this.cookingTimeMinutes = validatedCookingTime(cookingTimeMinutes);
    }

    @Override
    public String toString() {
        return name + " (" + cookingTimeMinutes + " min, " + ingredients.size() + " ingredients)";
    }

    /**
     * Assembles a {@link Meal} incrementally.
     *
     * <p>The admin screens collect a name, then ingredients one at a time, then a cooking time.
     * The builder holds that partial state so the {@code Meal} itself is never observable in an
     * invalid form, and validation happens once at {@link #build()}.
     */
    public static final class Builder {

        private final Map<String, Integer> ingredients = new LinkedHashMap<>();
        private String name;
        private int cookingTimeMinutes = 1;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder ingredient(String ingredient, int quantity) {
            this.ingredients.put(Text.normalize(ingredient), quantity);
            return this;
        }

        public Builder ingredients(Map<String, Integer> source) {
            source.forEach(this::ingredient);
            return this;
        }

        public Builder cookingTimeMinutes(int minutes) {
            this.cookingTimeMinutes = minutes;
            return this;
        }

        public boolean hasIngredients() {
            return !ingredients.isEmpty();
        }

        public Meal build() {
            return new Meal(name, ingredients, cookingTimeMinutes);
        }
    }
}
