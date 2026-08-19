package com.cookmgmt.app;

import com.cookmgmt.domain.Money;
import com.cookmgmt.service.CatalogService;
import com.cookmgmt.service.CustomerService;
import com.cookmgmt.service.InventoryService;
import com.cookmgmt.service.StaffService;

import java.util.List;
import java.util.Map;

/**
 * The demonstration dataset: a stocked pantry, a menu, five customers with varied dietary needs,
 * and two chefs.
 *
 * <p>This used to be {@code initializeSampleData()} inside the console class, which meant the GUI
 * could not have reused it and a test wanting realistic data had to rebuild it by hand. It now
 * loads into any {@link AppContext}.
 *
 * <p>The customer profiles are chosen so the interesting paths are reachable straight away:
 * Layla is vegan and allergic to milk, Ahmad keeps halal and is allergic to shrimp, Salma is
 * gluten-free, vegetarian and allergic to nuts.
 */
public final class SampleData {

    private SampleData() {
    }

    public static void load(AppContext context) {
        loadInventory(context.inventoryService());
        loadMenu(context.catalogService());
        loadCustomers(context.customerService());
        loadChefs(context.staffService());
    }

    /**
     * Stocks the standard pantry. Public so tests can start from a realistic inventory rather than
     * rebuilding one ingredient at a time in every scenario.
     */
    public static void loadInventory(InventoryService inventory) {
        inventory.addIngredient("chicken", 30, Money.of("5.00"));
        inventory.addIngredient("beef", 25, Money.of("6.00"));
        inventory.addIngredient("pork", 10, Money.of("4.00"));
        inventory.addIngredient("shrimp", 18, Money.of("5.00"));
        inventory.addIngredient("egg", 50, Money.of("2.00"));
        inventory.addIngredient("tofu", 20, Money.of("3.50"));
        inventory.addIngredient("chickpeas", 24, Money.of("2.20"));
        inventory.addIngredient("mushroom", 22, Money.of("2.80"));

        inventory.addIngredient("milk", 40, Money.of("1.50"));
        inventory.addIngredient("almond milk", 16, Money.of("2.40"));
        inventory.addIngredient("oat milk", 14, Money.of("2.10"));
        inventory.addIngredient("cheese", 40, Money.of("2.50"));
        inventory.addIngredient("vegan cheese", 12, Money.of("3.20"));

        inventory.addIngredient("flour", 100, Money.of("1.00"));
        inventory.addIngredient("bun", 45, Money.of("1.00"));
        inventory.addIngredient("rice", 60, Money.of("1.20"));

        inventory.addIngredient("lettuce", 35, Money.of("0.80"));
        inventory.addIngredient("tomato", 35, Money.of("0.90"));
        inventory.addIngredient("nuts", 20, Money.of("4.00"));
        inventory.addIngredient("honey", 15, Money.of("2.50"));
    }

    private static void loadMenu(CatalogService catalog) {
        catalog.addMeal("Vegan Salad",
                Map.of("lettuce", 2, "tomato", 2, "nuts", 1), 10);
        catalog.addMeal("Breakfast Wrap",
                Map.of("egg", 1, "cheese", 1, "bun", 1), 12);
        catalog.addMeal("Chicken Rice",
                Map.of("chicken", 1, "rice", 2, "tomato", 1), 20);
        catalog.addMeal("Beef Burger",
                Map.of("bun", 2, "beef", 1, "cheese", 1, "lettuce", 1), 25);
        catalog.addMeal("Tofu Bowl",
                Map.of("tofu", 1, "rice", 2, "lettuce", 1, "tomato", 1), 20);
        catalog.addMeal("Shrimp Pasta",
                Map.of("shrimp", 2, "flour", 1, "milk", 1), 30);
        catalog.addMeal("Veggie Wrap",
                Map.of("lettuce", 1, "tomato", 1, "tofu", 1), 15);
        catalog.addMeal("Mac and Cheese",
                Map.of("flour", 2, "milk", 1, "cheese", 2), 18);
    }

    private static void loadCustomers(CustomerService customers) {
        customers.register("Layla", "layla@example.com", List.of("Vegan"), List.of("milk"));
        customers.register("Ahmad", "ahmad@example.com", List.of("Halal"), List.of("shrimp"));
        customers.register("Salma", "salma@example.com",
                List.of("Gluten-Free", "Vegetarian"), List.of("nuts"));
        customers.register("Omar", "omar@example.com", List.of("Vegetarian"), List.of("egg"));
        customers.register("Yasmin", "yasmin@example.com", List.of("Vegan"), List.of("nuts"));
    }

    private static void loadChefs(StaffService staff) {
        staff.hire("Chef Nora", "nora@kitchen.com");
        staff.hire("Chef Zaid", "zaid@kitchen.com");
    }
}
