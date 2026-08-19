Feature: Admin management of meals and chefs
  The admin maintains the menu and the kitchen roster.

  Scenario: Admin adds a new meal
    Given the admin is on the meal management panel
    When the admin creates a meal named "Falafel Plate"
    And adds ingredients "Chickpeas" with quantity 2, "Lettuce" with quantity 1, "Tomato" with quantity 1
    And sets the cooking time to 20 minutes
    Then the new meal "Falafel Plate" should appear in the available meals list
    And the meal "Falafel Plate" should need 2 units of "chickpeas"

  Scenario: Admin edits an existing meal
    Given the meal "Beef Burger" exists in the system
    When the admin changes its name to "Vegan Burger"
    And replaces "Beef" with "Tofu" in the recipe
    Then the meal "Vegan Burger" should contain "Tofu" and not "Beef"
    And the meal "Beef Burger" should no longer appear in the meal list

  Scenario: Admin deletes a meal
    Given the meal "Shrimp Pasta" exists in the system
    When the admin deletes the meal "Shrimp Pasta"
    Then the meal "Shrimp Pasta" should no longer appear in the meal list

  Scenario: Admin adds a new chef
    Given the admin is on the chef management panel
    When the admin adds a chef named "Chef Yasmine" with email "yasmine@kitchen.com"
    Then the chef "Chef Yasmine" should appear in the list of chefs

  Scenario: Admin edits a chef profile
    Given the chef "Chef Nora" exists with email "nora@kitchen.com"
    When the admin changes the chef name to "Chef Noura" and email to "noura@kitchen.com"
    Then the chef profile should show name "Chef Noura" and email "noura@kitchen.com"

  Scenario: Renaming a chef keeps their assigned orders
    Given the chef "Chef Nora" exists with email "nora@kitchen.com"
    And "Chef Nora" has been assigned one order
    When the admin changes the chef name to "Chef Noura" and email to "noura@kitchen.com"
    Then "Chef Noura" should still have 1 order in their queue

  Scenario: Admin deletes a chef
    Given the chef "Chef Zaid" exists in the system
    When the admin removes the chef "Chef Zaid"
    Then the chef "Chef Zaid" should no longer appear in the list of chefs

  Scenario: Removing a chef mid-rotation does not break order assignment
    Given the kitchen has chefs "Chef Nora" and "Chef Zaid"
    And one order has already been assigned
    When the admin removes the chef "Chef Zaid"
    And another order is placed
    Then the order should still be assigned to an available chef
