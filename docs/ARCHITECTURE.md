# Architecture

## Layers

```
┌─────────────────────────────────────────────────────────────────┐
│ ui.console                        ui.fx                         │
│   ConsoleApp                        FxApplication               │
│   ConsoleIO / ScannerConsoleIO      CustomerView                │
│   CustomerMenu, ChefMenu,           ChefView                    │
│   AdminMenu                         AdminView                   │
│                                                                 │
│   No business rules live here.                                  │
└────────────────────────────┬────────────────────────────────────┘
                             │ both build one
                             ▼
                   ┌───────────────────┐
                   │  app.AppContext   │   composition root
                   │  app.SampleData   │
                   └─────────┬─────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ service                                                         │
│   OrderService      place / preview / cancel                    │
│   KitchenService    assign / approve / reject / cook / complete  │
│   InventoryService  reserve / release / shortages / low stock    │
│   PricingService    prices and invoices                          │
│   SubstitutionService  candidate replacements                    │
│   CatalogService · CustomerService · StaffService   (CRUD)       │
└──────────┬──────────────────────────────────────┬───────────────┘
           ▼                                      ▼
┌────────────────────────┐          ┌─────────────────────────────┐
│ repository             │          │ domain                      │
│   Repository<T>        │          │   Meal, Order, Customer,    │
│   InMemoryRepository   │          │   Chef, Money, Invoice,     │
│   Meal/Customer/Chef/  │          │   Conflict, OrderStatus     │
│   Order repositories   │          │   rule.*    dietary rules   │
│                        │          │   policy.*  assignment      │
│ inventory              │          │   exception.*               │
│   ReadableInventory    │          │                             │
│   MutableInventory     │          │ Depends on nothing but      │
│   InMemoryInventory    │          │ support.Text and the JDK.   │
└────────────────────────┘          └─────────────────────────────┘
```

## The dependency rule

**Dependencies point inward.** The domain does not import from `service`, `repository` or `ui`;
services do not import from `ui`.

The one deliberate exception is documentation: a few domain Javadoc comments name the service that
uses them, to help a reader navigate. No domain *code* references a service.

There is a second rule specific to this project:

> **No business rule may live in `ui.console` or `ui.fx`.**

Whether a meal conflicts, what to substitute, what something costs, whether stock allows an order,
and which chef gets it are all service calls. If a controller started deciding any of these, the two
interfaces would immediately be able to disagree — which is exactly the failure the original code
had, where `Main.placeOrder` and `Customer.placeOrder` implemented ordering differently and only one
of them ran.

## Packages

| Package | Contains | Notes |
|---|---|---|
| `app` | `AppContext`, `SampleData` | the only place concrete implementations are chosen |
| `domain` | entities and value objects | `Entity` gives UUID identity; `Money` wraps `BigDecimal` |
| `domain.rule` | `DietaryRule` + 7 implementations, `IngredientCatalog`, `DietaryRuleEngine` | conflict detection |
| `domain.policy` | `ChefAssignmentStrategy` + 2 implementations | who cooks what |
| `domain.exception` | `InsufficientStock`, `DuplicateEmail`, `OrderState` | all unchecked |
| `inventory` | read/write interfaces + in-memory implementation | interface segregation |
| `repository` | `Repository<T>` + in-memory implementations | dependency inversion |
| `service` | eight application services | orchestration only; no storage details |
| `notify` | `Notifier`, `ConsoleNotifier`, `InMemoryNotifier` | keeps `System.out` out of the rules |
| `support` | `Text` | name normalisation and CSV parsing, used everywhere |
| `ui.console` / `ui.fx` | the two front ends | interchangeable, no rules |

## Key flows

### Placing an order

```
CustomerView / CustomerMenu
  └─ OrderService.preview(customer, meal)
       ├─ DietaryRuleEngine.check          → List<Conflict>
       ├─ SubstitutionService.proposeFor   → Map<original, replacement>
       ├─ InventoryService.shortages       → List<Shortage>
       └─ PricingService.priceOf           → Money
  ── user decides ──
  └─ OrderService.place(customer, meal, substitutions)
       ├─ reject outright if an allergen survives substitution
       ├─ InventoryService.reserve         → all-or-nothing, else InsufficientStockException
       ├─ OrderRepository.save             → order number from the repository's sequence
       └─ KitchenService.assign            → ChefAssignmentStrategy picks a chef, Notifier tells them
```

Stock is reserved **before** the order is stored, so a shortage aborts without leaving an order the
kitchen cannot cook.

### Cooking an order

```
ChefView / ChefMenu
  └─ KitchenService.queueFor(chef)      derived from OrderRepository; reading does not consume it
  ── chef decides (dialog, prompt, or a direct call in a test) ──
  ├─ approve  → status APPROVED
  └─ reject   → status REJECTED  +  InventoryService.release  (ingredients go back)
  └─ complete → PricingService.quote, price frozen on the order, Invoice returned
```

### Order state machine

```
PENDING ─────────────────► IN_PROGRESS ──► COMPLETED
   │                            ▲
   ├──► NEEDS_APPROVAL ──► APPROVED
   │            └────────► REJECTED
   └──► CANCELLED
```

Declared on `OrderStatus.canTransitionTo` and enforced by `Order`. Anything else throws
`OrderStateException`. All seven states are reachable and used.

## Deliberate design choices

**In memory, no database.** A course constraint, kept. `AppContext` is the only place storage is
chosen, so persistence would slot in behind `Repository<T>` and `MutableInventory` without touching
a service.

**`LinkedHashMap` and `LinkedHashSet` throughout.** Iteration order is stable, so menu numbering,
reports and substitution suggestions are reproducible. The original code iterated `HashMap` key sets
and took element 0, making the suggestion a customer saw vary between identical runs.

**Ingredient names normalised at every boundary.** `Text.normalize` (trim + lowercase, `Locale.ROOT`)
is applied on the way into `Meal`, `Customer`, `Inventory`, `Order` substitutions and
`IngredientCatalog`. A casing mismatch between a lookup table and its lookups is what silently
disabled every beef and shrimp dietary rule in the original code.

**Money is never `double`.** `Money` wraps `BigDecimal` at scale 2, `HALF_UP`.

**Prices frozen at completion.** `Order.complete(Money)` stores the total, and `PricingService`
prefers it when rendering an invoice, so a later reprice cannot rewrite a bill already issued.

**Assignment lives on the order, not the chef.** `Order.assignedChefId` plus
`OrderRepository.findQueueFor` means one source of truth. The old `Queue<Order>` on `Chef` was
handed out by reference and consumed by `poll()`, which is how a rejected order became unreachable.

## Extension points

| To add… | Do this | Nothing else changes |
|---|---|---|
| a dietary rule | implement `DietaryRule`, add it to `DietaryRuleEngine.defaultRules()` | ✓ |
| a chef assignment policy | implement `ChefAssignmentStrategy` | ✓ |
| persistence | implement `Repository<T>` / `MutableInventory`, wire in `AppContext` | ✓ |
| a notification channel (email, log) | implement `Notifier` | ✓ |
| another user interface | build against the services `AppContext` exposes | ✓ |
