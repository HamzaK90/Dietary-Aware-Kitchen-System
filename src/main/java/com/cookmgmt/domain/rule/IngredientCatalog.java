package com.cookmgmt.domain.rule;

import com.cookmgmt.support.Text;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reference data describing what each ingredient is: {@code beef} is {@code meat} and
 * {@code non-vegetarian}, {@code bun} contains {@code gluten}, and so on.
 *
 * <p>This replaces the {@code ingredientTags} map that was a private constant inside the console
 * {@code Main} class. Two defects came out of that arrangement:
 *
 * <ol>
 *   <li><b>Case mismatch.</b> Two entries were registered capitalised ({@code "Beef"} and
 *       {@code "Shrimp"}) while every lookup lower-cased its key first. Both lookups therefore fell
 *       through to the empty default and <em>no dietary rule ever fired for beef or shrimp</em> -
 *       the two ingredients the feature files use as their main examples. Keys are now normalised
 *       on the way in, so registration and lookup cannot disagree.</li>
 *   <li><b>Wrong tag consulted.</b> The vegetarian check tested for the {@code meat} tag, but
 *       shrimp was only tagged {@code seafood} and {@code non-vegetarian}. Shrimp consequently
 *       passed the vegetarian check. Rules now test the {@code non-vegetarian} tag directly.</li>
 * </ol>
 *
 * <p>Reference data living in its own class also means a new ingredient no longer requires editing
 * a user-interface file.
 */
public final class IngredientCatalog {

    /**
     * Role tags: the culinary job an ingredient does in a recipe.
     *
     * <p>These drive substitution suggestions. The old
     * {@code Inventory.getAlternativeIngredients} returned <em>every other ingredient in stock</em>
     * that the customer could eat, so a customer allergic to milk was offered "bun" as a
     * replacement for it. Restricting candidates to the same role keeps a protein being swapped for
     * a protein and a milk for a milk.
     */
    public static final String ROLE_PROTEIN = "role-protein";
    public static final String ROLE_MILK = "role-milk";
    public static final String ROLE_CHEESE = "role-cheese";
    public static final String ROLE_STARCH = "role-starch";
    public static final String ROLE_PRODUCE = "role-produce";
    public static final String ROLE_TOPPING = "role-topping";

    /** Every role tag, used to pick out the role portion of an ingredient's tag set. */
    public static final Set<String> ROLES = Set.of(
            ROLE_PROTEIN, ROLE_MILK, ROLE_CHEESE, ROLE_STARCH, ROLE_PRODUCE, ROLE_TOPPING);

    private final Map<String, Set<String>> tagsByIngredient;

    public IngredientCatalog(Map<String, Set<String>> tagsByIngredient) {
        Map<String, Set<String>> normalized = new LinkedHashMap<>();
        tagsByIngredient.forEach((ingredient, tags) -> {
            Set<String> normalizedTags = new LinkedHashSet<>();
            tags.forEach(tag -> normalizedTags.add(Text.normalize(tag)));
            normalized.put(Text.normalize(ingredient), normalizedTags);
        });
        this.tagsByIngredient = normalized;
    }

    /** @return the tags for an ingredient, or an empty set if it is not catalogued */
    public Set<String> tagsOf(String ingredient) {
        return tagsByIngredient.getOrDefault(Text.normalize(ingredient), Set.of());
    }

    public boolean hasTag(String ingredient, String tag) {
        return tagsOf(ingredient).contains(Text.normalize(tag));
    }

    public boolean isKnown(String ingredient) {
        return tagsByIngredient.containsKey(Text.normalize(ingredient));
    }

    public Set<String> knownIngredients() {
        return Collections.unmodifiableSet(tagsByIngredient.keySet());
    }

    /**
     * The culinary roles an ingredient fills, for example {@code role-protein}.
     *
     * @return the subset of this ingredient's tags that are role tags; empty if uncatalogued
     */
    public Set<String> rolesOf(String ingredient) {
        Set<String> roles = new LinkedHashSet<>(tagsOf(ingredient));
        roles.retainAll(ROLES);
        return roles;
    }

