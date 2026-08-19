package com.cookmgmt.ui.console;

import com.cookmgmt.app.AppContext;
import com.cookmgmt.notify.ConsoleNotifier;

/**
 * Entry point for the terminal interface.
 *
 * <p>What used to be an 870-line {@code Main} holding the console screens, the dietary rules and
 * the sample data is now a role chooser that delegates to {@link CustomerMenu}, {@link ChefMenu}
 * and {@link AdminMenu}, all of which talk only to the service layer.
 *
 * <p>This class and {@link com.cookmgmt.ui.fx.FxApplication} build the same
 * {@link AppContext}. Neither contains a business rule, so the two interfaces cannot drift apart -
 * a change to a dietary rule or a pricing decision takes effect in both without being written
 * twice.
 *
 * <p>Run with {@code ./mvnw exec:java}.
 */
public class ConsoleApp {

    private final AppContext app;
    private final ConsoleIO io;

    public ConsoleApp(AppContext app, ConsoleIO io) {
        this.app = app;
        this.io = io;
    }

    public static void main(String[] args) {
        ConsoleIO io = new ScannerConsoleIO();
        // Notifications print to the terminal here; the GUI routes the same messages to a panel.
        AppContext app = AppContext.withSampleData(new ConsoleNotifier());
        new ConsoleApp(app, io).run();
    }

    public void run() {
        io.heading("Special Cook Management System");
        io.print("Loaded with sample data: "
                + app.catalogService().allMeals().size() + " meals, "
                + app.customerService().allCustomers().size() + " customers, "
                + app.staffService().allChefs().size() + " chefs.");
        io.print("Try logging in as layla@example.com (vegan, milk allergy) "
                + "or nora@kitchen.com.");

        while (true) {
            io.heading("Main Menu");
            io.print("1. Customer");
            io.print("2. Chef");
            io.print("3. Admin");
            io.print("4. Exit");

            switch (io.askInt("Select: ", 1, 4)) {
                case 1 -> new CustomerMenu(app, io).run();
                case 2 -> new ChefMenu(app, io).run();
                case 3 -> new AdminMenu(app, io).run();
                case 4 -> {
                    // Returning from the loop rather than calling System.exit(0), which the old
                    // menu did from inside a switch - untestable, and it skipped any cleanup.
                    io.print("Goodbye.");
                    return;
                }
                default -> { }
            }
        }
    }
}
