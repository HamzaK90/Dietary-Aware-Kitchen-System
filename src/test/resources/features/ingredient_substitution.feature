Feature: Ingredient substitution based on dietary restrictions and availability
  When a meal does not suit a customer, the system explains why and offers a real replacement.

  Scenario: Customer sees substitution suggestion due to dietary restriction
    Given the customer has a dietary preference "Vegan"
    And the meal "Omelette" includes "Egg" as an ingredient
    When the customer attempts to order the "Omelette"
    Then the system should warn that "Egg" is not vegan
    And it should suggest a vegan replacement for "Egg"

  Scenario: Customer sees substitution suggestion due to an allergy
    Given the customer has an allergy to "Milk"
    And the meal "Mac and Cheese" includes "Milk" as an ingredient
    When the customer attempts to order the "Mac and Cheese"
    Then the system should warn about the milk allergy
    And it should suggest "almond milk" as a replacement for "Milk"

  Scenario: Substitution suggestions do the same culinary job as the original
    Given the customer has an allergy to "Milk"
    And the meal "Mac and Cheese" includes "Milk" as an ingredient
    When the customer attempts to order the "Mac and Cheese"
    Then every suggested replacement for "Milk" should also be a milk

  Scenario: Chef reviews an order with substitutions
    Given a customer submitted a modified meal replacing "Beef" with "Tofu"
    Then the chef should see a substitution alert
    When the chef approves the substitutions
    Then the order should be approved and ready to cook

  Scenario: Chef rejects an order and the ingredients go back into stock
    Given a customer submitted a modified meal replacing "Beef" with "Tofu"
    When the chef rejects the substitutions
    Then the order should be rejected
    And the reserved "Tofu" should have been returned to stock

  Scenario: System checks inventory and suggests replacement
    Given the ingredient "Tofu" is out of stock
    When a customer needs a replacement for "Tofu"
    Then the system should suggest an alternative that is in stock
    And "Tofu" should not be among the suggestions

  Scenario: Substitution is applied and order is placed
    Given a customer replaces "Shrimp" with "Tofu" in a meal
    When the chef approves the substitutions
    Then the order should proceed with "Tofu" instead of "Shrimp"
    And the order should be priced using "Tofu"

  Scenario: Beef is correctly identified as unsuitable for a vegetarian
    Given the customer has a dietary preference "Vegetarian"
    And the meal "Beef Burger" includes "Beef" as an ingredient
    When the customer attempts to order the "Beef Burger"
    Then the system should report a dietary conflict for "Beef"

  Scenario: Shrimp is correctly identified as unsuitable for a vegetarian
    Given the customer has a dietary preference "Vegetarian"
    And the meal "Shrimp Pasta" includes "Shrimp" as an ingredient
    When the customer attempts to order the "Shrimp Pasta"
    Then the system should report a dietary conflict for "Shrimp"
