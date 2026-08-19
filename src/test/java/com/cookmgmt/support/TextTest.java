package com.cookmgmt.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Text")
class TextTest {

    @ParameterizedTest(name = "\"{0}\" normalises to \"beef\"")
    @ValueSource(strings = {"beef", "Beef", "BEEF", "  Beef  ", "bEeF"})
    @DisplayName("normalises case and surrounding whitespace")
    void normalisesCaseAndWhitespace(String input) {
        // The defect this prevents: the ingredient tag table registered "Beef" while every lookup
        // lower-cased its key, so no dietary rule ever matched beef.
        assertEquals("beef", Text.normalize(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("normalises null and empty to an empty string")
    void normalisesNullAndEmpty(String input) {
        assertEquals("", Text.normalize(input));
    }

    @Test
    @DisplayName("parses comma separated input, trimming each value")
    void parsesCsvTrimmingValues() {
        assertEquals(List.of("Vegan", "Gluten-Free"), Text.parseCsv(" Vegan , Gluten-Free "));
    }

    @Test
    @DisplayName("returns an empty list for blank input rather than a list holding an empty string")
    void returnsEmptyListForBlankInput() {
        // "".split(",") returns [""], so the old code stored a preference that was the empty
        // string. It matched nothing and displayed as a stray comma in the profile screen.
        assertTrue(Text.parseCsv("").isEmpty());
        assertTrue(Text.parseCsv("   ").isEmpty());
        assertTrue(Text.parseCsv(null).isEmpty());
        assertTrue(Text.parseCsv(",,,").isEmpty());
    }

    @Test
    @DisplayName("drops empty entries between commas")
    void dropsEmptyEntries() {
        assertEquals(List.of("Vegan", "Halal"), Text.parseCsv("Vegan,,Halal,"));
    }

    @Test
    @DisplayName("rejects blank required text")
    void rejectsBlankRequiredText() {
        assertThrows(IllegalArgumentException.class, () -> Text.requireText("  ", "Name"));
        assertEquals("Layla", Text.requireText("  Layla  ", "Name"));
    }
}
