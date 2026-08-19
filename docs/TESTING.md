# Testing

```
217 tests   ·   45 Cucumber scenarios (174 steps)   ·   172 unit tests   ·   ~85% line coverage
```

## Running them

```bash
./mvnw test                                  # everything
./mvnw test -Dtest=RunCucumberTest           # the BDD suite only
./mvnw test -Dtest=DietaryRuleEngineTest     # one unit test class
./mvnw test -Dtest='*ServiceTest'            # all service tests
./mvnw verify                                # + JaCoCo report and the 80% gate
```

Reports:

| Report | Path |
|---|---|
| Coverage | `target/site/jacoco/index.html` |
| BDD (HTML) | `target/cucumber/report.html` |
| BDD (JSON) | `target/cucumber/report.json` |
| Surefire | `target/surefire-reports/` |

## How the suite is wired

**Runner.** [`RunCucumberTest`](../src/test/java/com/cookmgmt/bdd/RunCucumberTest.java) is a JUnit 5
`@Suite` that includes the Cucumber engine and selects the `features` classpath resource. Glue is
configured in `src/test/resources/junit-platform.properties`.

It is named `*Test` so that it matches Surefire's default include pattern, and it uses the JUnit 5
platform engine rather than the older JUnit 4 Cucumber runner, so the runner and the assertions in
the step definitions belong to the same JUnit generation.

**Shared state.** [`TestContext`](../src/test/java/com/cookmgmt/bdd/TestContext.java) holds one
`AppContext` and the scenario's working objects. Cucumber's PicoContainer creates one per scenario
and injects it into every step class that declares it as a constructor parameter:

```java
public class BillingSteps {
    private final TestContext context;
    public BillingSteps(TestContext context) { this.context = context; }
}
```

Without a shared context each step class would build its own world, and a `Given` in one class would
set up state that a `When` in another could not see — so any scenario spanning two classes would be
impossible to write.

**Scenario isolation.** A fresh `AppContext` per scenario means fresh repositories *and* an order
number sequence that starts at 1, so scenarios cannot influence one another through leftover state.

## Feature files → step definitions

All features live in `src/test/resources/features/`.

| Feature | Scenarios | Step definitions |
|---|---|---|
| `customer_profile_management.feature` | 3 | `CustomerProfileSteps` |
| `update_customer_profile.feature` | 5 | `UpdateCustomerProfileSteps` |
| `admin_management.feature` | 8 | `AdminManagementSteps` |
| `inventory_management.feature` | 5 | `InventoryManagementSteps` |
| `billing.feature` | 3 | `BillingSteps` |
| `meal_pricing_and_invoice.feature` | 3 | `MealPricingAndInvoiceSteps` |
| `ingredient_substitution.feature` | 9 | `IngredientSubstitutionSteps` |
| `notifications.feature` | 4 | `NotificationsSteps` |
| `order_customization.feature` | 5 | `OrderCustomizationSteps` |

## Unit tests

| Class | Covers |
|---|---|
| `MoneyTest` | arithmetic, rounding, no floating-point drift |
| `TextTest` | name normalisation, CSV trimming and blank handling |
| `MealTest` | recipe validation, substitution, the builder |
| `OrderTest` | the state machine, encapsulation, price freezing |
| `DietaryRuleEngineTest` | every diet × ingredient combination, plus the capitalisation regression |
| `ChefAssignmentStrategyTest` | the shared strategy contract and the roster-shrink crash |
| `InMemoryInventoryTest` | stock invariants, negative-stock refusal, deterministic low-stock order |
| `InventoryServiceTest` | all-or-nothing reservation, restock warnings |
| `PricingServiceTest` | pricing, substituted pricing, invoice line-item consistency |
| `SubstitutionServiceTest` | role filtering, diet filtering, determinism |
| `OrderServiceTest` | placing, previewing, cancelling, stock effects, history |
| `KitchenServiceTest` | queue behaviour, approve/reject, stock release, invoicing |
| `PersonRepositoryTest` | unique email, identity lookups, order numbering |

## Tests covering specific bugs

Each of these pins down a bug found in the earlier iteration, so it cannot come back unnoticed.

| Bug | Test |
|---|---|
| Beef and shrimp bypassed every dietary rule | `DietaryRuleEngineTest.Capitalisation` |
| `IndexOutOfBoundsException` when a chef was removed | `ChefAssignmentStrategyTest.removingAChefMidRotationDoesNotThrow` |
| Ordering never reduced stock | `OrderServiceTest.placingAnOrderReducesStock` |
| Rejected orders lost their ingredients | `KitchenServiceTest.rejectingReturnsIngredientsToStock` |
| Reading a chef's queue consumed it | `KitchenServiceTest.readingTheQueueDoesNotConsumeIt` |
| Stock could go negative | `InMemoryInventoryTest.refusesToOverConsume` |
| Substitutions were unsuitable and random | `SubstitutionServiceTest` (three tests) |
| Invoice totals drifted from their line items | `MoneyTest.doesNotAccumulateFloatingPointError` |
| Order numbers leaked between scenarios | `PersonRepositoryTest.eachRepositoryStartsAtOne` |
| Illegal status transitions were allowed | `OrderTest.refusesIllegalTransitions` |
| A pending order showed `$0.00` | `OrderTest.pendingOrderHasNoPrice` |
| Duplicate email addresses accepted | `PersonRepositoryTest.rejectsDuplicateEmail` |
| Renaming a chef discarded their orders | *"Renaming a chef keeps their assigned orders"* scenario |

## Conventions for assertions

Two rules keep the suite meaningful:

1. **Assert on what the system produced**, never on a value the test itself set or on a Gherkin
   parameter — otherwise the test is checking its own setup.
2. **Watch for disjunctions.** `assertTrue(a || !b)` is often a tautology in disguise. If you cannot
   name an input that would make an assertion fail, it is not testing anything.

`cucumber.execution.strict=true` in `junit-platform.properties` supports this from the other
direction: a step with no matching definition fails the build rather than being reported as skipped.

## The coverage gate

JaCoCo enforces 80% line coverage at `verify`; the actual figure is around 85%. `com.cookmgmt.ui.**`
and `SampleData` are excluded — the interfaces are exercised by hand, and unit-testing JavaFX
controllers would add machinery without adding confidence. Everything they call *is* covered.

| Package | Line coverage |
|---|---|
| `support` | 100% |
| `domain.exception` | 94% |
| `domain` | 92% |
| `inventory` | 92% |
| `service` | 90% |
| `repository` | 90% |
| `domain.rule` | 87% |
| `domain.policy` | 86% |

## TDD workflow used here

1. Write the failing test first — a Gherkin scenario for behaviour a user can describe, a JUnit test
   for a rule or an edge case.
2. Run it and **watch it fail for the right reason**. An assertion that has never failed has never
   been shown to test anything.
3. Write the smallest change that passes.
4. Refactor with the suite green.

The same order applies to a bug: reproduce it with a failing test first, then fix it.
