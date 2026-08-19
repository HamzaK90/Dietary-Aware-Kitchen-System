package com.cookmgmt.service;

import com.cookmgmt.domain.Chef;
import com.cookmgmt.domain.Invoice;
import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.OrderStatus;
import com.cookmgmt.domain.policy.ChefAssignmentStrategy;
import com.cookmgmt.notify.Notifier;
import com.cookmgmt.repository.ChefRepository;
import com.cookmgmt.repository.OrderRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything that happens to an order once it reaches the kitchen: assignment to a chef, review of
 * substitutions, cooking, and completion.
 *
 * <p>Two behaviours here did not exist before.
 *
 * <p><b>Rejection returns the ingredients.</b> Nothing released reserved stock, so the ingredients
 * of a refused order were simply lost from inventory.
 *
 * <p><b>A rejected order survives.</b> The console pulled the order off the chef's queue with
 * {@code poll()} and, on rejection, returned without putting it anywhere - the object was
 * unreachable from that point on, even though the customer menu offered a "Modify Pending Order"
 * action that went looking for exactly it. The queue is now derived from stored orders, so nothing
 * is consumed by being looked at.
 */
public class KitchenService {

    private final OrderRepository orders;
    private final ChefRepository chefs;
    private final InventoryService inventoryService;
    private final PricingService pricingService;
    private final Notifier notifier;
    private ChefAssignmentStrategy assignmentStrategy;

    public KitchenService(OrderRepository orders,
                          ChefRepository chefs,
                          InventoryService inventoryService,
                          PricingService pricingService,
                          ChefAssignmentStrategy assignmentStrategy,
                          Notifier notifier) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.chefs = Objects.requireNonNull(chefs, "chefs");
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService");
        this.assignmentStrategy = Objects.requireNonNull(assignmentStrategy, "assignmentStrategy");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    // ------------------------------------------------------------ assignment

    /**
     * Gives an order to a chef according to the current {@link ChefAssignmentStrategy}.
     *
     * @return the chef chosen, or empty if there are no chefs. The old code printed
     *         "No chefs available!" and returned, leaving the caller to believe the order had been
     *         accepted.
     */
    public Optional<Chef> assign(Order order) {
        Optional<Chef> chosen = assignmentStrategy.selectChef(chefs.findAll(), orders);
        chosen.ifPresent(chef -> {
            order.assignTo(chef);
            orders.save(order);
            notifier.notify(chef.getEmail(), "New order #" + order.getOrderNumber()
                    + " - " + order.getMeal().getName()
                    + (order.requiresApproval() ? " (needs your approval)" : ""));
        });
        return chosen;
    }

    /** Swaps the assignment policy at runtime; both interfaces expose this in the admin screens. */
    public void setAssignmentStrategy(ChefAssignmentStrategy strategy) {
        this.assignmentStrategy = Objects.requireNonNull(strategy, "strategy");
    }

    public ChefAssignmentStrategy getAssignmentStrategy() {
        return assignmentStrategy;
    }

    // ----------------------------------------------------------------- queue

    /** A chef's unfinished orders, oldest first. Reading it does not remove anything. */
    public List<Order> queueFor(Chef chef) {
        return orders.findQueueFor(chef);
    }

    /** The order a chef should look at next. */
    public Optional<Order> nextFor(Chef chef) {
        return queueFor(chef).stream().findFirst();
    }

    /** Orders anywhere in the kitchen still waiting for a chef to accept or refuse them. */
    public List<Order> awaitingApproval() {
        return orders.findByStatus(OrderStatus.NEEDS_APPROVAL);
    }

    // ------------------------------------------------------------- decisions

    /**
     * Chef accepts the substitutions. The order can now be cooked.
     *
     * <p>The decision itself is made by whoever is driving - a console prompt, a GUI dialog, or a
     * test - and passed in. The domain no longer opens a {@link java.util.Scanner} to ask.
     */
    public void approve(Order order) {
        order.approveSubstitutions();
        orders.save(order);
        notifier.notify(order.getCustomer().getEmail(),
                "Your substitutions for order #" + order.getOrderNumber() + " were approved.");
    }

    /** Chef refuses the substitutions: the order ends and its ingredients go back into stock. */
    public void reject(Order order) {
        var recipe = order.effectiveRecipe();
        order.rejectSubstitutions();
        orders.save(order);
        inventoryService.release(recipe);
        notifier.notify(order.getCustomer().getEmail(),
                "Order #" + order.getOrderNumber() + " was rejected by the chef. "
                        + "You can modify it and order again.");
    }

    public void startCooking(Order order) {
        order.markInProgress();
        orders.save(order);
    }

    /**
     * Finishes an order and issues its invoice.
     *
     * <p>An order that has not been started is moved through {@link OrderStatus#IN_PROGRESS} first,
     * so the caller does not have to know the intermediate step.
     *
     * @return the itemised invoice, priced once and frozen on the order
     */
    public Invoice complete(Order order) {
        if (order.getStatus() != OrderStatus.IN_PROGRESS) {
            startCooking(order);
        }
        Money total = pricingService.quote(order);
        order.complete(total);
        orders.save(order);

        Invoice invoice = pricingService.invoiceFor(order);
        notifier.notify(order.getCustomer().getEmail(),
                "Order #" + order.getOrderNumber() + " is ready. Total: " + total);
        return invoice;
    }

    /** Everything currently in the kitchen, across all chefs. */
    public List<Order> activeOrders() {
        return orders.findAll().stream()
                .filter(order -> order.getStatus().isActive())
                .toList();
    }
}
