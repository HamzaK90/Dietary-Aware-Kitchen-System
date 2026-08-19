package com.cookmgmt.bdd;

import com.cookmgmt.app.AppContext;
import com.cookmgmt.domain.Chef;
import com.cookmgmt.domain.Conflict;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Invoice;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.Order;
import com.cookmgmt.notify.InMemoryNotifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * State shared by every step definition class within a single scenario.
 *
 * <p>PicoContainer was already a declared dependency of this project, but nothing used it: each of
 * the nine step classes created its own {@code Admin} and {@code Inventory}. A {@code Given} step
 * in one class therefore set up a world that the {@code When} step in another class could not see,
 * so a scenario spanning more than one class could not work.
 *
 * <p>Cucumber creates one instance of this class per scenario and injects it into every step class
 * that declares it as a constructor parameter. That gives each scenario a clean
 * {@link AppContext} - fresh repositories, and an order-number sequence that starts again at 1,
 * which the old {@code static int nextId} counter on {@code Order} could never do.
 */
public class TestContext {

    private final InMemoryNotifier notifier = new InMemoryNotifier();
    private final AppContext app = new AppContext(notifier);

    // --- scenario working state --------------------------------------------
    private Customer customer;
    private Chef chef;
    private Meal meal;
    private Order order;
    private Invoice invoice;
    private Money price;
    private List<Conflict> conflicts = new ArrayList<>();
    private Map<String, String> substitutions = new LinkedHashMap<>();
    private List<String> suggestions = new ArrayList<>();
    private Exception thrownException;

    public AppContext app() {
        return app;
    }

    public InMemoryNotifier notifier() {
        return notifier;
    }

    /**
     * Runs an action that is expected to fail, storing the exception for a later {@code Then}.
     *
     * <p>Lets a scenario assert that an operation was refused, which the old suite had no way to
     * express - it could only assert on things that succeeded.
     */
    public void attempt(Runnable action) {
        try {
            action.run();
            thrownException = null;
        } catch (Exception e) {
            thrownException = e;
        }
    }

    public Exception thrownException() {
        return thrownException;
    }

    public Customer customer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Chef chef() {
        return chef;
    }

    public void setChef(Chef chef) {
        this.chef = chef;
    }

    public Meal meal() {
        return meal;
    }

    public void setMeal(Meal meal) {
        this.meal = meal;
    }

    public Order order() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Invoice invoice() {
        return invoice;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public Money price() {
        return price;
    }

    public void setPrice(Money price) {
        this.price = price;
    }

    public List<Conflict> conflicts() {
        return conflicts;
    }

    public void setConflicts(List<Conflict> conflicts) {
        this.conflicts = conflicts;
    }

    public Map<String, String> substitutions() {
        return substitutions;
    }

    public void setSubstitutions(Map<String, String> substitutions) {
        this.substitutions = substitutions;
    }

    public List<String> suggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    // --- convenience builders used across several step classes --------------

    /** Registers a customer with a generated email based on their name. */
    public Customer givenCustomer(String name, List<String> preferences, List<String> allergies) {
        Customer created = app.customerService().register(
                name, name.toLowerCase().replace(" ", ".") + "@example.com",
                preferences, allergies);
        this.customer = created;
        return created;
    }

    public Chef givenChef(String name, String email) {
        Chef created = app.staffService().hire(name, email);
        this.chef = created;
        return created;
    }

    /** Adds a meal to the menu and makes every ingredient it needs available in stock. */
    public Meal givenMeal(String name, Map<String, Integer> ingredients, int cookingTime) {
        ingredients.forEach((ingredient, quantity) -> {
            if (!app.inventory().isKnown(ingredient)) {
                app.inventoryService().addIngredient(ingredient, quantity * 10, Money.of("1.00"));
            }
        });
        Meal created = app.catalogService().addMeal(name, ingredients, cookingTime);
        this.meal = created;
        return created;
    }
}
