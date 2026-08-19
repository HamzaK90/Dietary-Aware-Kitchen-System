package com.cookmgmt.ui.fx;

import com.cookmgmt.app.AppContext;
import com.cookmgmt.domain.Chef;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.OrderStatus;
import com.cookmgmt.domain.policy.ChefAssignmentStrategy;
import com.cookmgmt.domain.policy.LeastLoadedAssignment;
import com.cookmgmt.domain.policy.RoundRobinAssignment;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin tab: menu, staff, customers, stock, the orders report, and the chef assignment policy.
 *
 * <p>The policy selector is the Liskov demonstration made visible: switching between
 * {@link RoundRobinAssignment} and {@link LeastLoadedAssignment} at runtime requires no change
 * anywhere else, because {@link com.cookmgmt.service.KitchenService} only knows the
 * {@link ChefAssignmentStrategy} interface.
 *
 * <p>The chart uses {@code javafx.scene.chart}, which ships with the toolkit. Adding a second
 * charting library for one bar chart would have reintroduced exactly the kind of unused dependency
 * this project was cleaned of.
 */
public class AdminView implements Refreshable {

    private final AppContext app;
    private final ViewRefresher refresher;

    private final TableView<Meal> mealsTable = new TableView<>();
    private final TableView<Chef> chefsTable = new TableView<>();
    private final TableView<Customer> customersTable = new TableView<>();
    private final TableView<Map.Entry<String, Integer>> stockTable = new TableView<>();
    private final TableView<Order> ordersTable = new TableView<>();
    // Held as fields so refreshChart can reset the category list and pin the numeric range; a
    // CategoryAxis otherwise keeps every category it has ever been given.
    private final CategoryAxis categoryAxis = new CategoryAxis();
    private final NumberAxis countAxis = new NumberAxis();
    private final BarChart<String, Number> ordersChart = buildChart();
    private final ComboBox<String> policyPicker = new ComboBox<>();

    public AdminView(AppContext app, ViewRefresher refresher) {
        this.app = app;
        this.refresher = refresher;
    }

