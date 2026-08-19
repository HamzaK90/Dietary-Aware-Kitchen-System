# SOLID principles, with the code

Each principle below names the file that demonstrates it and the code it replaced. The "before"
snippets are the actual previous implementation, not invented examples.

---

## S — Single Responsibility

> A class should have one reason to change.

### Before

`Main.java` was 872 lines holding four unrelated jobs:

- the console screens for all three roles (~600 lines);
- the dietary rule table and the conflict-checking algorithm (`ingredientTags`,
  `checkMealConflicts`);
- the sample dataset (`initializeSampleData`);
- application startup.

`Admin.java` was a second concentration: it owned four raw `ArrayList` fields plus inventory, and
implemented CRUD for all of them *and* chef assignment.

Changing the wording of a menu prompt meant editing the file that also defined whether a meal was
vegan. Adding a GUI would have meant reimplementing the rules, because they were only reachable
through a `public static` method on a console class.

### After

| Job | Class |
|---|---|
| Terminal I/O | [`ConsoleApp`](../src/main/java/com/cookmgmt/ui/console/ConsoleApp.java) + `CustomerMenu`, `ChefMenu`, `AdminMenu` |
| Deciding what happens when an order is placed | [`OrderService`](../src/main/java/com/cookmgmt/service/OrderService.java) |
| Holding one order's state | [`Order`](../src/main/java/com/cookmgmt/domain/Order.java) |
| Deciding what conflicts | [`DietaryRuleEngine`](../src/main/java/com/cookmgmt/domain/rule/DietaryRuleEngine.java) |
| Calculating prices | [`PricingService`](../src/main/java/com/cookmgmt/service/PricingService.java) |
| Demonstration data | [`SampleData`](../src/main/java/com/cookmgmt/app/SampleData.java) |

The practical proof: the JavaFX interface was added without touching a single rule, because there
were no rules left in the console class to copy.

---

## O — Open/Closed

> Open for extension, closed for modification.

### Before

```java
for (String pref : dietary) {
    if (pref.equals("vegan") && tags.contains("non-vegan")) {
        System.out.println("⚠ Vegan conflict: " + ing + " is not vegan.");
        conflictFound = true;
    } else if (pref.equals("vegetarian") && tags.contains("meat")) {
        ...
    } else if (pref.equals("halal") && tags.contains("non-halal")) {
        ...
    } else if (pref.equals("gluten-free") && tags.contains("gluten")) {
        ...
    }
}
```

Two problems beyond the obvious one.

1. **Adding a diet meant editing this method** — inside the user-interface class — and re-testing
   every existing diet. The `non-kosher` tag already existed in the data but no branch read it, so
   kosher customers were never protected.
2. **`else if` made the branches mutually exclusive.** A customer who was both vegan *and*
   gluten-free was told about the first clash only.

### After

```java
public interface DietaryRule {
    String preferenceName();
    boolean appliesTo(Customer customer);
    Optional<Conflict> check(String ingredient, Customer customer, IngredientCatalog catalog);
}
```

Six implementations, each a small named class:
[`VeganRule`](../src/main/java/com/cookmgmt/domain/rule/VeganRule.java),
`VegetarianRule`, `HalalRule`, `KosherRule`, `GlutenFreeRule`, `DairyFreeRule`, plus
[`AllergyRule`](../src/main/java/com/cookmgmt/domain/rule/AllergyRule.java) which consults the
customer instead of the catalogue and so implements the interface directly.

`DietaryRuleEngine` applies *every* applicable rule:

```java
for (String ingredient : ingredients) {
    for (DietaryRule rule : applicable) {
        rule.check(ingredient, customer, catalog).ifPresent(conflicts::add);
    }
}
```

Adding kosher required one new class and one line in `defaultRules()`. No existing file changed.

`DietaryRuleEngineTest.newDietIsAddedBySupplyingARule` executes this claim: it defines a
pescatarian rule **inside the test file** and the engine applies it with no production change.

---

## L — Liskov Substitution

> Subtypes must be usable anywhere their supertype is, without the caller knowing.

### Before

Assignment was arithmetic inlined in `Admin.receiveOrder` over a mutable field:

```java
Chef chef = chefs.get(currentChefIndex);
currentChefIndex = (currentChefIndex + 1) % chefs.size();
```

There was nothing to substitute — and the code was outright wrong. The index was advanced *after*
reading, wrapped against the roster size at that moment. Delete a chef mid-rotation and the next
order threw `IndexOutOfBoundsException`.

### After

[`ChefAssignmentStrategy`](../src/main/java/com/cookmgmt/domain/policy/ChefAssignmentStrategy.java)
states its contract explicitly:

- returns empty **if and only if** the roster is empty;
- never returns a chef outside the supplied roster;
- **never throws for any list, including one that shrank since the last call.**

`RoundRobinAssignment` and `LeastLoadedAssignment` both satisfy it, and `KitchenService` accepts
either without change. The admin screens switch between them at runtime.

`ChefAssignmentStrategyTest.SharedContract` is a parameterised suite that runs the *same* contract
tests against *every* implementation — which is what makes this a Liskov check rather than two
unrelated test classes.

---

## I — Interface Segregation

> No client should depend on methods it does not use.

### Before

One concrete `Inventory` class was passed to everything that touched stock:

```java
public class Chef {
    private Inventory inventory;    // never read, anywhere
    public Chef(String name, String email, Inventory inventory) { ... }
}
```

`Chef` required an `Inventory` it never used, so every construction site had to supply one. Pricing
code needed only `getPrice` yet received the ability to mutate every quantity in the kitchen.

### After

Two interfaces over one implementation:

