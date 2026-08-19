package com.cookmgmt.bdd;

import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.exception.DuplicateEmailException;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Steps for {@code update_customer_profile.feature}. */
public class UpdateCustomerProfileSteps {

    private final TestContext context;

    public UpdateCustomerProfileSteps(TestContext context) {
        this.context = context;
    }

    @Given("the customer {string} has an existing profile")
    public void theCustomerHasAnExistingProfile(String name) {
        context.givenCustomer(name, List.of(), List.of());
    }

    @Given("the customer {string} has {string} listed as an allergy")
    public void theCustomerHasAnAllergy(String name, String allergy) {
        context.givenCustomer(name, List.of(), List.of(allergy));
    }

    @Given("the customer {string} has email {string}")
    public void theCustomerHasEmail(String name, String email) {
        Customer customer = context.app().customerService()
                .register(name, email, List.of(), List.of());
        context.setCustomer(customer);
    }

    @Given("the admin opens the customer profile for {string}")
    public void theAdminOpensTheProfileFor(String name) {
        context.givenCustomer(name, List.of(), List.of());
    }

    @When("she adds {string} to her dietary preferences")
    public void sheAddsToHerDietaryPreferences(String preference) {
        context.customer().addDietaryPreference(preference);
        context.app().customerService().repository().save(context.customer());
    }

    @When("he removes {string} and adds {string} instead")
    public void heRemovesAndAddsInstead(String oldAllergy, String newAllergy) {
        Customer customer = context.customer();
        customer.removeAllergy(oldAllergy);
        customer.addAllergy(newAllergy);
        context.app().customerService().repository().save(customer);
    }

    @When("she updates her name to {string} and email to {string}")
    public void sheUpdatesHerNameAndEmail(String newName, String newEmail) {
        context.app().customerService()
                .updateProfile(context.customer(), newName, newEmail, null, null);
    }

    @When("the admin updates his preferences to include {string}")
    public void theAdminUpdatesPreferences(String preference) {
        context.customer().addDietaryPreference(preference);
    }

    @And("updates his allergies to include {string}")
    public void theAdminUpdatesAllergies(String allergy) {
        context.customer().addAllergy(allergy);
        context.app().customerService().repository().save(context.customer());
    }

    @When("another customer tries to register with the same email")
    public void anotherCustomerTriesToRegisterWithTheSameEmail() {
        String takenEmail = context.customer().getEmail();
        context.attempt(() -> context.app().customerService()
                .register("Impostor", takenEmail, List.of(), List.of()));
    }

    @Then("her profile should include {string} in the preferences list")
    public void herProfileShouldIncludeInPreferences(String preference) {
        assertTrue(context.customer().prefers(preference),
                "Preferences were " + context.customer().getDietaryPreferences());
    }

    @Then("his allergy list should only include {string}")
    public void hisAllergyListShouldOnlyInclude(String allergy) {
        assertEquals(1, context.customer().getAllergies().size(),
                "Allergies were " + context.customer().getAllergies());
        assertTrue(context.customer().isAllergicTo(allergy));
    }

    @Then("her profile should reflect the name {string} and email {string}")
    public void herProfileShouldReflectNameAndEmail(String name, String email) {
        assertEquals(name, context.customer().getName());
        assertEquals(email.toLowerCase(), context.customer().getEmail());
    }

    @Then("the profile for {string} should include {string} in preferences and {string} in allergies")
    public void theProfileShouldIncludePreferenceAndAllergy(String name, String preference, String allergy) {
        Customer customer = context.customer();
        assertEquals(name, customer.getName());
        assertTrue(customer.prefers(preference), "Missing preference " + preference);
        assertTrue(customer.isAllergicTo(allergy), "Missing allergy " + allergy);
    }

    @Then("the registration should be rejected as a duplicate")
    public void theRegistrationShouldBeRejected() {
        Exception thrown = context.thrownException();
        assertNotNull(thrown, "Registering a duplicate email was allowed");
        assertInstanceOf(DuplicateEmailException.class, thrown);
        assertEquals(1, context.app().customerService().allCustomers().size(),
                "The duplicate account should not have been stored");
    }
}
