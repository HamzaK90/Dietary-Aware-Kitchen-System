package com.cookmgmt.bdd;

import com.cookmgmt.app.SampleData;
import com.cookmgmt.domain.ConflictType;
import com.cookmgmt.domain.Order;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Steps for {@code notifications.feature}. */
public class NotificationsSteps {

    private final TestContext context;
    private List<Order> queue = List.of();

    public NotificationsSteps(TestContext context) {
        this.context = context;
    }

    @Given("a chef has pending orders")
    public void aChefHasPendingOrders() {
        SampleData.loadInventory(context.app().inventoryService());
        context.givenChef("Chef Zaid", "zaid@kitchen.com");
        context.givenCustomer("Layla", List.of("Vegan"), List.of());
        context.givenMeal("Tofu Bowl", Map.of("tofu", 1, "rice", 2), 15);
        context.setOrder(context.app().orderService().place(context.customer(), context.meal()));
    }

    @Given("a customer with a {string} allergy selects a meal containing {string}")
    public void aCustomerWithAnAllergySelectsAMeal(String allergy, String ingredient) {
        SampleData.loadInventory(context.app().inventoryService());
        context.givenCustomer("Salma", List.of(), List.of(allergy));
        context.givenMeal("Nut Salad", Map.of(ingredient.toLowerCase(), 1), 10);
        context.setConflicts(context.app().ruleEngine()
                .check(context.meal(), context.customer()));
    }

    @When("the chef logs into the system")
    public void theChefLogsIntoTheSystem() {
        queue = context.app().kitchenService().queueFor(context.chef());
    }

    @When("the chef completes the order")
    public void theChefCompletesTheOrder() {
        context.setInvoice(context.app().kitchenService().complete(context.order()));
    }

    @Then("the system should list tasks with meal names and order IDs")
    public void theSystemShouldListTasks() {
        assertFalse(queue.isEmpty(), "Chef's queue was empty");
        for (Order order : queue) {
            assertFalse(order.getMeal().getName().isBlank(), "Task had no meal name");
            assertTrue(order.getOrderNumber() > 0, "Task had no order number");
        }
        // Reading the queue must not consume it. The old console used Queue.poll() to look at an
        // order, which removed it, and a rejected order then became unreachable entirely.
        assertFalse(context.app().kitchenService().queueFor(context.chef()).isEmpty(),
                "Reading the queue removed the order from it");
    }

    @Then("the chef should have been notified about the new order")
    public void theChefShouldHaveBeenNotified() {
        assertTrue(context.notifier().forRecipient(context.chef().getEmail()).stream()
                        .anyMatch(n -> n.message().contains("New order")),
                "Chef notifications were " + context.notifier().forRecipient(context.chef().getEmail()));
    }

    @Then("the system should warn about the conflict")
    public void theSystemShouldWarnAboutTheConflict() {
        assertFalse(context.conflicts().isEmpty(), "No conflict was detected");
    }

    @And("the warning should be an allergy conflict")
    public void theWarningShouldBeAnAllergyConflict() {
        assertTrue(context.conflicts().stream()
                        .anyMatch(conflict -> conflict.type() == ConflictType.ALLERGY),
                "Conflicts were " + context.conflicts());
    }

    @Then("the customer should have been notified that the order is ready")
    public void theCustomerShouldHaveBeenNotifiedOrderReady() {
        assertTrue(context.notifier().forRecipient(context.customer().getEmail()).stream()
                        .anyMatch(n -> n.message().contains("is ready")),
                "Customer notifications were "
                        + context.notifier().forRecipient(context.customer().getEmail()));
    }
}
