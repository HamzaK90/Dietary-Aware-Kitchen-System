# Special Cook Management System

An in-memory kitchen management system: customers with dietary profiles order meals, the system
detects allergy and dietary conflicts and proposes ingredient substitutions, chefs review and cook
them, and completed orders produce itemised invoices.

Built as a software-engineering coursework project, so the code is written to be *read*: SOLID
principles, TDD/BDD, and clean separation of layers. **There is no database** — everything lives in
memory and resets when the application stops.

> ### About this repository
>
> This is the **enhanced version** of an earlier iteration of the same coursework project
> (March–May 2025). The domain model and the Gherkin feature files carry over from it; the
> structure, the test suite and both user interfaces are rewritten.
>
> A number of bugs found in that iteration are fixed here, and each has a test covering it so it
> cannot come back. Comments through the source occasionally refer to "the previous version" or
> "the old code" — that is the earlier iteration.

```
Java 17+  ·  Maven (wrapper included)  ·  JUnit 5  ·  Cucumber 7  ·  JavaFX 21
217 tests  ·  45 BDD scenarios  ·  ~85% line coverage
```

---

## Quick start

You do not need Maven installed — the wrapper fetches it.

```bash
./mvnw clean verify        # compile, run all 215 tests, produce the coverage report
./mvnw javafx:run          # launch the JavaFX desktop interface
./mvnw exec:java           # launch the console interface
```

On Windows use `mvnw.cmd` in place of `./mvnw`.

Both interfaces start with sample data loaded. Useful logins:

| Who | Email | Profile |
|---|---|---|
| Layla | `layla@example.com` | vegan, allergic to milk |
| Ahmad | `ahmad@example.com` | halal, allergic to shrimp |
| Salma | `salma@example.com` | gluten-free + vegetarian, allergic to nuts |
| Chef Nora | `nora@kitchen.com` | chef |
| Chef Zaid | `zaid@kitchen.com` | chef |

**Try this first:** open the Customer tab as *Layla* and select **Beef Burger**. The suitability
panel explains that beef and cheese are not vegan, proposes replacements that are actually in stock
and actually vegan, and the order then goes to a chef for approval.

---

## What it does

- **Dietary profiles** — customers record preferences (vegan, vegetarian, halal, kosher,
  gluten-free, dairy-free) and allergies.
- **Conflict detection** — every meal is checked against every rule that applies to the customer,
  and each clash is reported individually with a reason.
- **Substitution** — replacements are drawn from ingredients that fill the same culinary role, are
  acceptable for that customer's diet, and are in stock.
- **Stock control** — placing an order reserves its ingredients atomically; rejecting or cancelling
  returns them; low stock raises a notification.
- **Kitchen workflow** — orders are assigned to chefs by a swappable policy, reviewed when they
  carry substitutions, cooked, and completed through an enforced state machine.
- **Billing** — itemised invoices in `BigDecimal`, with the total frozen at completion.

---

## Architecture

```
┌──────────────────────┐        ┌──────────────────────┐
│  ui.console          │        │  ui.fx  (JavaFX)     │   ← no business rules
│  ConsoleApp, menus   │        │  Customer/Chef/Admin │
└──────────┬───────────┘        └───────────┬──────────┘
           └───────────────┬────────────────┘
                           ▼
                  ┌─────────────────┐
                  │  app.AppContext │   composition root — wires everything
                  └────────┬────────┘
                           ▼
   ┌──────────────────────────────────────────────────────┐
   │  service                                             │
   │  OrderService · KitchenService · PricingService       │
   │  InventoryService · SubstitutionService               │
   │  CatalogService · CustomerService · StaffService      │
   └───────┬──────────────────────────────┬───────────────┘
           ▼                              ▼
   ┌───────────────┐            ┌──────────────────────────┐
   │  repository   │            │  domain                  │
   │  (interfaces) │            │  Meal · Order · Customer  │
   │  in-memory    │            │  Money · rule.* · policy.*│
   └───────────────┘            └──────────────────────────┘
```

Dependencies point inward. The domain knows nothing about storage or the user interface, and the
two front ends share one service layer — which is why a rule change takes effect in both without
being written twice.

Full detail in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## SOLID, with the file to look at

