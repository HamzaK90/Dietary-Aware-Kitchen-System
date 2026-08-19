Feature: Billing and Invoicing
  A completed order produces an itemised invoice whose total matches its line items.

  Scenario: Customer receives an invoice after meal completion
    Given a customer places an order for "Tofu Bowl"
    When the order has been completed
    Then the customer should receive an invoice
    And the invoice should include the total cost

  Scenario: The invoice total equals the sum of its line items
    Given a customer places an order for "Tofu Bowl"
    When the order has been completed
    Then the invoice line items should add up to the invoice total

  Scenario: An issued invoice is not changed by a later price rise
    Given a customer places an order for "Tofu Bowl"
    And the order has been completed
    When the price of "Rice" is doubled
    Then the invoice total should stay the same

  Scenario: A cancelled order returns its ingredients and is never billed
    Given a customer places an order for "Tofu Bowl"
    When the customer cancels the order
    Then the ingredients should be back in stock
    And the order should no longer appear in the chef's queue
    And the order should not appear on the customer's statement
    And the statement total should be "$0.00"

  Scenario: An order already being cooked can no longer be cancelled
    Given a customer places an order for "Tofu Bowl"
    And a chef has started cooking the order
    When the customer tries to cancel the order
    Then the cancellation should be refused
    And the ingredients should stay out of stock

  Scenario: A statement bills completed orders and excludes cancelled ones
    Given a customer places an order for "Tofu Bowl"
    And the order has been completed
    And the customer places a second order for "Tofu Bowl"
    And the customer cancels the second order
    Then the statement should bill 1 order
    And the statement should report 1 excluded order
    And the statement total should equal the completed order's invoice
