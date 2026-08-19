package com.cookmgmt.domain.rule;

import com.cookmgmt.domain.Conflict;
import com.cookmgmt.domain.Customer;

import java.util.Optional;

/**
 * Base for every rule that works the same way: the customer holds a preference, and any ingredient
 * carrying a particular tag violates it.
 *
 * <p>Each concrete diet stays a separate, nameable class - which is what makes the Open/Closed
 * story legible - while the one line of shared logic is written once here.
 */
public abstract class TagBasedDietaryRule implements DietaryRule {

    private final String preference;
    private final String forbiddenTag;
    private final String explanation;

    /**
     * @param preference   the customer preference that activates this rule, for example {@code "vegan"}
     * @param forbiddenTag the ingredient tag that violates it, for example {@code "non-vegan"}
     * @param explanation  wording shown to the user when the rule fires
     */
    protected TagBasedDietaryRule(String preference, String forbiddenTag, String explanation) {
        this.preference = preference;
        this.forbiddenTag = forbiddenTag;
        this.explanation = explanation;
    }

    @Override
    public final String preferenceName() {
        return preference;
    }

    /** The ingredient tag this rule rejects. Exposed so substitution search can avoid it. */
    public final String forbiddenTag() {
        return forbiddenTag;
    }

    @Override
    public boolean appliesTo(Customer customer) {
        return customer.prefers(preference);
    }

    @Override
    public Optional<Conflict> check(String ingredient, Customer customer, IngredientCatalog catalog) {
        if (catalog.hasTag(ingredient, forbiddenTag)) {
            return Optional.of(Conflict.dietary(ingredient, preference, explanation));
        }
        return Optional.empty();
    }
}
