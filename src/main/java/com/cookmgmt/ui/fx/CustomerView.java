package com.cookmgmt.ui.fx;

import com.cookmgmt.app.AppContext;
import com.cookmgmt.domain.Conflict;
import com.cookmgmt.domain.ConflictType;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Order;
import com.cookmgmt.service.OrderService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

/**
 * Customer tab: pick who you are, browse the menu, see live conflict warnings, and order.
 *
 * <p>The conflict panel is the clearest illustration of why detection had to stop being a
 * {@code boolean} plus {@code System.out.println}. It renders the individual reasons and the
 * proposed substitution - information the old signature threw away, and which the console could
 * only ever print into a terminal that a GUI has no access to.
 */
public class CustomerView {

    private final AppContext app;

    private final ComboBox<Customer> customerPicker = new ComboBox<>();
    private final TableView<Meal> menuTable = new TableView<>();
    private final TableView<Order> ordersTable = new TableView<>();
    private final TextArea detailPane = new TextArea();
    private final Label profileLabel = new Label();
    private final Button orderButton = new Button("Place order");

    public CustomerView(AppContext app) {
        this.app = app;
    }

    public Region build() {
        buildCustomerPicker();
        buildMenuTable();
        buildOrdersTable();

        detailPane.setEditable(false);
        detailPane.getStyleClass().add("detail-pane");
        detailPane.setPromptText("Select a meal to see whether it suits this customer.");

        orderButton.getStyleClass().add("primary-button");
        orderButton.setDisable(true);
        orderButton.setOnAction(event -> placeOrder());

        Button invoiceButton = new Button("View invoice");
        invoiceButton.setOnAction(event -> showInvoice());

        Button cancelButton = new Button("Cancel order");
        cancelButton.setOnAction(event -> cancelSelectedOrder());

        VBox left = new VBox(8,
                sectionLabel("Menu"),
                menuTable,
                new HBox(8, orderButton));
        VBox.setVgrow(menuTable, Priority.ALWAYS);
        left.setPadding(new Insets(12));

        VBox right = new VBox(8,
                sectionLabel("Suitability"),
                detailPane,
                sectionLabel("My orders"),
                ordersTable,
                new HBox(8, invoiceButton, cancelButton));
        VBox.setVgrow(detailPane, Priority.ALWAYS);
        VBox.setVgrow(ordersTable, Priority.ALWAYS);
        right.setPadding(new Insets(12));

        SplitPane split = new SplitPane(left, right);
        split.setDividerPositions(0.5);

        VBox root = new VBox(8, customerBar(), split);
        VBox.setVgrow(split, Priority.ALWAYS);
        return root;
    }

    private HBox customerBar() {
        profileLabel.getStyleClass().add("profile-label");
        HBox bar = new HBox(10, new Label("Ordering as:"), customerPicker, profileLabel);
        bar.setPadding(new Insets(12, 12, 0, 12));
        return bar;
    }

    private void buildCustomerPicker() {
        customerPicker.setItems(FXCollections.observableArrayList(
                app.customerService().allCustomers()));
        customerPicker.setConverter(new SimpleConverter<>(Customer::getName));
        customerPicker.getSelectionModel().selectFirst();
        customerPicker.valueProperty().addListener((obs, old, selected) -> {
            refreshProfile();
            refreshSuitability();
            refreshOrders();
        });
        refreshProfile();
    }

