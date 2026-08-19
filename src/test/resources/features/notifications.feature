Feature: Notifications and Alerts
  Chefs are told about work waiting for them, and customers about problems with their order.

  Scenario: Chef receives notification of scheduled cooking
    Given a chef has pending orders
    When the chef logs into the system
    Then the system should list tasks with meal names and order IDs

  Scenario: Chef is notified when a new order is assigned
    Given a chef has pending orders
    Then the chef should have been notified about the new order

  Scenario: Customer is warned of allergen in selected meal
    Given a customer with a "Nuts" allergy selects a meal containing "Nuts"
    Then the system should warn about the conflict
    And the warning should be an allergy conflict

  Scenario: Customer is told when their order is completed
    Given a chef has pending orders
    When the chef completes the order
    Then the customer should have been notified that the order is ready
