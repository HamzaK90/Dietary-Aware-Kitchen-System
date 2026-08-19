package com.cookmgmt.domain;

import com.cookmgmt.domain.exception.OrderStateException;
import com.cookmgmt.support.Text;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A customer's request for one meal, with any ingredient substitutions and its progress through
 * the kitchen.
 *
 * <p>Notable changes from the original design:
 * <ul>
 *   <li>The display number no longer comes from a {@code static int nextId} counter. A static
 *       counter is shared process-wide and was never reset, so order numbers leaked between
 *       Cucumber scenarios and no test could assert on a specific one. The number is now handed in
 *       by {@link com.cookmgmt.repository.OrderRepository}, which owns its own sequence.</li>
 *   <li>{@code substitutionsApproved} was a {@code public} mutable field that any caller could
 *       flip, bypassing the state machine entirely. Approval is now a status transition.</li>
 *   <li>The final price is frozen at completion and is never recomputed afterwards, so a later
 *       change to inventory pricing cannot silently rewrite an issued invoice.</li>
 * </ul>
 */
public class Order extends Entity {

    private final int orderNumber;
    private final Customer customer;
    private final Meal meal;
    private final Map<String, String> substitutions = new LinkedHashMap<>();
    private final Instant placedAt;

    private OrderStatus status = OrderStatus.PENDING;
    private UUID assignedChefId;
    private Money finalPrice;
    private Instant completedAt;

    public Order(int orderNumber, Customer customer, Meal meal) {
        if (orderNumber <= 0) {
            throw new IllegalArgumentException("Order number must be positive");
        }
        this.orderNumber = orderNumber;
        this.customer = Objects.requireNonNull(customer, "customer");
        this.meal = Objects.requireNonNull(meal, "meal");
        this.placedAt = Instant.now();
    }

    /**
     * Records the substitutions this order will be cooked with and moves it to
     * {@link OrderStatus#NEEDS_APPROVAL}.
     *
     * @throws OrderStateException if the order has already been approved, rejected or finished
     */
    public void applySubstitutions(Map<String, String> requested) {
        if (requested == null || requested.isEmpty()) {
            return;
        }
        if (status != OrderStatus.PENDING && status != OrderStatus.NEEDS_APPROVAL) {
            throw new OrderStateException(
                    "Order #" + orderNumber + " can no longer be modified (status " + status + ")");
        }
        requested.forEach((original, replacement) -> {
            String from = Text.normalize(original);
            String to = Text.normalize(replacement);
            if (!from.isEmpty() && !to.isEmpty()) {
                substitutions.put(from, to);
            }
        });
        if (!substitutions.isEmpty()) {
            status = OrderStatus.NEEDS_APPROVAL;
        }
    }

    /** @return {@code true} when a chef still has to accept or refuse the substitutions */
    public boolean requiresApproval() {
        return status == OrderStatus.NEEDS_APPROVAL;
    }

    /** A chef accepted the substitutions; the order can now be cooked. */
    public void approveSubstitutions() {
        transitionTo(OrderStatus.APPROVED);
    }

    /**
     * A chef refused the substitutions. The order is terminal and its reserved stock is returned
     * by {@link com.cookmgmt.service.KitchenService}.
     */
    public void rejectSubstitutions() {
        transitionTo(OrderStatus.REJECTED);
    }

    public void markInProgress() {
        transitionTo(OrderStatus.IN_PROGRESS);
    }

    public void cancel() {
        transitionTo(OrderStatus.CANCELLED);
    }

    /**
     * Marks the order served at the price it was actually cooked for.
     *
     * @param price the total, computed once by
     *              {@link com.cookmgmt.service.PricingService} and frozen here
     */
    public void complete(Money price) {
        Objects.requireNonNull(price, "price");
        transitionTo(OrderStatus.COMPLETED);
        this.finalPrice = price;
        this.completedAt = Instant.now();
    }

    private void transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new OrderStateException(orderNumber, status, target);
        }
        this.status = target;
    }

    /**
     * The ingredients this order will really consume, with substitutions applied.
     *
     * <p>Both stock reservation and pricing read this, which is what keeps the two in agreement.
     */
    public Map<String, Integer> effectiveRecipe() {
        return meal.recipeWith(substitutions);
    }

    public int getOrderNumber() {
        return orderNumber;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Meal getMeal() {
        return meal;
    }

    /** @return an unmodifiable view of original ingredient to replacement ingredient */
    public Map<String, String> getSubstitutions() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(substitutions));
    }

    public boolean hasSubstitutions() {
        return !substitutions.isEmpty();
    }

    public OrderStatus getStatus() {
        return status;
    }

    public boolean isRejected() {
        return status == OrderStatus.REJECTED;
    }

    public boolean isCompleted() {
        return status == OrderStatus.COMPLETED;
    }

    public Optional<UUID> getAssignedChefId() {
        return Optional.ofNullable(assignedChefId);
    }

    public void assignTo(Chef chef) {
        this.assignedChefId = Objects.requireNonNull(chef, "chef").getId();
    }

    /**
     * @return the frozen total, or empty while the order is still in progress. The old model
     *         returned {@code 0.0} in that case, which the console printed as a real
     *         {@code Total: $0.00} on pending orders.
     */
    public Optional<Money> getFinalPrice() {
        return Optional.ofNullable(finalPrice);
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public Optional<Instant> getCompletedAt() {
        return Optional.ofNullable(completedAt);
    }

    @Override
    public String toString() {
        return "Order #" + orderNumber + " - " + meal.getName() + " [" + status + "]";
    }
}