    private void buildMenuTable() {
        TableColumn<Meal, String> name = new TableColumn<>("Meal");
        name.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getName()));
        name.setPrefWidth(150);

        TableColumn<Meal, String> price = new TableColumn<>("Price");
        price.setCellValueFactory(cell ->
                FxBindings.of(app.catalogService().priceOf(cell.getValue()).toString()));
        price.setPrefWidth(80);

        TableColumn<Meal, String> time = new TableColumn<>("Time");
        time.setCellValueFactory(cell ->
                FxBindings.of(cell.getValue().getCookingTimeMinutes() + " min"));
        time.setPrefWidth(70);

        TableColumn<Meal, String> ingredients = new TableColumn<>("Ingredients");
        ingredients.setCellValueFactory(cell ->
                FxBindings.of(String.join(", ", cell.getValue().ingredientNames())));
        ingredients.setPrefWidth(240);

        menuTable.getColumns().setAll(List.of(name, price, time, ingredients));
        menuTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        menuTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> refreshSuitability());
        refreshMenu();
    }

    private void buildOrdersTable() {
        TableColumn<Order, String> number = new TableColumn<>("#");
        number.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getOrderNumber()));
        number.setPrefWidth(45);

        TableColumn<Order, String> meal = new TableColumn<>("Meal");
        meal.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getMeal().getName()));
        meal.setPrefWidth(140);

        TableColumn<Order, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell ->
                FxBindings.of(cell.getValue().getStatus().displayName()));
        status.setPrefWidth(150);

        TableColumn<Order, String> total = new TableColumn<>("Total");
        total.setCellValueFactory(cell -> FxBindings.of(
                // A running quote, so a pending order shows a real price rather than $0.00.
                cell.getValue().getFinalPrice()
                        .map(Object::toString)
                        .orElseGet(() -> app.pricingService().quote(cell.getValue()) + " est.")));
        total.setPrefWidth(90);

        ordersTable.getColumns().setAll(List.of(number, meal, status, total));
    }

    // ------------------------------------------------------------------ logic

    private void refreshProfile() {
        Customer customer = customerPicker.getValue();
        if (customer == null) {
            profileLabel.setText("");
            return;
        }
        profileLabel.setText("diet: "
                + orNone(String.join(", ", customer.getDietaryPreferences()))
                + "   |   allergies: "
                + orNone(String.join(", ", customer.getAllergies())));
    }

    private void refreshSuitability() {
        Customer customer = customerPicker.getValue();
        Meal meal = menuTable.getSelectionModel().getSelectedItem();
        if (customer == null || meal == null) {
            detailPane.clear();
            orderButton.setDisable(true);
            return;
        }

        OrderService.OrderPreview preview = app.orderService().preview(customer, meal);
        StringBuilder text = new StringBuilder();
        text.append(meal.getName()).append("  -  ").append(preview.price()).append("\n\n");

        if (preview.conflicts().isEmpty()) {
            text.append("No conflicts. This meal suits ").append(customer.getName()).append(".\n");
        } else {
            text.append("Conflicts:\n");
            for (Conflict conflict : preview.conflicts()) {
                text.append(conflict.type() == ConflictType.ALLERGY ? "  [allergy] " : "  [diet] ")
                        .append(conflict.reason()).append('\n');
            }
            text.append('\n');
            if (preview.substitutions().isEmpty()) {
                text.append("No suitable replacement is in stock, so this meal cannot be ordered.\n");
            } else {
                text.append("Proposed substitutions:\n");
                preview.substitutions().forEach((original, replacement) ->
                        text.append("  ").append(original).append("  ->  ")
                                .append(replacement).append('\n'));

                if (preview.hasUnresolvedConflicts()) {
                    // Never imply the substitutions fixed everything when they did not.
                    text.append("\nStill unresolved after those changes:\n");
                    for (Conflict conflict : preview.remainingConflicts()) {
                        text.append("  ").append(conflict.reason()).append('\n');
                    }
                    text.append(preview.hasUnresolvedAllergen()
                            ? "\nAn allergen cannot be replaced, so this meal cannot be ordered.\n"
                            : "\nYou can still order, but the meal will not fully match your diet.\n");
                } else {
                    text.append("\nOrdering will apply these and send the order for chef approval.\n");
                }
            }
        }

        if (preview.hasShortages()) {
            text.append("\nOut of stock: ").append(preview.shortages()).append('\n');
        }

        detailPane.setText(text.toString());
        boolean orderable = preview.canProceed()
                && (preview.conflicts().isEmpty() || !preview.substitutions().isEmpty());
        orderButton.setDisable(!orderable);
        orderButton.setText(preview.hasUnresolvedConflicts()
                ? "Place order anyway" : "Place order");
    }

    private void placeOrder() {
        Customer customer = customerPicker.getValue();
        Meal meal = menuTable.getSelectionModel().getSelectedItem();
        if (customer == null || meal == null) {
            return;
        }

        OrderService.OrderPreview preview = app.orderService().preview(customer, meal);
        Map<String, String> substitutions =
                preview.hasConflicts() ? preview.substitutions() : Map.of();

        try {
            Order order = app.orderService().place(customer, meal, substitutions);
            FxDialogs.info("Order placed",
                    "Order #" + order.getOrderNumber() + " - " + order.getStatus().displayName()
                            + (order.hasSubstitutions()
                            ? "\n\nSubstitutions: " + order.getSubstitutions()
                            : ""));
            refreshOrders();
            refreshSuitability();
        } catch (RuntimeException e) {
            FxDialogs.error("Could not place the order", e.getMessage());
        }
    }

    private void cancelSelectedOrder() {
        Order order = ordersTable.getSelectionModel().getSelectedItem();
        if (order == null) {
            return;
        }
        try {
            app.orderService().cancel(order);
            refreshOrders();
        } catch (RuntimeException e) {
            FxDialogs.error("Could not cancel the order", e.getMessage());
        }
    }

    private void showInvoice() {
        Order order = ordersTable.getSelectionModel().getSelectedItem();
        if (order == null) {
            return;
        }
        FxDialogs.text("Invoice #" + order.getOrderNumber(),
                app.pricingService().invoiceFor(order).format());
    }

    /** Re-reads everything from the services. Called by the other tabs after they change state. */
    public void refreshAll() {
        refreshMenu();
        refreshOrders();
        refreshSuitability();
    }

    private void refreshMenu() {
        Meal previouslySelected = menuTable.getSelectionModel().getSelectedItem();
        menuTable.setItems(FXCollections.observableArrayList(app.catalogService().allMeals()));
        // Select something straight away so the suitability panel is populated on open rather
        // than showing an empty box until the user happens to click a row.
        if (previouslySelected != null && menuTable.getItems().contains(previouslySelected)) {
            menuTable.getSelectionModel().select(previouslySelected);
        } else {
            menuTable.getSelectionModel().selectFirst();
        }
    }

    private void refreshOrders() {
        Customer customer = customerPicker.getValue();
        ordersTable.setItems(FXCollections.observableArrayList(
                customer == null ? List.<Order>of()
                        : app.orderRepository().findByCustomer(customer)));
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-label");
        return label;
    }

    private static String orNone(String value) {
        return value.isBlank() ? "none" : value;
    }
}
