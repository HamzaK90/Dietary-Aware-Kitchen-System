package com.cookmgmt.domain.rule;

/** Rejects anything tagged {@code non-halal}, such as pork and gelatin. */
public class HalalRule extends TagBasedDietaryRule {

    public HalalRule() {
        super("halal", "non-halal", "it is not halal");
    }
}
