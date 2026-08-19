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
