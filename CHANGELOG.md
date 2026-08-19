# Changelog

This repository is the enhanced continuation of an earlier iteration of the same coursework project.
Version 1.0 below refers to that earlier iteration; version 2.0 is the code in this repository.

## 2.0.0 — SOLID restructuring, test overhaul, JavaFX interface

A rewrite of the internals, carrying over the domain model and the Gherkin feature files. Behaviour
a user sees is broadly the same, but almost every class moved and a number of bugs were fixed.

### Fixed — critical

- **Beef and shrimp bypassed every dietary rule.** The ingredient tag table registered `"Beef"` and
  `"Shrimp"` capitalised while every lookup lower-cased its key.
- **Placing an order never reduced stock.** The console built orders directly and never touched
  inventory; the one method that did was never called.
- **`IndexOutOfBoundsException`** in chef assignment whenever a chef was removed mid-rotation.
- **Rejected orders lost their ingredients and themselves** — nothing released reserved stock, and
  `poll()` had already removed the order from the only place holding it.
- **Stock could go negative**, silently.

### Fixed — correctness

- Substitution suggestions were unsuitable ("bun" for "milk"), ignored dietary preferences entirely,
  and were non-deterministic.
- Money held as `double`; invoice totals could drift from their own line items.
- Order numbers came from a never-reset `static` counter, leaking between test scenarios.
- `substitutionsApproved` was a public mutable field; order status was writable from anywhere.
- Pending orders displayed `Total: $0.00`.
- Renaming a chef discarded their assigned orders.
- Entity lookups used `List.indexOf` without any `equals` override.
- Comma-separated input was parsed without trimming; empty input produced `[""]`.
- No duplicate-email check on registration.
- Editing a meal could silently erase its recipe.
- Beef was tagged `non-halal`, blocking every halal customer from every beef dish.

### Added

- **JavaFX interface** (`./mvnw javafx:run`) — Customer, Chef and Admin tabs with live conflict
  detection, substitution review, stock tables, an orders report and an orders-per-meal chart.
- **172 unit tests** alongside the BDD suite, including a named regression test for each defect
  above.
- **Unresolved-conflict reporting.** A substitution set does not always fix everything — an
  ingredient may have no suitable replacement in stock. `OrderPreview.remainingConflicts` reports
  what survives, and both interfaces say so instead of implying the changes resolved the meal. An
  unresolvable *allergen* now blocks the order; an unresolved dietary preference warns and lets the
  customer decide. (`vegan cheese` was added to the catalogue and sample pantry so the common case
  resolves cleanly.)
- **JaCoCo coverage gate** at 80%; actual coverage ~85%.
- **Maven wrapper** — the project previously built only inside the IDE.
- `KosherRule` and `DairyFreeRule`. The `non-kosher` tag existed in the original data but nothing
  read it.
- Order cancellation, stock release, and `IN_PROGRESS` / `APPROVED` / `NEEDS_APPROVAL` states that
  were declared but never assigned.
- `docs/ARCHITECTURE.md`, `docs/SOLID.md`, `docs/TESTING.md`, and a real README.

### Changed

- Package `Cook.update` → `com.cookmgmt.*`, split into `domain`, `domain.rule`, `domain.policy`,
  `inventory`, `repository`, `service`, `notify`, `support`, `ui.console`, `ui.fx`, `app`.
- `Main` (872 lines: console UI + dietary rules + sample data) → `ConsoleApp` plus three menu classes
  over the service layer.
- `Admin` (god object owning four collections and chef assignment) → four repositories and three
  services.
- Conflict detection: an `else if` chain returning `boolean` and printing to `System.out` →
  `DietaryRule` implementations returning `List<Conflict>`. Every applicable rule now runs; the old
  chain stopped at the first match, so a customer with two dietary needs saw only one problem.
- Chef assignment: inline index arithmetic → `ChefAssignmentStrategy`, switchable at runtime.
- `Inventory` → `ReadableInventory` / `MutableInventory`, so pricing cannot mutate stock.
- Cucumber moved from the JUnit 4 runner to `cucumber-junit-platform-engine`; assertions and runner
  are now the same JUnit generation.
- Step classes share one `TestContext` via PicoContainer, which was already a dependency but unused.
- Feature files moved to `src/test/resources/features` (they were loaded from a working-directory
  relative path), and one was renamed to drop a leading space in its filename.
- Java target 17; JaCoCo 0.8.8 → 0.8.12, which can read modern bytecode.

### Removed

- `Chef.setTestAutoApprove` — a test-only flag that existed in production code so the suite could
  bypass a `Scanner` read inside a domain class. The `Scanner` is gone, so the flag is unnecessary.
- `jfreechart` and `joda-time` — declared but never imported. The GUI chart uses
  `javafx.scene.chart`, which ships with the toolkit.
- Dead code: `Meal.calculateBasePrice`, `Admin.editMeal`, `Admin.updateCustomer`,
  `Customer.placeOrder`, `Customer.resolveConflicts`, `Chef`'s unused `Inventory` field.
- `System.exit(0)` from inside a menu switch.

---

## 1.0.0 — Original coursework submission

Console application with Cucumber feature files covering customer profiles, admin management,
inventory, billing, pricing, substitutions, notifications and order customisation.
