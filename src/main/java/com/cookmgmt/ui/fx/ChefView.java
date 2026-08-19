package com.cookmgmt.ui.fx;

import com.cookmgmt.app.AppContext;
import com.cookmgmt.domain.Chef;
import com.cookmgmt.domain.Invoice;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.OrderStatus;
import com.cookmgmt.inventory.ReadableInventory;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Chef tab: work queue, substitution review, cooking, and the low-stock panel.
 *
 * <p>The approve/reject buttons are the GUI's answer to the prompt that used to sit inside
 * {@code Chef.reviewOrder} as a {@link java.util.Scanner} read. Because the decision now arrives
 * from the interface layer, the same {@code KitchenService.approve}/{@code reject} calls serve the
 * console, this screen and the tests - and the {@code setTestAutoApprove} flag that existed purely
 * to bypass that prompt is gone.
 */
public class ChefView implements Refreshable {

    private final AppContext app;
    private final ViewRefresher refresher;

    private final ComboBox<Chef> chefPicker = new ComboBox<>();
    private final TableView<Order> queueTable = new TableView<>();
    private final TextArea orderDetail = new TextArea();
    private final ListView<String> lowStockList = new ListView<>();
    private final Label queueSummary = new Label();

    private final Button approveButton = new Button("Approve substitutions");
    private final Button rejectButton = new Button("Reject");
    private final Button startButton = new Button("Start cooking");
    private final Button completeButton = new Button("Complete order");

    public ChefView(AppContext app, ViewRefresher refresher) {
        this.app = app;
        this.refresher = refresher;
    }

    public Region build() {
        buildChefPicker();
        buildQueueTable();
        buildButtons();

        orderDetail.setEditable(false);
        orderDetail.getStyleClass().add("detail-pane");
        orderDetail.setPromptText("Select an order from the queue.");

        HBox actions = new HBox(8, approveButton, rejectButton, startButton, completeButton);

        VBox left = new VBox(8, sectionLabel("Work queue"), queueTable, queueSummary);
        VBox.setVgrow(queueTable, Priority.ALWAYS);
        left.setPadding(new Insets(12));

        VBox right = new VBox(8,
                sectionLabel("Order detail"), orderDetail, actions,
                sectionLabel("Low stock"), lowStockList);
        VBox.setVgrow(orderDetail, Priority.ALWAYS);
        right.setPadding(new Insets(12));

        HBox body = new HBox(left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        VBox root = new VBox(8, chefBar(), body);
        VBox.setVgrow(body, Priority.ALWAYS);

        // Populates the queue, the low-stock list and the button states in one place, so none of
        // them depends on a build step happening to call it.
        refreshAll();
        return root;
    }

    private HBox chefBar() {
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refreshAll());

        HBox bar = new HBox(10, new Label("Signed in as:"), chefPicker, refresh);
        bar.setPadding(new Insets(12, 12, 0, 12));
        return bar;
    }

    private void buildChefPicker() {
        chefPicker.setItems(FXCollections.observableArrayList(app.staffService().allChefs()));
        chefPicker.setConverter(new SimpleConverter<>(Chef::getName));
        chefPicker.getSelectionModel().selectFirst();
        chefPicker.valueProperty().addListener((obs, old, selected) -> refreshAll());
    }