| Interface | Methods | Who takes it |
|---|---|---|
| [`ReadableInventory`](../src/main/java/com/cookmgmt/inventory/ReadableInventory.java) | `stockOf`, `priceOf`, `hasStock`, `lowStock`, snapshots | `PricingService`, `SubstitutionService`, display code |
| [`MutableInventory`](../src/main/java/com/cookmgmt/inventory/MutableInventory.java) | the above plus `addIngredient`, `restock`, `reprice`, `consume`, `release` | `InventoryService` only |

`PricingService` is now *structurally incapable* of changing stock — not by convention, but because
its field has no such method. `Chef` takes no inventory at all.

`MutableInventory` also splits what used to be one overloaded operation. The old
`addIngredient(name, qty, price)` both created and topped up, silently overwriting the price every
time, so a routine restock could reprice the menu. It is now `addIngredient` / `restock` /
`reprice` / `consume` / `release`, each with one meaning.

---

## D — Dependency Inversion

> Depend on abstractions, not concretions. High-level policy should not depend on low-level detail.

### Before

Classes created their own collaborators, so nothing could be substituted:

```java
public class Admin {
    private List<Meal> meals = new ArrayList<>();          // storage hardcoded
    private Inventory inventory = new Inventory();
}

public class Chef {
    public boolean reviewOrder(Order order) {
        System.out.print("Approve substitutions? (Y/N): ");
        Scanner sc = new Scanner(System.in);               // console I/O in the domain
        ...
    }
}
```

The `Scanner` is the clearest case. A domain class blocked on standard input, so a test could not
run it — which is why this appeared in production code:

```java
private boolean testAutoApprove = false;
public void setTestAutoApprove(boolean testAutoApprove) { ... }
```

A test-only branch, shipped in the application, to work around a design fault.

### After

Three abstractions carry the inversion:

| Interface | Implementations |
|---|---|
| [`Repository<T>`](../src/main/java/com/cookmgmt/repository/Repository.java) | `InMemoryRepository` and its four subclasses |
| [`Notifier`](../src/main/java/com/cookmgmt/notify/Notifier.java) | `ConsoleNotifier` (terminal), `InMemoryNotifier` (GUI panel and test assertions) |
| [`ConsoleIO`](../src/main/java/com/cookmgmt/ui/console/ConsoleIO.java) | `ScannerConsoleIO`; the GUI equivalent is `FxDialogs` |

`Chef.reviewOrder` no longer exists. The approve/reject decision is made by whoever is driving — a
console prompt, a JavaFX dialog, or a direct call in a test — and handed to
`KitchenService.approve`/`reject`. **`setTestAutoApprove` was deleted because nothing needs it.**

Wiring happens in exactly one place,
[`AppContext`](../src/main/java/com/cookmgmt/app/AppContext.java), the composition root:

```java
this.pricingService  = new PricingService(inventory);
this.inventoryService = new InventoryService(inventory, notifier);
this.kitchenService  = new KitchenService(orderRepository, chefRepository,
        inventoryService, pricingService, assignmentStrategy, notifier);
```

Swapping in database-backed repositories would touch this file and nothing else.

---

## Design patterns used

| Pattern | Where | Why |
|---|---|---|
| **Repository** | `repository` package | services state what storage must do; in-memory satisfies it |
| **Strategy** | `DietaryRule`, `ChefAssignmentStrategy` | behaviour chosen at runtime, extended by adding classes |
| **Builder** | `Meal.Builder` | the admin screens collect a recipe field by field; the builder holds the partial state so a `Meal` is never observable half-formed |
| **Value Object** | `Money`, `Conflict`, `Invoice` | immutable, compared by value, no identity |
| **Observer** | `Notifier`, `ViewRefresher` | low-stock and order events reach whichever interface is running; GUI tabs are told that shared state changed without knowing about each other |
| **Composition Root** | `AppContext` | one assembly point for the object graph |

---

## Two later additions worth pointing at

Both came out of testing the finished GUI, and both are cases where the principles above did real
work rather than decorative work.

**Open/Closed, in the interface layer.** Cancelling an order on the Customer tab has to reach the
chef's queue, the admin report and the stock table. The obvious fix — give each view a reference to
the others — couples all three and means every new screen has to be added to the existing ones.
Instead the views implement [`Refreshable`](../src/main/java/com/cookmgmt/ui/fx/Refreshable.java)
and register with [`ViewRefresher`](../src/main/java/com/cookmgmt/ui/fx/ViewRefresher.java), which
fans one notification out to all of them. A fourth tab joins by registering; no existing view is
edited. Exactly the same shape as `Notifier` in the service layer, applied one layer up.

**Single Responsibility, in deciding what a button may do.** Whether an order can be cancelled is
stated once, on
[`OrderStatus.canTransitionTo`](../src/main/java/com/cookmgmt/domain/OrderStatus.java), and the
interfaces ask it rather than restating it:

```java
cancelButton.setDisable(order == null
        || !order.getStatus().canTransitionTo(OrderStatus.CANCELLED));
```

The console filters its list with the same call. Neither can offer an action the domain would
refuse, and widening the cancellation window later means editing one `switch` in the enum. Where the
GUI had written a rule out a second time — `startButton` was enabled for any order that was merely
"not terminal" — it was already wrong: an order awaiting approval is not terminal, but starting it
throws. The button offered an action that could only ever fail.

---

## What to look at first

If you have five minutes, read these three files in order:

1. [`DietaryRule`](../src/main/java/com/cookmgmt/domain/rule/DietaryRule.java) — the Open/Closed
   case, with the `else if` chain it replaced quoted in the Javadoc.
2. [`AppContext`](../src/main/java/com/cookmgmt/app/AppContext.java) — the whole object graph on one
   screen.
3. [`OrderService.place`](../src/main/java/com/cookmgmt/service/OrderService.java) — the single
   order path that both user interfaces call, replacing two implementations that disagreed.
