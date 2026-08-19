package com.cookmgmt.domain.rule;

/**
 * Rejects anything tagged {@code non-kosher}, such as pork and shellfish.
 *
 * <p>The {@code non-kosher} tag existed in the original ingredient data but no code ever read it,
 * so kosher customers were never protected. Adding the diet required only this class and one line
 * in {@link DietaryRuleEngine#defaultRules()} - no existing rule was touched.
 */
public class KosherRule extends TagBasedDietaryRule {

    public KosherRule() {
        super("kosher", "non-kosher", "it is not kosher");
    }
}
