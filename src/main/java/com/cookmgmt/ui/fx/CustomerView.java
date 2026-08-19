package com.cookmgmt.ui.fx;

import com.cookmgmt.app.AppContext;
import com.cookmgmt.domain.Conflict;
import com.cookmgmt.domain.ConflictType;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.OrderStatus;
import com.cookmgmt.service.OrderService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
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
public class CustomerView implements Refreshable {

    private final AppContext app;
    private final ViewRefresher refresher;

    private final ComboBox<Customer> customerPicker = new ComboBox<>();
    private final TableView<Meal> menuTable = new TableView<>();
    private final TableView<Order> ordersTable = new TableView<>();
    private final TextArea detailPane = new TextArea();
    private final Label profileLabel = new Label();
    private final Button orderButton = new Button("Place order");
    private final Button billButton = new Button("View statement");
    private final Button clearSelectionButton = new Button("Clear selection");
    private final Button cancelButton = new Button("Cancel order");

    public CustomerView(AppContext app, ViewRefresher refresher) {
        this.app = app;
        this.refresher = refresher;
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

        // One button covering both readings of "bill me": with an order selected it shows that
        // order alone, with nothing selected it sums the lot. The label says which it will do, so
        // the two are never confused.
        billButton.setOnAction(event -> showBill());

        clearSelectionButton.setOnAction(event -> ordersTable.getSelectionModel().clearSelection());
        clearSelectionButton.setTooltip(new Tooltip(
                "Deselect the order to see a statement for every order instead."));

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
                new HBox(8, billButton, clearSelectionButton, cancelButton));
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
        total.setCellValueFactory(cell -> FxBindings.of(totalTextFor(cell.getValue())));
        total.setPrefWidth(90);

        ordersTable.getColumns().setAll(List.of(number, meal, status, total));
        ordersTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        ordersTable.setPlaceholder(new Label("No orders yet."));