    private void buildQueueTable() {
        TableColumn<Order, String> number = new TableColumn<>("#");
        number.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getOrderNumber()));
        number.setPrefWidth(45);

        TableColumn<Order, String> meal = new TableColumn<>("Meal");
        meal.setCellValueFactory(cell -> FxBindings.of(cell.getValue().getMeal().getName()));
        meal.setPrefWidth(140);

        TableColumn<Order, String> customer = new TableColumn<>("Customer");
        customer.setCellValueFactory(cell ->
                FxBindings.of(cell.getValue().getCustomer().getName()));
        customer.setPrefWidth(100);

        TableColumn<Order, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell ->
                FxBindings.of(cell.getValue().getStatus().displayName()));
        status.setPrefWidth(150);

        queueTable.getColumns().setAll(List.of(number, meal, customer, status));
        queueTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> refreshDetail());
        refreshQueue();
    }

    private void buildButtons() {
        // Every one of these changes stock, the customer's order list, or the admin report, so
        // they refresh the whole application rather than only this tab.
        approveButton.getStyleClass().add("primary-button");
        approveButton.setOnAction(event -> withSelectedOrder(order -> {
            app.kitchenService().approve(order);
            refresher.refreshAll();
        }));

        rejectButton.setOnAction(event -> withSelectedOrder(order -> {
            if (FxDialogs.confirm("Reject order #" + order.getOrderNumber(),
                    "The order will end and its ingredients will be returned to stock.")) {
                app.kitchenService().reject(order);
                refresher.refreshAll();
            }
        }));

        startButton.setOnAction(event -> withSelectedOrder(order -> {
            app.kitchenService().startCooking(order);
            refresher.refreshAll();
        }));

        completeButton.setOnAction(event -> withSelectedOrder(order -> {
            Invoice invoice = app.kitchenService().complete(order);
            FxDialogs.text("Order #" + order.getOrderNumber() + " completed", invoice.format());
            refresher.refreshAll();
        }));
    }

    /**
     * Mirrors {@link com.cookmgmt.service.KitchenService#complete}, which walks an order that has
     * not been started through {@code IN_PROGRESS} on the caller's behalf. An order still awaiting
     * approval cannot get there, so it cannot be completed either.
     */
    private static boolean canComplete(Order order) {
        return order.getStatus() == OrderStatus.IN_PROGRESS
                || order.getStatus().canTransitionTo(OrderStatus.IN_PROGRESS);
    }

    private void withSelectedOrder(java.util.function.Consumer<Order> action) {
        Order order = queueTable.getSelectionModel().getSelectedItem();
        if (order == null) {
            FxDialogs.warn("No order selected", "Pick an order from the queue first.");
            return;
        }
        try {
            action.accept(order);
        } catch (RuntimeException e) {
            // The order state machine refuses illegal transitions rather than applying them
            // silently, so the message here is a real explanation.
            FxDialogs.error("That is not possible right now", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ state

    @Override
    public void refreshAll() {
        refreshQueue();
        refreshLowStock();
        refreshDetail();
    }

    private void refreshQueue() {
        Chef chef = chefPicker.getValue();
        List<Order> queue = chef == null ? List.of() : app.kitchenService().queueFor(chef);
        // Re-selecting by order number keeps the chef's place in the queue after a refresh, which
        // now happens whenever any tab changes something.
        Order previous = queueTable.getSelectionModel().getSelectedItem();
        queueTable.setItems(FXCollections.observableArrayList(queue));
        if (previous != null) {
            queue.stream()
                    .filter(order -> order.getOrderNumber() == previous.getOrderNumber())
                    .findFirst()
                    .ifPresent(order -> queueTable.getSelectionModel().select(order));
        }

        // See CustomerView.refreshOrders: identity-based equality means JavaFX treats an order
        // whose status changed as unchanged, so the cells must be told to re-read explicitly.
        queueTable.refresh();

        queueSummary.setText(queue.size() + " order(s) waiting; "
                + app.kitchenService().awaitingApproval().size()
                + " awaiting approval across the kitchen.");
    }

    private void refreshDetail() {
        Order order = queueTable.getSelectionModel().getSelectedItem();
        boolean none = order == null;

        // Asked of the state machine rather than restated here. "Not terminal" was too loose: an
        // order still awaiting approval is not terminal, but starting it is an illegal transition,
        // so the button offered an action that could only ever throw.
        approveButton.setDisable(none || !order.requiresApproval());
        rejectButton.setDisable(none || !order.requiresApproval());
        startButton.setDisable(none || !order.getStatus().canTransitionTo(OrderStatus.IN_PROGRESS));
        completeButton.setDisable(none || !canComplete(order));

        if (none) {
            orderDetail.clear();
            return;
        }

        StringBuilder text = new StringBuilder();
        text.append("Order #").append(order.getOrderNumber())
                .append("  -  ").append(order.getMeal().getName()).append('\n')
                .append("Customer:  ").append(order.getCustomer().getName()).append('\n')
                .append("Status:    ").append(order.getStatus().displayName()).append('\n')
                .append("Cook time: ").append(order.getMeal().getCookingTimeMinutes())
                .append(" min\n\n");

        text.append("Ingredients to use:\n");
        order.effectiveRecipe().forEach((ingredient, quantity) ->
                text.append("  ").append(quantity).append(" x ").append(ingredient).append('\n'));

        if (order.hasSubstitutions()) {
            text.append("\nSubstitutions requested:\n");
            order.getSubstitutions().forEach((original, replacement) ->
                    text.append("  ").append(original).append("  ->  ")
                            .append(replacement).append('\n'));
            if (order.requiresApproval()) {
                text.append("\nThis order needs your decision before it can be cooked.\n");
            }
        }

        text.append("\nRunning total: ").append(app.pricingService().quote(order)).append('\n');
        orderDetail.setText(text.toString());
    }

    private void refreshLowStock() {
        List<String> low = app.inventoryService().lowStock().stream()
                .map(ingredient -> ingredient + "  -  "
                        + app.inventory().stockOf(ingredient) + " left")
                .toList();
        lowStockList.setItems(FXCollections.observableArrayList(
                low.isEmpty()
                        ? List.of("Everything is above " + ReadableInventory.LOW_STOCK_THRESHOLD + " units")
                        : low));
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-label");
        return label;
    }
}
