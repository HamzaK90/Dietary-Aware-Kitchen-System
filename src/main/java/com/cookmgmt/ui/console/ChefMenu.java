package com.cookmgmt.ui.console;

import com.cookmgmt.app.AppContext;
import com.cookmgmt.domain.Chef;
import com.cookmgmt.domain.Invoice;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.OrderStatus;
import com.cookmgmt.domain.exception.OrderStateException;

import java.util.List;
import java.util.Optional;

/**
 * The chef-facing part of the console interface: see the queue, review substitutions, cook, and
 * check stock.
 *
 * <p>The approve/reject prompt lives here rather than in {@code Chef}, which is what allowed the
 * {@code setTestAutoApprove} hook to be deleted from the domain.
 */
public class ChefMenu {

    private final AppContext app;
    private final ConsoleIO io;

    public ChefMenu(AppContext app, ConsoleIO io) {
        this.app = app;
        this.io = io;
    }

    public void run() {
        io.heading("Chef Login");
        String email = io.ask("Email (or \"cancel\"): ");
        if (email.isEmpty() || email.equalsIgnoreCase(ScannerConsoleIO.CANCEL)) {
            return;
        }

        Optional<Chef> login = app.staffService().login(email);
        if (login.isEmpty()) {
            io.print("No chef is registered with that address.");
            return;
        }
        Chef chef = login.get();

        while (true) {
            List<Order> queue = app.kitchenService().queueFor(chef);
            io.heading("Chef Panel - " + chef.getName() + " (" + queue.size() + " in queue)");
            io.print("1. View my queue");
            io.print("2. Work on the next order");
            io.print("3. Low stock report");
            io.print("4. Log out");

            switch (io.askInt("Select: ", 1, 4)) {
                case 1 -> showQueue(chef);
                case 2 -> workOnNextOrder(chef);
                case 3 -> showLowStock();
                case 4 -> {
                    return;
                }
                default -> { }
            }
        }
    }

    private void showQueue(Chef chef) {
        io.heading("My Queue");
        List<Order> queue = app.kitchenService().queueFor(chef);
        if (queue.isEmpty()) {
            io.print("Nothing waiting.");
            return;
        }
        for (Order order : queue) {
            io.print(String.format("#%d  %-16s %-24s %d min",
                    order.getOrderNumber(),
                    order.getMeal().getName(),
                    order.getStatus().displayName(),
                    order.getMeal().getCookingTimeMinutes()));
        }
    }

    private void workOnNextOrder(Chef chef) {
        // Looking at the next order no longer removes it from the queue: the queue is derived from
        // stored orders rather than being a Queue that poll() consumes.
        Optional<Order> next = app.kitchenService().nextFor(chef);
        if (next.isEmpty()) {
            io.print("Nothing waiting.");
            return;
        }
        Order order = next.get();

        io.heading("Order #" + order.getOrderNumber() + " - " + order.getMeal().getName());
        io.print("For:         " + order.getCustomer().getName());
        io.print("Ingredients: " + order.effectiveRecipe());
        io.print("Status:      " + order.getStatus().displayName());

        if (order.requiresApproval()) {
            io.blankLine();
            io.print("This order has substitutions:");
            order.getSubstitutions().forEach((original, replacement) ->
                    io.print("  - " + original + " replaced with " + replacement));

            if (io.confirm("Approve these substitutions?")) {
                app.kitchenService().approve(order);
                io.print("Approved.");
            } else {
                app.kitchenService().reject(order);
                io.print("Rejected. The ingredients have been returned to stock and the "
                        + "customer has been notified.");
                return;
            }
        }

        if (!io.confirm("Cook and complete this order now?")) {
            io.print("Left in the queue.");
            return;
        }

        try {
            Invoice invoice = app.kitchenService().complete(order);
            io.print("Order #" + order.getOrderNumber() + " completed.");
            io.blankLine();
            io.print(invoice.format());
        } catch (OrderStateException e) {
            io.print("Could not complete the order: " + e.getMessage());
        }
    }

    private void showLowStock() {
        io.heading("Low Stock");
        List<String> low = app.inventoryService().lowStock();
        if (low.isEmpty()) {
            io.print("Everything is well stocked.");
            return;
        }
        for (String ingredient : low) {
            io.print(String.format("  %-16s %d left",
                    ingredient, app.inventory().stockOf(ingredient)));
        }
    }

    /** Orders across the whole kitchen still waiting for someone to review them. */
    public void showAwaitingApproval() {
        io.heading("Awaiting Approval");
        List<Order> waiting = app.kitchenService().awaitingApproval();
        if (waiting.isEmpty()) {
            io.print("Nothing waiting for approval.");
            return;
        }
        waiting.forEach(order -> io.print("#" + order.getOrderNumber()
                + " " + order.getMeal().getName()
                + " " + OrderStatus.NEEDS_APPROVAL.displayName()));
    }
}
