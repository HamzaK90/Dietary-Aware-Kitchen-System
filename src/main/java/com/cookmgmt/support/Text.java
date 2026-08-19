package com.cookmgmt.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small text helpers shared by the domain and both user interfaces.
 *
 * <p>Two long-standing defects lived in ad-hoc string handling:
 * <ul>
 *   <li>ingredient names were compared with inconsistent casing, so tags registered under
 *       {@code "Beef"} were never found by a lookup that had lower-cased its key;</li>
 *   <li>comma-separated input was parsed with a bare {@code split(",")}, so {@code " Vegan"} kept
 *       its leading space and never matched, and empty input produced a list holding one empty
 *       string rather than an empty list.</li>
 * </ul>
 * Both now have exactly one implementation.
 */
public final class Text {

    private Text() {
    }

    /**
     * Canonical form of an ingredient, preference or allergy name: trimmed and lower-cased using
     * {@link Locale#ROOT} so behaviour does not change with the machine's locale.
     *
     * @return the normalised value, or an empty string when {@code value} is {@code null}
     */
    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** {@code true} when the value is {@code null} or contains only whitespace. */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Splits comma-separated user input into trimmed, non-empty values.
     *
     * @return an empty list for {@code null}, blank or comma-only input
     */
    public static List<String> parseCsv(String value) {
        List<String> parsed = new ArrayList<>();
        if (isBlank(value)) {
            return parsed;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        return parsed;
    }

    /** Requires that {@code value} is non-blank, returning it trimmed. */
    public static String requireText(String value, String field) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
