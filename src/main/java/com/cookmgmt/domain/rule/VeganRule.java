package com.cookmgmt.domain.rule;

/** Rejects anything tagged {@code non-vegan}: meat, seafood, dairy, eggs, honey, gelatin. */
public class VeganRule extends TagBasedDietaryRule {

    public VeganRule() {
        super("vegan", "non-vegan", "it is an animal product");
    }
}