| Principle | Where | What it replaced |
|---|---|---|
| **S**ingle responsibility | [`ConsoleApp`](src/main/java/com/cookmgmt/ui/console/ConsoleApp.java) vs [`OrderService`](src/main/java/com/cookmgmt/service/OrderService.java) vs [`Order`](src/main/java/com/cookmgmt/domain/Order.java) | one 870-line `Main` holding UI, dietary rules and sample data |
| **O**pen/closed | [`DietaryRule`](src/main/java/com/cookmgmt/domain/rule/DietaryRule.java) + 6 implementations | an `else if` chain — adding kosher meant editing the UI class |
| **L**iskov substitution | [`ChefAssignmentStrategy`](src/main/java/com/cookmgmt/domain/policy/ChefAssignmentStrategy.java) | index arithmetic inlined in a god object |
| **I**nterface segregation | [`ReadableInventory`](src/main/java/com/cookmgmt/inventory/ReadableInventory.java) / [`MutableInventory`](src/main/java/com/cookmgmt/inventory/MutableInventory.java) | one class handed to everyone, including a `Chef` that never used it |
| **D**ependency inversion | [`Repository`](src/main/java/com/cookmgmt/repository/Repository.java), [`Notifier`](src/main/java/com/cookmgmt/notify/Notifier.java), [`ConsoleIO`](src/main/java/com/cookmgmt/ui/console/ConsoleIO.java) | `new ArrayList<>()` and `new Scanner(System.in)` inside domain classes |

Patterns used: **Repository**, **Strategy** (dietary rules, chef assignment), **Builder** (`Meal`),
**Value Object** (`Money`), **Observer** (`Notifier`).

Worked examples with before/after code in [docs/SOLID.md](docs/SOLID.md).

---

## Testing

```bash
./mvnw test                                  # all tests
./mvnw test -Dtest=DietaryRuleEngineTest     # one unit test class
./mvnw test -Dtest=RunCucumberTest           # the BDD suite only
./mvnw verify                                # + coverage report and 80% gate
```

Reports land in `target/site/jacoco/index.html` (coverage) and `target/cucumber/report.html` (BDD).

| | Count |
|---|---|
| Cucumber scenarios | 45 (174 steps) |
| Unit tests | 172 |
| **Total** | **217** |
| Line coverage | ~85% (build fails below 80%) |

More in [docs/TESTING.md](docs/TESTING.md).

---

## Project layout

```
src/main/java/com/cookmgmt/
├── app/           AppContext (composition root), SampleData
├── domain/        Meal, Order, Customer, Chef, Money, Invoice, Conflict
│   ├── rule/      DietaryRule + Vegan/Vegetarian/Halal/Kosher/GlutenFree/DairyFree/Allergy
│   ├── policy/    ChefAssignmentStrategy + RoundRobin/LeastLoaded
│   └── exception/ InsufficientStock, DuplicateEmail, OrderState
├── inventory/     ReadableInventory, MutableInventory, InMemoryInventory
├── repository/    Repository, InMemoryRepository, Meal/Customer/Chef/Order repositories
├── service/       the eight application services
├── notify/        Notifier, ConsoleNotifier, InMemoryNotifier
├── support/       Text (normalisation, CSV parsing)
└── ui/
    ├── console/   ConsoleApp, ConsoleIO, Customer/Chef/Admin menus
    └── fx/        FxApplication, AppLauncher, Customer/Chef/Admin views

src/test/
├── java/com/cookmgmt/bdd/     RunCucumberTest, TestContext, 9 step-definition classes
├── java/com/cookmgmt/...      unit tests mirroring the main packages
└── resources/features/        9 Gherkin feature files
```

---

## Notes for the reader

- **Everything is in memory by design.** Restarting loses all data. `AppContext` is the only place
  storage is chosen, so a persistent implementation would slot in behind the same interfaces.
- **The two interfaces are deliberately duplicate-free.** If you find a business rule inside
  `ui.console` or `ui.fx`, that is a bug — it belongs in `service` or `domain`.
- **Money is `BigDecimal`, never `double`.** See `MoneyTest.doesNotAccumulateFloatingPointError`.
- **Ingredient names are normalised everywhere** through `Text.normalize`. A casing mismatch between
  a lookup table and its lookups is what silently disabled every beef and shrimp dietary rule in the
  original code.

## Documentation

| Document | Contents |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | layers, dependency rules, patterns, key flows |
| [docs/SOLID.md](docs/SOLID.md) | each principle with the before/after code |
| [docs/TESTING.md](docs/TESTING.md) | how to run tests, feature→step map, TDD workflow |
| [CHANGELOG.md](CHANGELOG.md) | what changed in this overhaul |
