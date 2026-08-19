package com.cookmgmt.ui.console;

import com.cookmgmt.app.AppContext;
import com.cookmgmt.domain.Conflict;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.exception.DuplicateEmailException;
import com.cookmgmt.domain.exception.InsufficientStockException;
import com.cookmgmt.service.OrderService;
import com.cookmgmt.support.Text;

import java.util.Map;
import java.util.Optional;

/**
 * The customer-facing part of the console interface: browse the menu, see conflicts, order, and
 * follow progress.
 *
 * <p>Contains no business rules. Every decision - whether a meal conflicts, what to substitute,
 * whether stock allows the order - comes from the service layer, which the JavaFX front end calls
 * in exactly the same way.
 */
public class CustomerMenu {

    private final AppContext app;
    private final ConsoleIO io;

    public CustomerMenu(AppContext app, ConsoleIO io) {
        this.app = app;
        this.io = io;
    }

    public void run() {
        Optional<Customer> login = login();
        if (login.isEmpty()) {
            return;
        }
        Customer customer = login.get();

        while (true) {
            io.heading("Customer Menu - " + customer.getName());
            io.print("1. Browse menu and place an order");
            io.print("2. My active orders");
            io.print("3. Order history");
            io.print("4. Update my profile");
            io.print("5. Log out");

            switch (io.askInt("Select: ", 1, 5)) {
                case 1 -> placeOrder(customer);
                case 2 -> showActiveOrders(customer);
                case 3 -> showHistory(customer);
                case 4 -> updateProfile(customer);
                case 5 -> {
                    return;
                }
                default -> { }
            }
        }
    }

    private Optional<Customer> login() {
        io.heading("Customer Login");
        String email = io.ask("Email (or \"cancel\"): ");
        if (email.isEmpty() || email.equalsIgnoreCase(ScannerConsoleIO.CANCEL)) {
            return Optional.empty();
        }

        Optional<Customer> existing = app.customerService().login(email);
        if (existing.isPresent()) {
            return existing;
        }

        io.print("No account found for that address.");
        if (!io.confirm("Create one?")) {
            return Optional.empty();
        }

        String name = io.ask("Name: ");
        String diets = io.ask("Dietary preferences (comma separated, blank for none): ");
        String allergies = io.ask("Allergies (comma separated, blank for none): ");

        try {
            Customer created = app.customerService().register(
                    name, email, Text.parseCsv(diets), Text.parseCsv(allergies));
            io.print("Account created. Welcome, " + created.getName() + ".");
            return Optional.of(created);
        } catch (DuplicateEmailException | IllegalArgumentException e) {
            io.print("Could not create the account: " + e.getMessage());
            return Optional.empty();
        }
    }

    private void placeOrder(Customer customer) {
        Optional<Meal> selection = io.choose("Available meals:",
                app.catalogService().allMeals(),
                meal -> String.format("%-16s %-8s %2d min",
                        meal.getName(), app.catalogService().priceOf(meal),
                        meal.getCookingTimeMinutes()));
        if (selection.isEmpty()) {
            return;
        }
        Meal meal = selection.get();

        OrderService.OrderPreview preview = app.orderService().preview(customer, meal);
        Map<String, String> substitutions = Map.of();

        if (preview.hasConflicts()) {
            io.blankLine();
            io.print("This meal does not suit your profile:");
            for (Conflict conflict : preview.conflicts()) {
                io.print("  - " + conflict.reason());
            }

            if (preview.substitutions().isEmpty()) {
                io.print("No suitable replacements are available. Order cancelled.");
                return;
            }

            io.blankLine();
            io.print("Suggested changes:");
            preview.substitutions().forEach((original, replacement) ->
                    io.print("  - replace " + original + " with " + replacement));
            io.print("New price would be " + preview.price());

            if (preview.hasUnresolvedAllergen()) {
                io.print("An allergen cannot be replaced, so this meal cannot be ordered.");
                return;
            }
            if (preview.hasUnresolvedConflicts()) {
                // Say so plainly rather than implying the substitutions resolved everything.
                io.blankLine();
                io.print("Even with those changes the meal would still contain:");
                preview.remainingConflicts().forEach(conflict ->
                        io.print("  - " + conflict.reason()));
            }

            if (!io.confirm("Apply these changes and order?")) {
                io.print("Order cancelled.");
                return;
            }
            substitutions = preview.substitutions();
        }

        if (preview.hasShortages()) {
            io.print("The kitchen is short of: " + preview.shortages());
            io.print("Order cancelled.");
            return;
        }

        try {
            Order order = app.orderService().place(customer, meal, substitutions);
            io.print("Order #" + order.getOrderNumber() + " placed - "
                    + order.getStatus().displayName() + ".");
        } catch (InsufficientStockException e) {
            io.print("Could not place the order: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            io.print("Could not place the order: " + e.getMessage());
        }
    }

    private void showActiveOrders(Customer customer) {
        io.heading("Active Orders");
        var active = app.orderService().activeFor(customer);
        if (active.isEmpty()) {
            io.print("You have no orders in progress.");
            return;
        }
        for (Order order : active) {
            // The running total comes from PricingService, so a pending order shows a real price
            // instead of the $0.00 the old screen printed before completion.
            io.print(String.format("#%d  %-16s %-24s %s",
                    order.getOrderNumber(),
                    order.getMeal().getName(),
                    order.getStatus().displayName(),
                    app.pricingService().quote(order)));
            if (order.hasSubstitutions()) {
                io.print("     substitutions: " + order.getSubstitutions());
            }
        }
    }

    private void showHistory(Customer customer) {
        io.heading("Order History");
        var history = app.orderService().historyFor(customer);
        if (history.isEmpty()) {
            io.print("No completed orders yet.");
            return;
        }
        for (Order order : history) {
            io.print(String.format("#%d  %-16s %s",
                    order.getOrderNumber(),
                    order.getMeal().getName(),
                    order.getFinalPrice().orElseThrow()));
        }
        io.choose("View an invoice:", history,
                        order -> "#" + order.getOrderNumber() + " " + order.getMeal().getName())
                .ifPresent(order -> {
                    io.blankLine();
                    io.print(app.pricingService().invoiceFor(order).format());
                });
    }

    private void updateProfile(Customer customer) {
        io.heading("My Profile");
        io.print("Name:        " + customer.getName());
        io.print("Email:       " + customer.getEmail());
        io.print("Preferences: " + String.join(", ", customer.getDietaryPreferences()));
        io.print("Allergies:   " + String.join(", ", customer.getAllergies()));
        io.blankLine();
        io.print("Leave a field blank to keep it as it is.");

        String name = io.ask("New name: ");
        String diets = io.ask("New dietary preferences (comma separated): ");
        String allergies = io.ask("New allergies (comma separated): ");

        try {
            app.customerService().updateProfile(customer,
                    name.isBlank() ? null : name,
                    null,
                    diets.isBlank() ? null : Text.parseCsv(diets),
                    allergies.isBlank() ? null : Text.parseCsv(allergies));
            io.print("Profile updated.");
        } catch (IllegalArgumentException e) {
            io.print("Could not update the profile: " + e.getMessage());
        }
    }
}