    /** @return {@code true} if the two ingredients do the same culinary job */
    public boolean sharesRole(String ingredient, String other) {
        Set<String> roles = rolesOf(ingredient);
        return !roles.isEmpty() && !Collections.disjoint(roles, rolesOf(other));
    }

    /** Registers or replaces the tags for one ingredient. */
    public void register(String ingredient, String... tags) {
        Set<String> normalizedTags = new LinkedHashSet<>();
        Arrays.stream(tags).map(Text::normalize).forEach(normalizedTags::add);
        tagsByIngredient.put(Text.normalize(ingredient), normalizedTags);
    }

    /**
     * The catalogue the application ships with.
     *
     * <p>Carried over from the original data with two corrections:
     * <ul>
     *   <li>{@code beef} is no longer tagged {@code non-halal}. Beef is halal when slaughtered
     *       correctly; only pork and gelatin are inherently not. As written, every halal customer
     *       was blocked from every beef dish.</li>
     *   <li>Ingredients that only appeared as suggested replacements in the feature files
     *       ({@code almond milk}, {@code oat milk}, {@code mushroom}, {@code chickpeas}) are now
     *       catalogued, so the substitution engine can actually propose them.</li>
     * </ul>
     */
    public static IngredientCatalog defaultCatalog() {
        Map<String, Set<String>> tags = new LinkedHashMap<>();

        // --- proteins -------------------------------------------------------
        tags.put("chicken", Set.of(ROLE_PROTEIN, "meat", "poultry", "non-vegetarian", "non-vegan"));
        tags.put("beef", Set.of(ROLE_PROTEIN, "meat", "non-vegetarian", "non-vegan"));
        tags.put("pork", Set.of(ROLE_PROTEIN, "meat", "non-halal", "non-kosher", "non-vegetarian", "non-vegan"));
        tags.put("shrimp", Set.of(ROLE_PROTEIN, "seafood", "non-kosher", "non-vegetarian", "non-vegan"));
        tags.put("egg", Set.of(ROLE_PROTEIN, "animal-product", "vegetarian", "non-vegan"));
        tags.put("tofu", Set.of(ROLE_PROTEIN, "plant-based", "vegetarian", "vegan", "halal", "kosher"));
        tags.put("chickpeas", Set.of(ROLE_PROTEIN, "plant-based", "vegetarian", "vegan", "halal", "kosher"));
        tags.put("gelatin", Set.of("animal-product", "non-halal", "non-vegetarian", "non-vegan"));

        // --- milks ----------------------------------------------------------
        tags.put("milk", Set.of(ROLE_MILK, "dairy", "vegetarian", "non-vegan"));
        tags.put("almond milk", Set.of(ROLE_MILK, "plant-based", "nuts", "vegetarian", "vegan"));
        tags.put("oat milk", Set.of(ROLE_MILK, "plant-based", "vegetarian", "vegan"));
        tags.put("cheese", Set.of(ROLE_CHEESE, "dairy", "vegetarian", "non-vegan"));
        tags.put("vegan cheese", Set.of(ROLE_CHEESE, "plant-based", "vegetarian", "vegan"));

        // --- starches -------------------------------------------------------
        tags.put("flour", Set.of(ROLE_STARCH, "gluten", "vegetarian", "vegan"));
        tags.put("bun", Set.of(ROLE_STARCH, "gluten", "vegetarian", "vegan"));
        tags.put("rice", Set.of(ROLE_STARCH, "vegetarian", "vegan", "halal", "kosher"));

        // --- produce and extras ---------------------------------------------
        tags.put("lettuce", Set.of(ROLE_PRODUCE, "vegetarian", "vegan"));
        tags.put("tomato", Set.of(ROLE_PRODUCE, "vegetarian", "vegan"));
        // Mushroom sits in both roles: it is produce, and it is a conventional meat replacement,
        // which is what lets it be offered when a protein is unavailable or unsuitable.
        tags.put("mushroom", Set.of(ROLE_PRODUCE, ROLE_PROTEIN, "plant-based", "vegetarian", "vegan"));
        tags.put("nuts", Set.of(ROLE_TOPPING, "nuts", "vegetarian", "vegan"));
        tags.put("honey", Set.of(ROLE_TOPPING, "animal-product", "vegetarian", "non-vegan"));

        return new IngredientCatalog(tags);
    }
}
