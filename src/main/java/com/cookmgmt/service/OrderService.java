package com.cookmgmt.service;

import com.cookmgmt.domain.Conflict;
import com.cookmgmt.domain.ConflictType;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.rule.DietaryRuleEngine;
import com.cookmgmt.notify.Notifier;
import com.cookmgmt.repository.OrderRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Placing, previewing and cancelling orders. The one path into the kitchen, shared by the console
 * and the GUI.
 *
 * <p>There used to be two competing implementations of "place an order" that behaved differently:
 * <ul>
 *   <li>{@code Customer.placeOrder} checked conflicts, applied substitutions and deducted stock -
 *       but nothing called it;</li>
 *   <li>{@code Main.placeOrder}, the one the application actually used, constructed the
 *       {@code Order} itself and never touched inventory.</li>
 * </ul>
 * With a single entry point the two cannot disagree, and both interfaces get identical behaviour by
 * construction - which is the practical payoff of the whole restructuring.
 */
public class OrderService {

    private final OrderRepository orders;
    private final InventoryService inventoryService;
    private final PricingService pricingService;
    private final DietaryRuleEngine ruleEngine;
    private final SubstitutionService substitutionService;
    private final KitchenService kitchenService;
    private final Notifier notifier;

    public OrderService(OrderRepository orders,
                        InventoryService inventoryService,
                        PricingService pricingService,
                        DietaryRuleEngine ruleEngine,
                        SubstitutionService substitutionService,
                        KitchenService kitchenService,
                        Notifier notifier) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService");
        this.ruleEngine = Objects.requireNonNull(ruleEngine, "ruleEngine");
        this.substitutionService = Objects.requireNonNull(substitutionService, "substitutionService");
        this.kitchenService = Objects.requireNonNull(kitchenService, "kitchenService");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Works out what would happen if this customer ordered this meal, without ordering it.
     *
     * <p>Lets the interfaces show conflicts, proposed substitutions, shortages and a price before
     * the customer commits - and lets a test assert on all of it without side effects.
     */
    public OrderPreview preview(Customer customer, Meal meal) {
        List<Conflict> conflicts = ruleEngine.check(meal, customer);
        Map<String, String> proposal = substitutionService.proposeFor(meal, customer);
        Map<String, Integer> recipe = meal.recipeWith(proposal);
        return new OrderPreview(
                meal,
                conflicts,
                proposal,
                // What is still wrong after the proposed changes. Not every conflict can be
                // resolved - an ingredient may have no suitable replacement in stock - and the
                // interfaces must say so rather than implying the substitutions fixed everything.
                ruleEngine.check(recipe.keySet(), customer),
                inventoryService.shortages(recipe),
                pricingService.priceOf(recipe));
    }

    /** Places an order exactly as the meal is written, with no substitutions. */
    public Order place(Customer customer, Meal meal) {
        return place(customer, meal, Map.of());
    }

    /**
     * Places an order, reserving its ingredients and handing it to a chef.
     *
     * <p>Order of operations matters: stock is reserved <em>before</em> the order is stored and
     * assigned, so a shortage aborts cleanly without leaving an order in the system that the
     * kitchen cannot cook.
     *
     * @param substitutions original ingredient to replacement; may be empty
     * @return the stored order, already assigned to a chef if any chef exists
     * @throws IllegalArgumentException if the meal would still contain one of the customer's
     *         allergens after substitution
     * @throws com.cookmgmt.domain.exception.InsufficientStockException if the kitchen cannot supply
     *         the recipe
     */
    public Order place(Customer customer, Meal meal, Map<String, String> substitutions) {
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(meal, "meal");

        Map<String, Integer> recipe = meal.recipeWith(substitutions);

        // An allergy is never something a customer can choose to order through, so it is checked
        // against the ingredients that will actually be used rather than the ones on the menu.
        List<Conflict> remaining = ruleEngine.check(recipe.keySet(), customer).stream()
                .filter(conflict -> conflict.type() == ConflictType.ALLERGY)
                .toList();
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException(
                    "Order would still contain an allergen: " + remaining.get(0).reason());
        }

        inventoryService.reserve(recipe);

        Order order = new Order(orders.nextOrderNumber(), customer, meal);
        order.applySubstitutions(substitutions);
        orders.save(order);

        if (kitchenService.assign(order).isEmpty()) {
            notifier.notify(customer.getEmail(), "Order #" + order.getOrderNumber()
                    + " was placed but no chef is available yet.");
        }
        return order;
    }

    /** Withdraws an order and returns its ingredients to stock. */
    public void cancel(Order order) {
        Map<String, Integer> recipe = order.effectiveRecipe();
        order.cancel();
        orders.save(order);
        inventoryService.release(recipe);
        notifier.notify(order.getCustomer().getEmail(),
                "Order #" + order.getOrderNumber() + " was cancelled.");
    }

    public List<Order> historyFor(Customer customer) {
        return orders.findHistory(customer);
    }

    public List<Order> activeFor(Customer customer) {
        return orders.findActiveFor(customer);
    }

    public OrderRepository repository() {
        return orders;
    }

    /**
     * What a prospective order looks like before it is placed.
     *
     * @param meal                the meal requested
     * @param conflicts           every dietary or allergy clash in the meal as written
     * @param substitutions       the replacements the system proposes to resolve them
     * @param remainingConflicts  clashes that would still be present after those substitutions,
     *                            because no suitable replacement is in stock
     * @param shortages           ingredients the kitchen cannot currently supply, after substitution
     * @param price               what the order would cost, after substitution
     */
    public record OrderPreview(Meal meal,
                               List<Conflict> conflicts,
                               Map<String, String> substitutions,
                               List<Conflict> remainingConflicts,
                               List<InventoryService.Shortage> shortages,
                               Money price) {

        public boolean hasConflicts() {
            return !conflicts.isEmpty();
        }

        public boolean hasAllergyConflict() {
            return conflicts.stream().anyMatch(c -> c.type() == ConflictType.ALLERGY);
        }

        /** @return {@code true} if something the customer cannot eat survives the substitutions */
        public boolean hasUnresolvedConflicts() {
            return !remainingConflicts.isEmpty();
        }

        /**
         * @return {@code true} if an <em>allergen</em> survives the substitutions, which blocks the
         *         order outright - unlike a dietary preference, which the customer may accept
         */
        public boolean hasUnresolvedAllergen() {
            return remainingConflicts.stream().anyMatch(c -> c.type() == ConflictType.ALLERGY);
        }

        public boolean hasShortages() {
            return !shortages.isEmpty();
        }

        /** @return {@code true} if the order can go ahead once the proposed substitutions apply */
        public boolean canProceed() {
            return !hasShortages() && !hasUnresolvedAllergen();
        }
    }
}
