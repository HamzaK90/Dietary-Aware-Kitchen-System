Feature: Customer Profile Management
  A customer records the dietary preferences and allergies that the kitchen must respect,
  and staff can read that profile back.

  Scenario: Customer enters dietary preferences and allergies
    Given a customer named "Layla" creates a profile
    When she specifies "Vegan" as a dietary preference
    And she adds "Milk" to her allergies
    Then her profile should store "Vegan" in preferences
    And her profile should store "Milk" in allergies

  Scenario: Preferences are matched regardless of spacing and capitalisation
    Given a customer named "Layla" creates a profile
    When she enters " VEGAN , Gluten-Free " as her comma separated preferences
    Then her profile should store "Vegan" in preferences
    And her profile should store "gluten-free" in preferences
    And her profile should have exactly 2 preferences

  Scenario: Chef views a customer dietary profile
    Given a customer named "Layla" has preferences "Vegan" and allergies "Milk"
    When a chef looks up the profile for "layla@example.com"
    Then the chef should see preference "Vegan" and allergy "Milk"
