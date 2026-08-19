Feature: Order and Menu Customization
  A customer can be warned about a clash, modify the meal, and submit it for a chef to confirm.

  Scenario: Customer orders a meal with a conflicting ingredient
    Given a meal "Shrimp Pasta" contains "Shrimp"
    And the customer "Ahmad" is allergic to "Shrimp"
    When "Ahmad" tries to order "Shrimp Pasta"
    Then the system should warn about the allergy
    And the system should suggest modifying the meal

  Scenario: An allergic customer cannot order the meal unmodified
    Given a meal "Shrimp Pasta" contains "Shrimp"
    And the customer "Ahmad" is allergic to "Shrimp"
    When "Ahmad" tries to place the order for "Shrimp Pasta" without changes
    Then the order should be refused because of the allergy

  Scenario: Customer modifies a meal and submits order
    Given a meal "Beef Burger" contains "Beef"
    And the customer "Salma" is vegetarian
    When "Salma" modifies the meal and replaces "Beef" with "Tofu"
    Then the modified meal should no longer conflict
    And the order should be placed with the new ingredients

  Scenario: Chef confirms a modified meal before preparation
    Given a meal was modified by the customer
    When the chef reviews the order
    And approves the substitutions
    Then the system should mark the order as ready to cook

  Scenario: An unmodified order does not need chef approval
    Given the customer "Layla" orders a meal with no substitutions
    Then the order should not require chef approval
