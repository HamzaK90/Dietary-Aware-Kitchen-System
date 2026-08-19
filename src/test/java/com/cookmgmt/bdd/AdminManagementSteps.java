package com.cookmgmt.bdd;

import com.cookmgmt.domain.Chef;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Order;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Steps for {@code admin_management.feature}. */
public class AdminManagementSteps {

    private final TestContext context;
    private Meal.Builder mealBuilder;

    public AdminManagementSteps(TestContext context) {
        this.context = context;
    }

    @Given("the admin is on the meal management panel")
    public void theAdminIsOnTheMealManagementPanel() {
        assertTrue(context.app().catalogService().allMeals().isEmpty(),
                "A new scenario should start with an empty menu");
    }

    @Given("the admin is on the chef management panel")
    public void theAdminIsOnTheChefManagementPanel() {
        assertTrue(context.app().staffService().allChefs().isEmpty(),
                "A new scenario should start with no chefs");
    }

    @When("the admin creates a meal named {string}")
    public void theAdminCreatesAMealNamed(String name) {
        // A Meal is never observable half-built: the builder holds the partial state until the
        // cooking time arrives and validation can run.
        mealBuilder = Meal.builder().name(name);
    }

    @And("adds ingredients {string} with quantity {int}, {string} with quantity {int}, {string} with quantity {int}")
    public void addsIngredients(String first, Integer firstQty,
                                String second, Integer secondQty,
                                String third, Integer thirdQty) {
        mealBuilder.ingredient(first, firstQty)
                .ingredient(second, secondQty)
                .ingredient(third, thirdQty);
    }

    @And("sets the cooking time to {int} minutes")
    public void setsTheCookingTime(Integer minutes) {
        Meal meal = mealBuilder.cookingTimeMinutes(minutes).build();
        context.setMeal(context.app().catalogService().addMeal(meal));
    }

    @Given("the meal {string} exists in the system")
    public void theMealExistsInTheSystem(String name) {
        Map<String, Integer> recipe = new LinkedHashMap<>();
        recipe.put("beef", 1);
        recipe.put("cheese", 1);
        context.givenMeal(name, recipe, 20);
    }

    @When("the admin changes its name to {string}")
    public void theAdminChangesItsNameTo(String newName) {
        context.app().catalogService().updateMeal(context.meal(), newName, null, null);
    }

    @When("replaces {string} with {string} in the recipe")
    public void replacesIngredientInTheRecipe(String oldIngredient, String newIngredient) {
        Map<String, Integer> recipe = new LinkedHashMap<>(context.meal().getIngredients());
        Integer quantity = recipe.remove(oldIngredient.toLowerCase());
        recipe.put(newIngredient.toLowerCase(), quantity == null ? 1 : quantity);
        context.app().catalogService().updateMeal(context.meal(), null, recipe, null);
    }

    @When("the admin deletes the meal {string}")
    public void theAdminDeletesTheMeal(String name) {
        Meal meal = context.app().catalogService().findByName(name)
                .orElseThrow(() -> new AssertionError("No meal named " + name));
        context.app().catalogService().removeMeal(meal);
    }

    @When("the admin adds a chef named {string} with email {string}")
    public void theAdminAddsAChef(String name, String email) {
        context.setChef(context.app().staffService().hire(name, email));
    }

    @Given("the chef {string} exists with email {string}")
    public void theChefExistsWithEmail(String name, String email) {
        context.givenChef(name, email);
    }

    @Given("the chef {string} exists in the system")
    public void theChefExistsInTheSystem(String name) {
        context.givenChef(name, name.toLowerCase().replace(" ", ".") + "@kitchen.com");
    }

    @Given("the kitchen has chefs {string} and {string}")
    public void theKitchenHasChefs(String first, String second) {
        context.givenChef(first, first.toLowerCase().replace(" ", ".") + "@kitchen.com");
        context.givenChef(second, second.toLowerCase().replace(" ", ".") + "@kitchen.com");
    }

    @And("{string} has been assigned one order")
    public void chefHasBeenAssignedOneOrder(String chefName) {
        placeOneOrder();
    }

    @And("one order has already been assigned")
    public void oneOrderHasAlreadyBeenAssigned() {
        placeOneOrder();
    }

