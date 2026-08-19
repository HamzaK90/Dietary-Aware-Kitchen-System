package com.cookmgmt.domain.rule;

import com.cookmgmt.domain.Conflict;
import com.cookmgmt.domain.Customer;

import java.util.Optional;

/**
 * One reason an ingredient might be unsuitable for a customer.
 *
 * <p>This interface is the project's Open/Closed demonstration. Conflict detection was previously
 * a chain of {@code else if} branches inside the console class:
 *
 * <pre>{@code
 * if (pref.equals("vegan") && tags.contains("non-vegan")) { ... }
 * else if (pref.equals("vegetarian") && tags.contains("meat")) { ... }
 * else if (pref.equals("halal") && tags.contains("non-halal")) { ... }
 * else if (pref.equals("gluten-free") && tags.contains("gluten")) { ... }
 * }</pre>
 *
 * <p>Supporting kosher or dairy-free meant editing that method, recompiling the user interface and
 * re-testing every existing diet. The {@code else if} chain also made the branches mutually
 * exclusive, so a customer who was both vegan and gluten-free only ever saw the first clash.
 *
 * <p>Now a new diet is a new class handed to {@link DietaryRuleEngine}; no existing file changes,
 * and every applicable rule is evaluated rather than just the first match.
 */
public interface DietaryRule {

    /** Short name used in messages and documentation, for example {@code "vegan"}. */
    String preferenceName();

    /**
     * Whether this rule is relevant to the given customer at all - typically because they hold the
     * matching dietary preference, or in the case of allergies because they have any.
     */
    boolean appliesTo(Customer customer);

    /**
     * Evaluates one ingredient.
     *
     * @return the clash found, or {@link Optional#empty()} if this ingredient is acceptable
     */
    Optional<Conflict> check(String ingredient, Customer customer, IngredientCatalog catalog);
}
