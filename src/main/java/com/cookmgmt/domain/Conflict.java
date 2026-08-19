package com.cookmgmt.domain;

import java.util.Objects;

/**
 * One reason a meal is unsuitable for a particular customer.
 *
 * <p>Conflict detection previously returned a {@code boolean} and printed its findings to
 * {@code System.out}. That made the detail unavailable to any caller and untestable without
 * capturing standard output, and it meant a GUI could not render the reasons at all. Rules now
 * return values, and the presentation layer decides how to show them.
 *
 * @param type       whether the clash is an allergy or a dietary preference
 * @param ingredient the offending ingredient, in its display form
 * @param reason     a human-readable explanation
 */
public record Conflict(ConflictType type, String ingredient, String reason) {

    public Conflict {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(ingredient, "ingredient");
        Objects.requireNonNull(reason, "reason");
    }

    public static Conflict allergy(String ingredient) {
        return new Conflict(ConflictType.ALLERGY, ingredient,
                "\"" + ingredient + "\" is on the customer's allergy list");
    }

    public static Conflict dietary(String ingredient, String preference, String explanation) {
        return new Conflict(ConflictType.DIETARY, ingredient,
                "\"" + ingredient + "\" conflicts with " + preference + ": " + explanation);
    }

    @Override
    public String toString() {
        return type + ": " + reason;
    }
}
