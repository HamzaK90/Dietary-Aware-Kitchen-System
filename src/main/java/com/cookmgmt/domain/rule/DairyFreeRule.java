package com.cookmgmt.domain.rule;

/** Rejects anything tagged {@code dairy}, such as milk and cheese. */
public class DairyFreeRule extends TagBasedDietaryRule {

    public DairyFreeRule() {
        super("dairy-free", "dairy", "it contains dairy");
    }
}