    @When("another order is placed")
    public void anotherOrderIsPlaced() {
        // Deleting a chef used to leave the round-robin index pointing past the end of the roster,
        // so this second order threw IndexOutOfBoundsException.
        context.attempt(this::placeOneOrder);
    }

    private void placeOneOrder() {
        if (context.customer() == null) {
            context.givenCustomer("Diner", List.of(), List.of());
        }
        if (context.meal() == null) {
            context.givenMeal("House Special", Map.of("rice", 1), 10);
        }
        Order order = context.app().orderService().place(context.customer(), context.meal());
        context.setOrder(order);
    }

    @When("the admin changes the chef name to {string} and email to {string}")
    public void theAdminChangesTheChefNameAndEmail(String newName, String newEmail) {
        context.app().staffService().updateProfile(context.chef(), newName, newEmail);
    }

    @When("the admin removes the chef {string}")
    public void theAdminRemovesTheChef(String name) {
        Chef chef = context.app().staffService().allChefs().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No chef named " + name));
        context.app().staffService().remove(chef);
    }

    @Then("the new meal {string} should appear in the available meals list")
    public void theNewMealShouldAppear(String name) {
        assertTrue(context.app().catalogService().findByName(name).isPresent(),
                "Menu was " + mealNames());
    }

    @And("the meal {string} should need {int} units of {string}")
    public void theMealShouldNeedUnitsOf(String mealName, Integer quantity, String ingredient) {
        Meal meal = context.app().catalogService().findByName(mealName)
                .orElseThrow(() -> new AssertionError("No meal named " + mealName));
        assertEquals(quantity.intValue(), meal.quantityOf(ingredient));
    }

    @Then("the meal {string} should contain {string} and not {string}")
    public void theMealShouldContainAndNot(String mealName, String expected, String removed) {
        Meal meal = context.app().catalogService().findByName(mealName)
                .orElseThrow(() -> new AssertionError("No meal named " + mealName));
        assertTrue(meal.contains(expected), "Recipe was " + meal.getIngredients());
        assertFalse(meal.contains(removed), "Recipe still contains " + removed);
    }

    @Then("the meal {string} should no longer appear in the meal list")
    public void theMealShouldNoLongerAppear(String name) {
        assertTrue(context.app().catalogService().findByName(name).isEmpty(),
                "Menu still contains " + name + ": " + mealNames());
    }

    @Then("the chef {string} should appear in the list of chefs")
    public void theChefShouldAppear(String name) {
        assertTrue(chefNames().contains(name), "Chefs were " + chefNames());
    }

    @Then("the chef {string} should no longer appear in the list of chefs")
    public void theChefShouldNoLongerAppear(String name) {
        assertFalse(chefNames().contains(name), "Chefs were " + chefNames());
    }

    @Then("the chef profile should show name {string} and email {string}")
    public void theChefProfileShouldShow(String name, String email) {
        assertEquals(name, context.chef().getName());
        assertEquals(email.toLowerCase(), context.chef().getEmail());
    }

    @Then("{string} should still have {int} order in their queue")
    public void chefShouldStillHaveOrdersInQueue(String chefName, Integer expected) {
        // Editing a chef used to replace the stored object with a brand new Chef, discarding the
        // work queue the old instance held.
        Chef chef = context.app().staffService().allChefs().stream()
                .filter(c -> c.getName().equals(chefName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No chef named " + chefName));
        assertEquals(expected.intValue(), context.app().kitchenService().queueFor(chef).size());
    }

    @Then("the order should still be assigned to an available chef")
    public void theOrderShouldStillBeAssigned() {
        assertNull(context.thrownException(),
                "Placing an order after a chef was removed threw " + context.thrownException());
        assertTrue(context.order().getAssignedChefId().isPresent(),
                "Order was not assigned to any chef");
        List<Chef> remaining = context.app().staffService().allChefs();
        assertTrue(remaining.stream()
                        .anyMatch(chef -> chef.getId().equals(context.order().getAssignedChefId().get())),
                "Order was assigned to a chef who no longer works here");
    }

    private List<String> mealNames() {
        return context.app().catalogService().allMeals().stream().map(Meal::getName).toList();
    }

    private List<String> chefNames() {
        return context.app().staffService().allChefs().stream().map(Chef::getName).toList();
    }
}
