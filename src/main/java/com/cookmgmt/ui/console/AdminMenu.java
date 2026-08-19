package com.cookmgmt.ui.console;

import com.cookmgmt.app.AppContext;
import com.cookmgmt.domain.Chef;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.policy.LeastLoadedAssignment;
import com.cookmgmt.domain.policy.RoundRobinAssignment;
import com.cookmgmt.support.Text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The admin-facing part of the console interface: menu, staff, customers, stock and reports. */
public class AdminMenu {

    private final AppContext app;
    private final ConsoleIO io;

    public AdminMenu(AppContext app, ConsoleIO io) {
        this.app = app;
        this.io = io;
    }

    public void run() {
        while (true) {
            io.heading("Admin Menu");
            io.print("1. Meals");
            io.print("2. Chefs");
            io.print("3. Customers");
            io.print("4. Inventory");
            io.print("5. Orders report");
            io.print("6. Chef assignment policy ("
                    + app.kitchenService().getAssignmentStrategy().name() + ")");
            io.print("7. Back");

            switch (io.askInt("Select: ", 1, 7)) {
                case 1 -> manageMeals();
                case 2 -> manageChefs();
                case 3 -> manageCustomers();
                case 4 -> manageInventory();
                case 5 -> ordersReport();
                case 6 -> chooseAssignmentPolicy();
                case 7 -> {
                    return;
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ meals

    private void manageMeals() {
        while (true) {
            io.heading("Meals");
            io.print("1. List");
            io.print("2. Add");
            io.print("3. Edit");
            io.print("4. Delete");
            io.print("5. Back");

            switch (io.askInt("Select: ", 1, 5)) {
                case 1 -> listMeals();
                case 2 -> addMeal();
                case 3 -> editMeal();
                case 4 -> deleteMeal();
                case 5 -> {
                    return;
                }
                default -> { }
            }
        }
    }

    private void listMeals() {
        io.heading("Menu");
        for (Meal meal : app.catalogService().allMeals()) {
            io.print(String.format("%-16s %-8s %3d min  %s",
                    meal.getName(), app.catalogService().priceOf(meal),
                    meal.getCookingTimeMinutes(), meal.getIngredients()));
        }
    }

    private void addMeal() {
        String name = io.ask("Meal name (or \"cancel\"): ");
        if (name.isBlank() || name.equalsIgnoreCase(ScannerConsoleIO.CANCEL)) {
            return;
        }

        // The builder holds the partial recipe, so no half-formed Meal ever exists.
        Meal.Builder builder = Meal.builder().name(name);
        while (true) {
            String ingredient = io.ask("Ingredient (or \"done\"): ");
            if (ingredient.equalsIgnoreCase("done")) {
                break;
            }
            if (ingredient.equalsIgnoreCase(ScannerConsoleIO.CANCEL)) {
                return;
            }
            Optional<Integer> quantity = io.askIntOrCancel("Quantity: ", 1, 100);
            if (quantity.isEmpty()) {
                return;
            }
            builder.ingredient(ingredient, quantity.get());
        }

        if (!builder.hasIngredients()) {
            // The old screen accepted this and stored a meal with no recipe at all.
            io.print("A meal needs at least one ingredient. Nothing was added.");
            return;
        }

        Optional<Integer> minutes = io.askIntOrCancel("Cooking time in minutes: ", 1, Meal.MAX_COOKING_MINUTES);
        if (minutes.isEmpty()) {
            return;
        }

        try {
            Meal meal = app.catalogService().addMeal(builder.cookingTimeMinutes(minutes.get()).build());
            io.print("Added \"" + meal.getName() + "\".");
        } catch (IllegalArgumentException e) {
            io.print("Could not add the meal: " + e.getMessage());
        }
    }

    private void editMeal() {
        Optional<Meal> selection = io.choose("Select a meal to edit:",
                app.catalogService().allMeals(), Meal::getName);
        if (selection.isEmpty()) {
            return;
        }
        Meal meal = selection.get();

        io.print("Current recipe: " + meal.getIngredients());
        io.print("Leave a field blank to keep it as it is.");

        String newName = io.ask("New name: ");
        Optional<Integer> newTime = io.askIntOrCancel(
                "New cooking time (or \"cancel\" to keep " + meal.getCookingTimeMinutes() + "): ",
                1, Meal.MAX_COOKING_MINUTES);

        Map<String, Integer> newRecipe = null;
        if (io.confirm("Replace the ingredient list?")) {
            Map<String, Integer> collected = new LinkedHashMap<>();
            while (true) {
                String ingredient = io.ask("Ingredient (or \"done\"): ");
                if (ingredient.equalsIgnoreCase("done")) {
                    break;
                }
                Optional<Integer> quantity = io.askIntOrCancel("Quantity: ", 1, 100);
                if (quantity.isEmpty()) {
                    return;
                }
                collected.put(ingredient, quantity.get());
            }
            if (collected.isEmpty()) {
                io.print("No ingredients entered - keeping the existing recipe.");
            } else {
                newRecipe = collected;
            }
        }

        try {
            app.catalogService().updateMeal(meal,
                    newName.isBlank() ? null : newName,
                    newRecipe,
                    newTime.orElse(null));
            io.print("Updated.");
        } catch (IllegalArgumentException e) {
            io.print("Could not update the meal: " + e.getMessage());
        }
    }

    private void deleteMeal() {
        io.choose("Select a meal to delete:", app.catalogService().allMeals(), Meal::getName)
                .ifPresent(meal -> {
                    if (io.confirm("Delete \"" + meal.getName() + "\"?")) {
                        app.catalogService().removeMeal(meal);
                        io.print("Deleted.");
                    }
                });
    }

    // ------------------------------------------------------------------ chefs

    private void manageChefs() {
        while (true) {
            io.heading("Chefs");
            io.print("1. List");
            io.print("2. Add");
            io.print("3. Edit");
            io.print("4. Delete");
            io.print("5. Back");

            switch (io.askInt("Select: ", 1, 5)) {
                case 1 -> app.staffService().allChefs().forEach(chef ->
                        io.print(String.format("%-16s %-24s %d in queue",
                                chef.getName(), chef.getEmail(),
                                app.kitchenService().queueFor(chef).size())));
                case 2 -> addChef();
                case 3 -> editChef();
                case 4 -> deleteChef();
                case 5 -> {
                    return;
                }
                default -> { }
            }
        }
    }

    private void addChef() {
        String name = io.ask("Name: ");
        String email = io.ask("Email: ");
        try {
            app.staffService().hire(name, email);
            io.print("Chef added.");
        } catch (RuntimeException e) {
            io.print("Could not add the chef: " + e.getMessage());
        }
    }

    private void editChef() {
        io.choose("Select a chef to edit:", app.staffService().allChefs(),
                chef -> chef.getName() + " (" + chef.getEmail() + ")").ifPresent(chef -> {
            String name = io.ask("New name (blank to keep): ");
            String email = io.ask("New email (blank to keep): ");
            try {
                // Editing keeps the same entity, so the chef's assigned orders survive the rename.
                app.staffService().updateProfile(chef,
                        name.isBlank() ? null : name,
                        email.isBlank() ? null : email);
                io.print("Updated.");
            } catch (RuntimeException e) {
                io.print("Could not update the chef: " + e.getMessage());
            }
        });
    }

    private void deleteChef() {
        io.choose("Select a chef to delete:", app.staffService().allChefs(), Chef::getName)
                .ifPresent(chef -> {
                    if (io.confirm("Delete " + chef.getName() + "?")) {
                        app.staffService().remove(chef);
                        io.print("Deleted.");
                    }
                });
    }

    // -------------------------------------------------------------- customers

    private void manageCustomers() {
        while (true) {
            io.heading("Customers");
            io.print("1. List");
            io.print("2. Add");
            io.print("3. Edit");
            io.print("4. Delete");
            io.print("5. Back");

            switch (io.askInt("Select: ", 1, 5)) {
                case 1 -> listCustomers();
                case 2 -> addCustomer();
                case 3 -> editCustomer();
                case 4 -> deleteCustomer();
                case 5 -> {
                    return;
                }
                default -> { }
            }
        }
    }

    private void listCustomers() {
        io.heading("Customers");
        for (Customer customer : app.customerService().allCustomers()) {
            io.print(String.format("%-12s %-24s diet: %-24s allergies: %s",
                    customer.getName(), customer.getEmail(),
                    String.join(", ", customer.getDietaryPreferences()),
                    String.join(", ", customer.getAllergies())));
        }
    }

    private void addCustomer() {
        String name = io.ask("Name: ");
        String email = io.ask("Email: ");
        String diets = io.ask("Dietary preferences (comma separated): ");
        String allergies = io.ask("Allergies (comma separated): ");
        try {
            app.customerService().register(name, email,
                    Text.parseCsv(diets), Text.parseCsv(allergies));
            io.print("Customer added.");
        } catch (RuntimeException e) {
            io.print("Could not add the customer: " + e.getMessage());
        }
    }

    private void editCustomer() {
        io.choose("Select a customer to edit:", app.customerService().allCustomers(),
                customer -> customer.getName() + " (" + customer.getEmail() + ")")
                .ifPresent(customer -> {
                    String name = io.ask("New name (blank to keep): ");
                    String email = io.ask("New email (blank to keep): ");
                    String diets = io.ask("New dietary preferences (blank to keep): ");
                    String allergies = io.ask("New allergies (blank to keep): ");
                    try {
                        app.customerService().updateProfile(customer,
                                name.isBlank() ? null : name,
                                email.isBlank() ? null : email,
                                diets.isBlank() ? null : Text.parseCsv(diets),
                                allergies.isBlank() ? null : Text.parseCsv(allergies));
                        io.print("Updated.");
                    } catch (RuntimeException e) {
                        io.print("Could not update the customer: " + e.getMessage());
                    }
                });
    }

    private void deleteCustomer() {
        io.choose("Select a customer to delete:", app.customerService().allCustomers(),
                Customer::getName).ifPresent(customer -> {
            if (io.confirm("Delete " + customer.getName() + "?")) {
                app.customerService().remove(customer);
                io.print("Deleted.");
            }
        });
    }

    // -------------------------------------------------------------- inventory

    private void manageInventory() {
        while (true) {
            io.heading("Inventory");
            io.print("1. View");
            io.print("2. Add or restock an ingredient");
            io.print("3. Change a price");
            io.print("4. Low stock report");
            io.print("5. Back");

            switch (io.askInt("Select: ", 1, 5)) {
                case 1 -> viewInventory();
                case 2 -> addOrRestock();
                case 3 -> changePrice();
                case 4 -> app.inventoryService().lowStock().forEach(ingredient ->
                        io.print(String.format("  %-16s %d left",
                                ingredient, app.inventory().stockOf(ingredient))));
                case 5 -> {
                    return;
                }
                default -> { }
            }
        }
    }

    private void viewInventory() {
        io.heading("Stock");
        app.inventory().stockSnapshot().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> io.print(String.format("  %-16s %4d units @ %s",
                        entry.getKey(), entry.getValue(),
                        app.inventory().priceOf(entry.getKey()))));
    }

    private void addOrRestock() {
        String name = io.ask("Ingredient name (or \"cancel\"): ");
        if (name.isBlank() || name.equalsIgnoreCase(ScannerConsoleIO.CANCEL)) {
            return;
        }
        Optional<Integer> quantity = io.askIntOrCancel("Quantity to add: ", 0, 1000);
        if (quantity.isEmpty()) {
            return;
        }

        if (app.inventory().isKnown(name) && !io.confirm("Also change the price?")) {
            app.inventoryService().restock(name, quantity.get());
            io.print("Restocked. Price left at " + app.inventory().priceOf(name) + ".");
            return;
        }

        Optional<Double> price = io.askDecimalOrCancel("Price per unit: ", 0.0, 1000.0);
        if (price.isEmpty()) {
            return;
        }
        app.inventoryService().addIngredient(name, quantity.get(), Money.of(price.get()));
        io.print("Inventory updated.");
    }

    private void changePrice() {
        String name = io.ask("Ingredient name: ");
        if (!app.inventory().isKnown(name)) {
            io.print("No such ingredient.");
            return;
        }
        io.askDecimalOrCancel("New price per unit: ", 0.0, 1000.0).ifPresent(price -> {
            app.inventoryService().reprice(name, Money.of(price));
            io.print("Price updated.");
        });
    }

    // ---------------------------------------------------------------- reports

    private void ordersReport() {
        io.heading("Orders Report");
        List<Order> orders = app.orderRepository().findAll();
        if (orders.isEmpty()) {
            io.print("No orders have been placed.");
            return;
        }
        for (Order order : orders) {
            String total = order.getFinalPrice()
                    .map(Money::toString)
                    .orElseGet(() -> app.pricingService().quote(order) + " (estimate)");
            io.print(String.format("#%-4d %-12s %-16s %-24s %s",
                    order.getOrderNumber(),
                    order.getCustomer().getName(),
                    order.getMeal().getName(),
                    order.getStatus().displayName(),
                    total));
        }
    }

    private void chooseAssignmentPolicy() {
        // Liskov in practice: either strategy drops into KitchenService unchanged.
        io.heading("Chef Assignment Policy");
        io.print("1. Round robin");
        io.print("2. Least loaded");
        io.print("3. Back");

        switch (io.askInt("Select: ", 1, 3)) {
            case 1 -> {
                app.kitchenService().setAssignmentStrategy(new RoundRobinAssignment());
                io.print("Now assigning orders in rotation.");
            }
            case 2 -> {
                app.kitchenService().setAssignmentStrategy(new LeastLoadedAssignment());
                io.print("Now assigning orders to the least busy chef.");
            }
            default -> { }
        }
    }
}
