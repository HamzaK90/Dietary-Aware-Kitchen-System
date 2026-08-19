package com.cookmgmt.domain.rule;

/**
 * Rejects anything tagged {@code non-vegetarian}.
 *
 * <p>The original check tested for the {@code meat} tag instead. Shrimp was tagged
 * {@code seafood} and {@code non-vegetarian} but not {@code meat}, so it passed the vegetarian
 * check unchallenged.
 */
public class VegetarianRule extends TagBasedDietaryRule {

    public VegetarianRule() {
        super("vegetarian", "non-vegetarian", "it contains meat or seafood");
    }
}
