Feature: Updating customer profile preferences and allergies
  Profiles change over time, and every change must be reflected in later conflict checks.

  Scenario: Customer updates dietary preferences
    Given the customer "Layla" has an existing profile
    When she adds "Gluten-Free" to her dietary preferences
    Then her profile should include "Gluten-Free" in the preferences list

  Scenario: Customer updates allergy information
    Given the customer "Ahmad" has "Shrimp" listed as an allergy
    When he removes "Shrimp" and adds "Eggs" instead
    Then his allergy list should only include "Eggs"

  Scenario: Customer changes email and name
    Given the customer "Salma" has email "salma@oldmail.com"
    When she updates her name to "Salma A." and email to "salma.new@mail.com"
    Then her profile should reflect the name "Salma A." and email "salma.new@mail.com"
    And she should be able to log in with "salma.new@mail.com"

  Scenario: Admin updates a customer profile manually
    Given the admin opens the customer profile for "Ahmad"
    When the admin updates his preferences to include "Halal"
    And updates his allergies to include "Gelatin"
    Then the profile for "Ahmad" should include "Halal" in preferences and "Gelatin" in allergies

  Scenario: A second account cannot reuse an existing email address
    Given the customer "Layla" has an existing profile
    When another customer tries to register with the same email
    Then the registration should be rejected as a duplicate