        // Which buttons make sense depends entirely on what is selected, so the state is
        // recomputed on every selection change rather than being set once at build time.
        ordersTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> updateOrderButtons());

        // Deselecting has to be possible, because "no selection" is what asks for the statement.
        // Escape is the conventional key for it...
        ordersTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                ordersTable.getSelectionModel().clearSelection();
                event.consume();
            }
        });

        // ...and ctrl/cmd-clicking the selected row toggles it off. This is an event filter rather
        // than a click handler so it runs before the table's own selection behaviour, which would
        // otherwise re-select the row immediately afterwards.
        ordersTable.setRowFactory(table -> {
            TableRow<Order> row = new TableRow<>();
            row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (!row.isEmpty()
                        && (event.isControlDown() || event.isMetaDown())
                        && row.getItem().equals(ordersTable.getSelectionModel().getSelectedItem())) {
                    ordersTable.getSelectionModel().clearSelection();
                    event.consume();
                }
            });
            return row;
        });

        // Populate straight away. The customer picker selects its first entry before its listener
        // is attached, so without this the orders table stayed empty until the user happened to
        // switch customer.
        refreshOrders();
    }

    /** The price column: an order nobody will cook has no total, not an estimate. */
    private String totalTextFor(Order order) {
        if (order.getFinalPrice().isPresent()) {
            return order.getFinalPrice().get().toString();
        }
        // A running quote, so a pending order shows a real price rather than $0.00 - but only
        // while the order can still be served.
        return order.getStatus().isActive()
                ? app.pricingService().quote(order) + " est."
                : "-";
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
            // Placing an order consumes stock and lands in a chef's queue, so the other tabs are
            // out of date too.
            refresher.refreshAll();
        } catch (RuntimeException e) {
            FxDialogs.error("Could not place the order", e.getMessage());
        }
    }

    /**
     * Enables the order buttons according to what is selected.
     *
     * <p>Whether an order can be cancelled is not restated here: it is asked of
     * {@link OrderStatus#canTransitionTo}, the same state machine the service enforces. Writing the
     * rule out a second time in the interface is how a screen ends up offering an action the domain
     * will refuse.
     */
    private void updateOrderButtons() {
        Order order = ordersTable.getSelectionModel().getSelectedItem();

        clearSelectionButton.setDisable(order == null);

        if (order == null) {
            billButton.setText("View statement");
            billButton.setDisable(false);
            billButton.setTooltip(new Tooltip(
                    "Shows every order for this customer with a grand total."));

            cancelButton.setDisable(true);
            cancelButton.setTooltip(new Tooltip("Select one of your orders to cancel it."));
            return;
        }

        boolean billable = order.isCompleted() || order.getStatus().isActive();
        billButton.setText(order.isCompleted()
                ? "View invoice (#" + order.getOrderNumber() + ")"
                : "View estimate (#" + order.getOrderNumber() + ")");
        billButton.setDisable(!billable);
        billButton.setTooltip(new Tooltip(billable
                ? "Shows this order on its own. Clear the selection for a full statement."
                : "A " + order.getStatus().displayName().toLowerCase()
                        + " order was never cooked, so it is not billed."));

        boolean cancellable = order.getStatus().canTransitionTo(OrderStatus.CANCELLED);
        cancelButton.setDisable(!cancellable);
        cancelButton.setTooltip(new Tooltip(cancellable
                ? "Withdraws order #" + order.getOrderNumber()
                        + " and returns its ingredients to stock."
                : cannotCancelBecause(order)));
    }

    private static String cannotCancelBecause(Order order) {
        return switch (order.getStatus()) {
            case IN_PROGRESS -> "This order is already being cooked, so it can no longer be cancelled.";
            case COMPLETED -> "This order has already been served.";
            case CANCELLED -> "This order is already cancelled.";
            case REJECTED -> "This order was rejected by the chef.";
            default -> "This order cannot be cancelled right now.";
        };
    }

    private void cancelSelectedOrder() {
        Order order = ordersTable.getSelectionModel().getSelectedItem();
        if (order == null) {
            return;
        }

        String returning = order.effectiveRecipe().entrySet().stream()
                .map(entry -> entry.getValue() + " x " + entry.getKey())
                .reduce((a, b) -> a + ", " + b)
                .orElse("nothing");

        if (!FxDialogs.confirm("Cancel order #" + order.getOrderNumber(),
                "Withdraw \"" + order.getMeal().getName() + "\"?\n\n"
                        + "These ingredients go back into stock: " + returning)) {
            return;
        }

        try {
            app.orderService().cancel(order);
            // Cancelling changes stock, the chef's queue and the admin report, so every screen is
            // rebuilt - not just this one. Refreshing locally is what left the other tabs stale.
            refresher.refreshAll();
        } catch (RuntimeException e) {
            // Kept even though the button is disabled for uncancellable orders: the service, not
            // the button, is what actually decides.
            FxDialogs.error("Could not cancel the order", e.getMessage());
        }
    }

    /**
     * Shows one order's bill, or the whole account when nothing is selected.
     *
     * <p>A cancelled or rejected order reaches neither: it was never cooked, so there is nothing to
     * charge for. Previously this method invoiced whatever row happened to be selected, and
     * {@code PricingService.invoiceFor} quietly falls back to a live quote when an order has no
     * frozen price - so a cancelled order rendered a complete, entirely fictional bill.
     */
    private void showBill() {
        Order order = ordersTable.getSelectionModel().getSelectedItem();

        if (order == null) {
            Customer customer = customerPicker.getValue();
            if (customer == null) {
                return;
            }
            FxDialogs.text("Statement - " + customer.getName(),
                    app.pricingService()
                            .statementFor(customer.getName(),
                                    app.orderRepository().findByCustomer(customer))
                            .format());
            return;
        }

        if (order.isCompleted()) {
            FxDialogs.text("Invoice #" + order.getOrderNumber(),
                    app.pricingService().invoiceFor(order).format());
        } else if (order.getStatus().isActive()) {
            FxDialogs.text("Estimate for order #" + order.getOrderNumber(),
                    "ESTIMATE - this order has not been served yet, so it has not been billed."
                            + System.lineSeparator()
                            + "Status: " + order.getStatus().displayName()
                            + System.lineSeparator() + System.lineSeparator()
                            + app.pricingService().invoiceFor(order).format());
        }
    }

    /**
     * Re-reads everything from the services.
     *
     * <p>Called through {@link ViewRefresher} whenever any tab changes shared state - a chef
     * completing an order, an admin restocking - so this screen never shows a stale menu price or
     * an order status that has since moved on.
     */
    @Override
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

        // setItems clears the selection, which would silently flip the bill button back to
        // statement mode every time anything in the kitchen changed. Order numbers are unique
        // across the system, so re-selecting by number restores exactly the same order.
        Order previous = ordersTable.getSelectionModel().getSelectedItem();

        ordersTable.setItems(FXCollections.observableArrayList(
                customer == null ? List.<Order>of()
                        : app.orderRepository().findByCustomer(customer)));

        if (previous != null) {
            ordersTable.getItems().stream()
                    .filter(order -> order.getOrderNumber() == previous.getOrderNumber())
                    .findFirst()
                    .ifPresent(order -> ordersTable.getSelectionModel().select(order));
        }

        // Forces every visible cell to re-read its value. Without it the Status column goes stale:
        // Entity.equals compares by identity, so an order whose status changed is still "equal" to
        // the one the cell already holds, and JavaFX skips the update as a no-op. Replacing the
        // items list is not enough - the rows are the same objects in the same positions.
        ordersTable.refresh();

        updateOrderButtons();
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
