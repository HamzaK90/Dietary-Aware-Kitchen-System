package com.cookmgmt.domain;

/** Severity category of a {@link Conflict}. */
public enum ConflictType {

    /** A medical allergy. Ordering anyway is never offered. */
    ALLERGY,

    /** A dietary preference such as vegan or halal. A substitution may resolve it. */
    DIETARY
}
