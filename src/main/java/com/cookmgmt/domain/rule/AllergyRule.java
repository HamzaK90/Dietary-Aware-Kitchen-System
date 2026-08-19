package com.cookmgmt.domain.rule;

import com.cookmgmt.domain.Conflict;
import com.cookmgmt.domain.Customer;

import java.util.Optional;

/**
 * Rejects any ingredient on the customer's own allergy list.
 *
 * <p>Unlike the diet rules this one consults the customer rather than the ingredient catalogue, so
 * it implements {@link DietaryRule} directly instead of extending
 * {@link TagBasedDietaryRule}. {@link DietaryRuleEngine} treats it identically to every other rule,
 * which is the point: callers depend only on the interface.
 *
 * <p>Allergies produce {@link com.cookmgmt.domain.ConflictType#ALLERGY} conflicts, which the
 * service layer treats as blocking - unlike a dietary preference, an allergy is never something the
 * customer can choose to order through.
 */
public class AllergyRule implements DietaryRule {

    @Override
    public String preferenceName() {
        return "allergy";
    }

    @Override
    public boolean appliesTo(Customer customer) {
        return !customer.getAllergies().isEmpty();
    }

    @Override
    public Optional<Conflict> check(String ingredient, Customer customer, IngredientCatalog catalog) {
        if (customer.isAllergicTo(ingredient)) {
            return Optional.of(Conflict.allergy(ingredient));
        }
        return Optional.empty();
    }
}
