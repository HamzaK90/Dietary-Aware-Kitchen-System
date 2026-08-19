Feature: Meal pricing and invoice generation
  Prices come from ingredient quantities and unit prices, and follow any substitution.

  Scenario: System calculates base price for a meal
    Given the meal "Tofu Bowl" contains 1 "Tofu" at 3.00 and 2 "Rice" at 1.50
    When the system calculates the base price
    Then the total should equal the sum of ingredient quantities times their prices
    And the base price should be 6.00

  Scenario: System calculates final price after substitutions
    Given the meal "Beef Burger" contains 1 "Beef" at 6.00
    And "Tofu" is available at 2.00
    When the customer replaces "Beef" with "Tofu" and the price is recalculated
    Then it should use the price of "Tofu" instead of "Beef" in the total
    And the recalculated total should be 2.00

  Scenario: Customer receives invoice after order is completed
    Given the customer "Layla" placed an order for "Tofu Bowl"
    And the system completes the order
    When the customer receives the invoice
    Then the invoice should include the meal name, order ID, and total price
