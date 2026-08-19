package com.cookmgmt.ui.console;

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;

/**
 * {@link ConsoleIO} backed by {@link System#in} and {@link System#out}.
 *
 * <p>One {@link Scanner} is created for the lifetime of the application. The old code called
 * {@code new Scanner(System.in)} inside {@code Chef.reviewOrder} on every review, creating a fresh
 * scanner over the same underlying stream each time - a well-known way to lose buffered input.
 */
public class ScannerConsoleIO implements ConsoleIO {

    /** Word the user can type at most prompts to back out. */
    public static final String CANCEL = "cancel";

    private final Scanner scanner;
    private final PrintStream out;

    public ScannerConsoleIO() {
        this(new Scanner(System.in), System.out);
    }

    public ScannerConsoleIO(Scanner scanner, PrintStream out) {
        this.scanner = scanner;
        this.out = out;
    }

    @Override
    public void print(String message) {
        out.println(message);
    }

    @Override
    public String ask(String prompt) {
        out.print(prompt);
        out.flush();
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "";
    }

    @Override
    public int askInt(String prompt, int min, int max) {
        while (true) {
            String input = ask(prompt);
            try {
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return value;
                }
                print("Please enter a number between " + min + " and " + max + ".");
            } catch (NumberFormatException e) {
                print("\"" + input + "\" is not a number.");
            }
        }
    }

    @Override
    public Optional<Integer> askIntOrCancel(String prompt, int min, int max) {
        while (true) {
            String input = ask(prompt);
            if (input.equalsIgnoreCase(CANCEL)) {
                return Optional.empty();
            }
            try {
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return Optional.of(value);
                }
                print("Enter " + min + "-" + max + ", or \"" + CANCEL + "\".");
            } catch (NumberFormatException e) {
                print("Enter a number, or \"" + CANCEL + "\".");
            }
        }
    }

    @Override
    public Optional<Double> askDecimalOrCancel(String prompt, double min, double max) {
        while (true) {
            String input = ask(prompt);
            if (input.equalsIgnoreCase(CANCEL)) {
                return Optional.empty();
            }
            try {
                double value = Double.parseDouble(input);
                if (value >= min && value <= max) {
                    return Optional.of(value);
                }
                print(String.format("Enter %.2f-%.2f, or \"%s\".", min, max, CANCEL));
            } catch (NumberFormatException e) {
                print("Enter a number, or \"" + CANCEL + "\".");
            }
        }
    }

    @Override
    public boolean confirm(String prompt) {
        String answer = ask(prompt + " (y/n): ");
        return answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes");
    }

    @Override
    public <T> Optional<T> choose(String title, List<T> options, Function<T, String> label) {
        if (options.isEmpty()) {
            print("Nothing to choose from.");
            return Optional.empty();
        }
        blankLine();
        print(title);
        for (int i = 0; i < options.size(); i++) {
            print("  " + (i + 1) + ". " + label.apply(options.get(i)));
        }
        int cancelOption = options.size() + 1;
        print("  " + cancelOption + ". Cancel");

        int choice = askInt("Select: ", 1, cancelOption);
        return choice == cancelOption ? Optional.empty() : Optional.of(options.get(choice - 1));
    }
}
