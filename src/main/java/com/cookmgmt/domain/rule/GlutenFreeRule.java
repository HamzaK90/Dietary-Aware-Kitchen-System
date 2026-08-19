package com.cookmgmt.domain.rule;

/** Rejects anything tagged {@code gluten}, such as flour and buns. */
public class GlutenFreeRule extends TagBasedDietaryRule {

    public GlutenFreeRule() {
        super("gluten-free", "gluten", "it contains gluten");
    }
}
