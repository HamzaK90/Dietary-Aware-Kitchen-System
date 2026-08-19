package com.cookmgmt.domain.rule;

import com.cookmgmt.domain.Conflict;
import com.cookmgmt.domain.ConflictType;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Meal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Applies every {@link DietaryRule} that is relevant to a customer against every ingredient in a
 * meal, and reports what it finds.
 *
 * <p>Replaces {@code Main.checkMealConflicts}, which returned a {@code boolean} while printing its
 * findings straight to {@code System.out}. That signature meant the caller could learn <em>that</em>
 * something clashed but never <em>what</em>, so the GUI could not have shown the reasons and no
 * test could assert on them without capturing standard output.
 *
 * <p>This class is closed for modification: adding a diet means passing another rule in, not
 * editing anything here.
 */
public class DietaryRuleEngine {

    private final IngredientCatalog catalog;
    private final List<DietaryRule> rules;

    public DietaryRuleEngine(IngredientCatalog catalog, Collection<DietaryRule> rules) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.rules = List.copyOf(rules);
    }

    /** Engine wired with the default catalogue and the full set of shipped rules. */
    public static DietaryRuleEngine withDefaults() {
        return new DietaryRuleEngine(IngredientCatalog.defaultCatalog(), defaultRules());
    }

    /**
     * Every rule the application ships with.
     *
     * <p>The only place that changes when a diet is added.
     */
    public static List<DietaryRule> defaultRules() {
        return List.of(
                new AllergyRule(),
                new VeganRule(),
                new VegetarianRule(),
                new HalalRule(),
                new KosherRule(),
                new GlutenFreeRule(),
                new DairyFreeRule());
    }

    /**
     * Checks a whole meal.
     *
     * @return every clash found, in ingredient order then rule order; empty when the meal is safe
     */
    public List<Conflict> check(Meal meal, Customer customer) {
        return check(meal.ingredientNames(), customer);
    }

    /**
     * Checks an explicit ingredient list, which lets callers evaluate a meal <em>after</em>
     * substitutions have been applied rather than only as originally written.
     */
    public List<Conflict> check(Collection<String> ingredients, Customer customer) {
        Objects.requireNonNull(customer, "customer");
        List<DietaryRule> applicable = applicableRules(customer);
        List<Conflict> conflicts = new ArrayList<>();
        for (String ingredient : ingredients) {
            for (DietaryRule rule : applicable) {
                // Every applicable rule runs. The old else-if chain stopped at the first match,
                // so a customer who was both vegan and gluten-free saw only one of two problems.
                rule.check(ingredient, customer, catalog).ifPresent(conflicts::add);
            }
        }
        return conflicts;
    }

    /** Rules relevant to this customer, so a meal is not tested against diets nobody holds. */
    public List<DietaryRule> applicableRules(Customer customer) {
        return rules.stream().filter(rule -> rule.appliesTo(customer)).toList();
    }

    public boolean hasConflicts(Meal meal, Customer customer) {
        return !check(meal, customer).isEmpty();
    }

    /** @return {@code true} if any clash is an allergy, which is never safe to order through */
    public boolean hasAllergyConflict(Meal meal, Customer customer) {
        return check(meal, customer).stream()
                .anyMatch(conflict -> conflict.type() == ConflictType.ALLERGY);
    }

    /**
     * Whether a single ingredient is acceptable for a customer. Used by
     * {@link com.cookmgmt.service.SubstitutionService} to filter candidate replacements.
     */
    public boolean isAcceptable(String ingredient, Customer customer) {
        return applicableRules(customer).stream()
                .allMatch(rule -> rule.check(ingredient, customer, catalog).isEmpty());
    }

    public IngredientCatalog getCatalog() {
        return catalog;
    }

    public List<DietaryRule> getRules() {
        return rules;
    }
}
