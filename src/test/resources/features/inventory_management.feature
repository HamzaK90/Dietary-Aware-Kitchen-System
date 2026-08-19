Feature: Inventory and Stock Management
  The kitchen tracks ingredient quantities and warns before it runs out.

  Scenario: System warns about low-stock ingredients
    Given the inventory has "Tofu" with quantity 3
    When a chef checks stock levels
    Then the system should report that "Tofu" is low

  Scenario: System suggests restocking
    Given the inventory has "Flour" with quantity 2
    When a chef checks stock levels
    Then a restock warning should have been raised for "Flour"

  Scenario: Well stocked ingredients are not reported
    Given the inventory has "Rice" with quantity 60
    When a chef checks stock levels
    Then the system should not report that "Rice" is low

  Scenario: Placing an order reduces ingredient stock
    Given the inventory has "Tofu" with quantity 10
    And the inventory has "Rice" with quantity 20
    And the meal "Tofu Bowl" needs 1 "Tofu" and 2 "Rice"
    When the customer "Layla" orders the "Tofu Bowl"
    Then the stock of "Tofu" should be 9
    And the stock of "Rice" should be 18

  Scenario: Stock can never be driven below zero
    Given the inventory has "Tofu" with quantity 1
    When 5 units of "Tofu" are consumed
    Then the operation should be refused for insufficient stock
    And the stock of "Tofu" should be 1
