package com.cookmgmt.service;

import com.cookmgmt.domain.Conflict;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.rule.DietaryRuleEngine;
import com.cookmgmt.domain.rule.IngredientCatalog;
import com.cookmgmt.inventory.ReadableInventory;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Proposes replacements for ingredients a customer cannot eat or the kitchen has run out of.
 *
 * <p>The original suggestion logic was:
 *
 * <pre>{@code
 * for (String ingredient : stock.keySet()) {
 *     if (!ingredient.equals(original)
 *             && !customer.getAllergies().contains(ingredient)
 *             && !customer.getDietaryPreferences().contains(ingredient)) {
 *         alternatives.add(ingredient);
 *     }
 * }
 * ...
 * String choice = alternatives.get(0);   // "Simplified choice"
 * }</pre>
 *
 * <p>Three things were wrong with it:
 * <ul>
 *   <li><b>No notion of suitability.</b> Every other stocked ingredient qualified, so a customer
 *       allergic to milk could be offered "bun" as its replacement.</li>
 *   <li><b>Preferences compared against ingredient names.</b> {@code dietaryPreferences.contains(
 *       ingredient)} tests whether a diet label such as "Vegan" equals an ingredient name such as
 *       "beef". It never matched, so dietary rules had no effect on suggestions at all.</li>
 *   <li><b>Non-deterministic.</b> It iterated a {@link java.util.HashMap} key set and took the
 *       first element, so the suggestion a customer saw could differ between identical runs -
 *       which also made the behaviour impossible to test reliably.</li>
 * </ul>
 *
 * <p>Candidates are now filtered by culinary role, checked against the real
 * {@link DietaryRuleEngine}, required to be in stock, and ranked deterministically.
 */
public class SubstitutionService {

    private final DietaryRuleEngine ruleEngine;
    private final ReadableInventory inventory;

    public SubstitutionService(DietaryRuleEngine ruleEngine, ReadableInventory inventory) {
        this.ruleEngine = Objects.requireNonNull(ruleEngine, "ruleEngine");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    /**
     * Replacements for one ingredient, best first.
     *
     * <p>A candidate qualifies when it does the same culinary job as the original, is something
     * this customer can eat, and is actually in stock. Ranking prefers cheaper ingredients, then
     * better-stocked ones, then alphabetical order - so the same inputs always give the same
     * answer.
     *
     * @param quantity how many units the recipe needs, so a candidate with too little stock is
     *                 not offered
     */
    public List<String> suggestFor(String original, Customer customer, int quantity) {
        IngredientCatalog catalog = ruleEngine.getCatalog();
        return inventory.ingredients().stream()
                .filter(candidate -> !candidate.equals(original))
                .filter(candidate -> catalog.sharesRole(original, candidate))
                .filter(candidate -> ruleEngine.isAcceptable(candidate, customer))
                .filter(candidate -> inventory.stockOf(candidate) >= quantity)
                .sorted(Comparator
                        .comparing(inventory::priceOf)
                        .thenComparing(Comparator.comparingInt(inventory::stockOf).reversed())
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** Convenience for a single unit. */
    public List<String> suggestFor(String original, Customer customer) {
        return suggestFor(original, customer, 1);
    }

    public Optional<String> bestFor(String original, Customer customer, int quantity) {
        return suggestFor(original, customer, quantity).stream().findFirst();
    }

    /**
     * Proposes a complete substitution set that makes a meal suitable for a customer.
     *
     * <p>Only dietary clashes are resolved this way. An {@link com.cookmgmt.domain.ConflictType
     * #ALLERGY} is also included, because removing an allergen is exactly what a substitution is
     * for - but an ingredient with no acceptable replacement is left out of the map rather than
     * being swapped for something arbitrary, so the caller can see the meal is still unsuitable.
     *
     * @return original ingredient to replacement, containing only the ones that could be resolved
     */
    public Map<String, String> proposeFor(Meal meal, Customer customer) {
        Map<String, String> proposal = new LinkedHashMap<>();
        for (Conflict conflict : ruleEngine.check(meal, customer)) {
            String ingredient = conflict.ingredient();
            if (proposal.containsKey(ingredient)) {
                continue;
            }
            bestFor(ingredient, customer, meal.quantityOf(ingredient))
                    .ifPresent(replacement -> proposal.put(ingredient, replacement));
        }
        return proposal;
    }

    /**
     * Replacements for an ingredient the kitchen has run out of, ignoring dietary fit beyond what
     * the customer requires.
     */
    public List<String> suggestForShortage(String original, Customer customer, int quantity) {
        return suggestFor(original, customer, quantity);
    }
}
