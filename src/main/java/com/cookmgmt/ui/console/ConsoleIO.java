package com.cookmgmt.ui.console;

import java.util.List;
import java.util.Optional;

/**
 * Terminal input and output, behind an interface.
 *
 * <p>This is the seam that removed console I/O from the domain. {@code Chef.reviewOrder} used to
 * open a {@code new Scanner(System.in)} and prompt "Approve substitutions? (Y/N)" from inside a
 * domain class. Because a test cannot answer a prompt, a {@code setTestAutoApprove} flag had to be
 * added to production code purely so the suite could get past that line - a test-only branch
 * shipping in the application.
 *
 * <p>With the prompt behind an interface the domain never asks for input at all: the console
 * implementation asks, the GUI shows a dialog, and a test passes the decision in directly. The flag
 * is gone because nothing needs it.
 */
public interface ConsoleIO {

    /** Writes a line of output. */
    void print(String message);

    /** Writes a blank line. */
    default void blankLine() {
        print("");
    }

    /** Writes a section heading. */
    default void heading(String title) {
        blankLine();
        print("=== " + title + " ===");
    }

    /**
     * Asks for free text.
     *
     * @return what the user typed, trimmed; never {@code null}
     */
    String ask(String prompt);

    /**
     * Asks for a whole number within a range, re-prompting until one is given.
     */
    int askInt(String prompt, int min, int max);

    /**
     * Asks for a whole number, allowing the user to back out.
     *
     * @return the number, or empty if the user cancelled
     */
    Optional<Integer> askIntOrCancel(String prompt, int min, int max);

    /**
     * Asks for a decimal number, allowing the user to back out.
     */
    Optional<Double> askDecimalOrCancel(String prompt, double min, double max);

    /** Asks a yes/no question. */
    boolean confirm(String prompt);

    /**
     * Shows a numbered list and asks the user to pick one, with a trailing "Cancel" entry.
     *
     * @return the chosen item, or empty if the list was empty or the user cancelled
     */
    <T> Optional<T> choose(String title, List<T> options, java.util.function.Function<T, String> label);
}
