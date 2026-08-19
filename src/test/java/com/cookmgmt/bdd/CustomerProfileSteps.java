package com.cookmgmt.bdd;

import com.cookmgmt.domain.Customer;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Steps for {@code customer_profile_management.feature}. */
public class CustomerProfileSteps {

    private final TestContext context;

    /** Injected by PicoContainer so every step class in a scenario shares one world. */
    public CustomerProfileSteps(TestContext context) {
        this.context = context;
    }

    @Given("a customer named {string} creates a profile")
    public void aCustomerCreatesAProfile(String name) {
        context.givenCustomer(name, List.of(), List.of());
    }

    @Given("a customer named {string} has preferences {string} and allergies {string}")
    public void aCustomerHasPreferencesAndAllergies(String name, String preference, String allergy) {
        context.givenCustomer(name, List.of(preference), List.of(allergy));
    }

    @When("she specifies {string} as a dietary preference")
    public void sheSpecifiesADietaryPreference(String preference) {
        context.customer().addDietaryPreference(preference);
    }

    @When("she adds {string} to her allergies")
    public void sheAddsToHerAllergies(String allergy) {
        context.customer().addAllergy(allergy);
    }

    @When("she enters {string} as her comma separated preferences")
    public void sheEntersCommaSeparatedPreferences(String csv) {
        context.customer().setDietaryPreferencesFromCsv(csv);
    }

    @Then("her profile should store {string} in preferences")
    public void herProfileShouldStoreInPreferences(String preference) {
        assertTrue(context.customer().prefers(preference),
                "Expected preference \"" + preference + "\" in "
                        + context.customer().getDietaryPreferences());
    }

    @Then("her profile should store {string} in allergies")
    public void herProfileShouldStoreInAllergies(String allergy) {
        assertTrue(context.customer().isAllergicTo(allergy),
                "Expected allergy \"" + allergy + "\" in " + context.customer().getAllergies());
    }

    @Then("her profile should have exactly {int} preferences")
    public void herProfileShouldHaveExactlyPreferences(int expected) {
        // Guards the CSV parsing fix: a bare split(",") on " VEGAN , Gluten-Free " used to
        // produce untrimmed values, and on empty input a list holding one empty string.
        assertEquals(expected, context.customer().getDietaryPreferences().size(),
                "Preferences were " + context.customer().getDietaryPreferences());
    }

    @When("a chef looks up the profile for {string}")
    public void aChefLooksUpTheProfileFor(String email) {
        Customer found = context.app().customerService().login(email)
                .orElseThrow(() -> new AssertionError("No customer registered for " + email));
        context.setCustomer(found);
    }

    @Then("the chef should see preference {string} and allergy {string}")
    public void theChefShouldSeePreferenceAndAllergy(String preference, String allergy) {
        assertTrue(context.customer().prefers(preference),
                "Chef could not see preference \"" + preference + "\"");
        assertTrue(context.customer().isAllergicTo(allergy),
                "Chef could not see allergy \"" + allergy + "\"");
    }

    @And("she should be able to log in with {string}")
    public void sheShouldBeAbleToLogInWith(String email) {
        assertTrue(context.app().customerService().login(email).isPresent(),
                "Login failed for " + email);
    }
}