    public Region build() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab("Menu", mealsPane()),
                new Tab("Chefs", chefsPane()),
                new Tab("Customers", customersPane()),
                new Tab("Inventory", stockPane()),
                new Tab("Orders", ordersPane()));

        refreshAll();

        VBox root = new VBox(8, policyBar(), tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        return root;
    }

    private HBox policyBar() {
        policyPicker.setItems(FXCollections.observableArrayList("Round robin", "Least loaded"));
        policyPicker.getSelectionModel().select(
                app.kitchenService().getAssignmentStrategy().name());
        policyPicker.valueProperty().addListener((obs, old, selected) -> {
            ChefAssignmentStrategy strategy = "Least loaded".equals(selected)
                    ? new LeastLoadedAssignment()
                    : new RoundRobinAssignment();
            app.kitchenService().setAssignmentStrategy(strategy);
        });

        Button refresh = new Button("Refresh all");
        refresh.setOnAction(event -> refresher.refreshAll());

        HBox bar = new HBox(10, new Label("Chef assignment policy:"), policyPicker, refresh);
        bar.setPadding(new Insets(12, 12, 0, 12));
        return bar;
    }

    // ------------------------------------------------------------------ menu

    private Region mealsPane() {
        TableColumn<Meal, String> name = new TableColumn<>("Meal");
        name.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getName()));
        name.setPrefWidth(160);

        TableColumn<Meal, String> price = new TableColumn<>("Price");
        price.setCellValueFactory(cell ->
                FxBindings.of(app.catalogService().priceOf(cell.getValue()).toString()));
        price.setPrefWidth(80);

        TableColumn<Meal, String> time = new TableColumn<>("Time");
        time.setCellValueFactory(cell ->
                FxBindings.of(cell.getValue().getCookingTimeMinutes() + " min"));
        time.setPrefWidth(80);

        TableColumn<Meal, String> recipe = new TableColumn<>("Recipe");
        recipe.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getIngredients()));
        recipe.setPrefWidth(320);

        mealsTable.getColumns().setAll(List.of(name, price, time, recipe));

        TextField mealName = new TextField();
        mealName.setPromptText("Meal name");
        TextField recipeField = new TextField();
        recipeField.setPromptText("Recipe, e.g. tofu:1, rice:2");
        recipeField.setPrefWidth(240);
        TextField minutes = new TextField();
        minutes.setPromptText("Minutes");
        minutes.setPrefWidth(80);

        Button add = new Button("Add meal");
        add.getStyleClass().add("primary-button");
        add.setOnAction(event -> {
            try {
                Map<String, Integer> parsed = parseRecipe(recipeField.getText());
                if (parsed.isEmpty()) {
                    FxDialogs.warn("Recipe needed", "Enter at least one ingredient, e.g. tofu:1");
                    return;
                }
                app.catalogService().addMeal(mealName.getText(), parsed,
                        Integer.parseInt(minutes.getText().trim()));
                mealName.clear();
                recipeField.clear();
                minutes.clear();
                refresher.refreshAll();
            } catch (NumberFormatException e) {
                FxDialogs.warn("Cooking time needed", "Enter the cooking time as a whole number.");
            } catch (RuntimeException e) {
                FxDialogs.error("Could not add the meal", e.getMessage());
            }
        });

        Button delete = new Button("Delete selected");
        delete.setOnAction(event -> {
            Meal selected = mealsTable.getSelectionModel().getSelectedItem();
            if (selected != null && FxDialogs.confirm("Delete meal",
                    "Remove \"" + selected.getName() + "\" from the menu?")) {
                app.catalogService().removeMeal(selected);
                refresher.refreshAll();
            }
        });

        return pane(mealsTable, new HBox(8, mealName, recipeField, minutes, add, delete));
    }

    /** Parses {@code "tofu:1, rice:2"} into a recipe map. */
    private static Map<String, Integer> parseRecipe(String text) {
        Map<String, Integer> recipe = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return recipe;
        }
        for (String part : text.split(",")) {
            String[] halves = part.split(":");
            if (halves.length == 2) {
                recipe.put(halves[0].trim(), Integer.parseInt(halves[1].trim()));
            }
        }
        return recipe;
    }

    // ----------------------------------------------------------------- chefs

    private Region chefsPane() {
        TableColumn<Chef, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getName()));
        name.setPrefWidth(160);

        TableColumn<Chef, String> email = new TableColumn<>("Email");
        email.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getEmail()));
        email.setPrefWidth(220);

        TableColumn<Chef, String> load = new TableColumn<>("In queue");
        load.setCellValueFactory(cell ->
                FxBindings.of(app.kitchenService().queueFor(cell.getValue()).size()));
        load.setPrefWidth(90);

        chefsTable.getColumns().setAll(List.of(name, email, load));

        TextField chefName = new TextField();
        chefName.setPromptText("Name");
        TextField chefEmail = new TextField();
        chefEmail.setPromptText("Email");

        Button hire = new Button("Add chef");
        hire.getStyleClass().add("primary-button");
        hire.setOnAction(event -> {
            try {
                app.staffService().hire(chefName.getText(), chefEmail.getText());
                chefName.clear();
                chefEmail.clear();
                refresher.refreshAll();
            } catch (RuntimeException e) {
                // Duplicate emails are refused by the repository rather than silently accepted.
                FxDialogs.error("Could not add the chef", e.getMessage());
            }
        });

        Button remove = new Button("Remove selected");
        remove.setOnAction(event -> {
            Chef selected = chefsTable.getSelectionModel().getSelectedItem();
            if (selected != null && FxDialogs.confirm("Remove chef",
                    "Remove " + selected.getName() + " from the roster?")) {
                app.staffService().remove(selected);
                refresher.refreshAll();
            }
        });

        return pane(chefsTable, new HBox(8, chefName, chefEmail, hire, remove));
    }

    // ------------------------------------------------------------- customers

    private Region customersPane() {
        TableColumn<Customer, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getName()));
        name.setPrefWidth(140);

        TableColumn<Customer, String> email = new TableColumn<>("Email");
        email.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getEmail()));
        email.setPrefWidth(200);

        TableColumn<Customer, String> diet = new TableColumn<>("Dietary preferences");
        diet.setCellValueFactory(cell ->
                FxBindings.of(String.join(", ", cell.getValue().getDietaryPreferences())));
        diet.setPrefWidth(200);

        TableColumn<Customer, String> allergies = new TableColumn<>("Allergies");
        allergies.setCellValueFactory(cell ->
                FxBindings.of(String.join(", ", cell.getValue().getAllergies())));
        allergies.setPrefWidth(160);

        customersTable.getColumns().setAll(List.of(name, email, diet, allergies));

        TextField customerName = new TextField();
        customerName.setPromptText("Name");
        TextField customerEmail = new TextField();
        customerEmail.setPromptText("Email");
        TextField diets = new TextField();
        diets.setPromptText("Diets, comma separated");
        TextField allergyField = new TextField();
        allergyField.setPromptText("Allergies, comma separated");

        Button register = new Button("Register");
        register.getStyleClass().add("primary-button");
        register.setOnAction(event -> {
            try {
                app.customerService().register(customerName.getText(), customerEmail.getText(),
                        com.cookmgmt.support.Text.parseCsv(diets.getText()),
                        com.cookmgmt.support.Text.parseCsv(allergyField.getText()));
                customerName.clear();
                customerEmail.clear();
                diets.clear();
                allergyField.clear();
                refresher.refreshAll();
            } catch (RuntimeException e) {
                FxDialogs.error("Could not register the customer", e.getMessage());
            }
        });

        return pane(customersTable,
                new HBox(8, customerName, customerEmail, diets, allergyField, register));
    }

    // ------------------------------------------------------------- inventory

    private Region stockPane() {
        TableColumn<Map.Entry<String, Integer>, String> ingredient = new TableColumn<>("Ingredient");
        ingredient.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getKey()));
        ingredient.setPrefWidth(180);

        TableColumn<Map.Entry<String, Integer>, String> quantity = new TableColumn<>("In stock");
        quantity.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getValue()));
        quantity.setPrefWidth(100);

        TableColumn<Map.Entry<String, Integer>, String> price = new TableColumn<>("Unit price");
        price.setCellValueFactory(cell ->
                FxBindings.of(app.inventory().priceOf(cell.getValue().getKey()).toString()));
        price.setPrefWidth(100);

        TableColumn<Map.Entry<String, Integer>, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell -> FxBindings.of(
                cell.getValue().getValue() < com.cookmgmt.inventory.ReadableInventory.LOW_STOCK_THRESHOLD
                        ? "LOW" : "ok"));
        status.setPrefWidth(80);

        stockTable.getColumns().setAll(List.of(ingredient, quantity, price, status));

        TextField ingredientName = new TextField();
        ingredientName.setPromptText("Ingredient");
        TextField quantityField = new TextField();
        quantityField.setPromptText("Quantity");
        quantityField.setPrefWidth(90);
        TextField priceField = new TextField();
        priceField.setPromptText("Unit price");
        priceField.setPrefWidth(90);

        Button addStock = new Button("Add / restock");
        addStock.getStyleClass().add("primary-button");
        addStock.setOnAction(event -> {
            try {
                app.inventoryService().addIngredient(ingredientName.getText(),
                        Integer.parseInt(quantityField.getText().trim()),
                        Money.of(priceField.getText().trim()));
                ingredientName.clear();
                quantityField.clear();
                priceField.clear();
                refresher.refreshAll();
            } catch (NumberFormatException e) {
                FxDialogs.warn("Check the numbers",
                        "Quantity must be a whole number and price a decimal, e.g. 3.50");
            } catch (RuntimeException e) {
                FxDialogs.error("Could not update the inventory", e.getMessage());
            }
        });

        return pane(stockTable, new HBox(8, ingredientName, quantityField, priceField, addStock));
    }

    // ---------------------------------------------------------------- orders

    private Region ordersPane() {
        TableColumn<Order, String> number = new TableColumn<>("#");
        number.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getOrderNumber()));
        number.setPrefWidth(45);

        TableColumn<Order, String> customer = new TableColumn<>("Customer");
        customer.setCellValueFactory(cell ->
                FxBindings.of(cell.getValue().getCustomer().getName()));
        customer.setPrefWidth(110);

        TableColumn<Order, String> meal = new TableColumn<>("Meal");
        meal.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getMeal().getName()));
        meal.setPrefWidth(140);

        TableColumn<Order, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell ->
                FxBindings.of(cell.getValue().getStatus().displayName()));
        status.setPrefWidth(150);

        TableColumn<Order, String> total = new TableColumn<>("Total");
        total.setCellValueFactory(cell -> FxBindings.of(totalTextFor(cell.getValue())));
        total.setPrefWidth(100);

        ordersTable.getColumns().setAll(List.of(number, customer, meal, status, total));
        ordersTable.setRowFactory(table -> new OrderStatusRow());

        VBox pane = new VBox(8, sectionLabel("All orders"), ordersTable,
                sectionLabel("Orders per meal"), ordersChart);
        VBox.setVgrow(ordersTable, Priority.ALWAYS);
        pane.setPadding(new Insets(12));
        return pane;
    }

    /**
     * An order that will never be cooked has no total.
     *
     * <p>Quoting a running estimate for a cancelled or rejected order is the same mistake as
     * invoicing one: it presents a number the kitchen will never charge as though it were real.
     */
    private String totalTextFor(Order order) {
        if (order.getFinalPrice().isPresent()) {
            return order.getFinalPrice().get().toString();
        }
        return order.getStatus().isActive()
                ? app.pricingService().quote(order) + " est."
                : "-";
    }

    /**
     * Colours an order row by its status, so a cancellation is visible at a glance rather than
     * only readable in the status column.
     *
     * <p>The style classes are removed before each is reapplied: {@link javafx.scene.control.TableRow}
     * instances are recycled as the table scrolls, so a row that keeps a class from whichever order
     * it displayed previously will smear that colour across unrelated rows.
     */
    private static final class OrderStatusRow extends javafx.scene.control.TableRow<Order> {

        private static final List<String> STATUS_CLASSES =
                List.of("order-cancelled", "order-rejected", "order-completed");

        @Override
        protected void updateItem(Order order, boolean empty) {
            super.updateItem(order, empty);
            getStyleClass().removeAll(STATUS_CLASSES);
            if (empty || order == null) {
                return;
            }
            switch (order.getStatus()) {
                case CANCELLED -> getStyleClass().add("order-cancelled");
                case REJECTED -> getStyleClass().add("order-rejected");
                case COMPLETED -> getStyleClass().add("order-completed");
                default -> { }
            }
        }
    }

    private BarChart<String, Number> buildChart() {
        categoryAxis.setLabel("Meal");
        // setAnimated on the chart does not reach its axes, and an animating axis re-ranges itself
        // asynchronously - which is what made the scale appear to creep on every refresh.
        categoryAxis.setAnimated(false);

        countAxis.setLabel("Orders");
        countAxis.setTickUnit(1);
        countAxis.setMinorTickCount(0);
        countAxis.setAutoRanging(false);
        countAxis.setAnimated(false);

        BarChart<String, Number> chart = new BarChart<>(categoryAxis, countAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(240);
        return chart;
    }

    // ----------------------------------------------------------------- state

    @Override
    public void refreshAll() {
        mealsTable.setItems(FXCollections.observableArrayList(app.catalogService().allMeals()));
        chefsTable.setItems(FXCollections.observableArrayList(app.staffService().allChefs()));
        customersTable.setItems(FXCollections.observableArrayList(
                app.customerService().allCustomers()));
        stockTable.setItems(FXCollections.observableArrayList(
                app.inventory().stockSnapshot().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList()));
        ordersTable.setItems(FXCollections.observableArrayList(app.orderRepository().findAll()));

        /*
         * Replacing the items list is not enough to redraw a row.
         *
         * Entity.equals compares by identity, so an order whose status has just changed is still
         * equal to the object the cell already holds, and JavaFX treats the update as a no-op and
         * skips it. The Status column therefore kept showing "Awaiting chef approval" after a chef
         * had approved the order - the data was right, the cell simply never re-read it. The chefs
         * table has the same problem with its live "In queue" count.
         */
        mealsTable.refresh();
        chefsTable.refresh();
        customersTable.refresh();
        stockTable.refresh();
        ordersTable.refresh();

        refreshChart();
    }

    private void refreshChart() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Order order : app.orderRepository().findAll()) {
            // Cancelled and rejected orders are excluded: the chart is meant to show what the
            // kitchen is actually serving, and counting withdrawn orders inflates demand for
            // exactly the meals customers turned out not to want.
            if (order.getStatus() == OrderStatus.CANCELLED
                    || order.getStatus() == OrderStatus.REJECTED) {
                continue;
            }
            counts.merge(order.getMeal().getName(), 1, Integer::sum);
        }

        /*
         * The categories are cleared and restated on every refresh.
         *
         * A CategoryAxis accumulates the categories it has been shown. Replacing the series without
         * resetting it left the old names in place, so each "Refresh all" widened the axis by
         * another set of empty slots and the bars shrank towards nothing even though the order
         * count had not moved.
         */
        ordersChart.getData().clear();
        categoryAxis.getCategories().clear();
        categoryAxis.setCategories(FXCollections.observableArrayList(counts.keySet()));

        // Auto-ranging picks its own bounds and animates towards them, which made the vertical
        // scale drift on every refresh. Whole orders are counted, so the range is stated exactly.
        int highest = counts.values().stream().max(Integer::compareTo).orElse(0);
        countAxis.setLowerBound(0);
        countAxis.setUpperBound(Math.max(1, highest));
        countAxis.setTickUnit(1);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        counts.forEach((meal, count) -> series.getData().add(new XYChart.Data<>(meal, count)));
        ordersChart.getData().add(series);
    }

    private static Region pane(Region table, Region controls) {
        VBox pane = new VBox(8, table, controls);
        VBox.setVgrow(table, Priority.ALWAYS);
        pane.setPadding(new Insets(12));
        return pane;
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-label");
        return label;
    }
}
