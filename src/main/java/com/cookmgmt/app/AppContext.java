package com.cookmgmt.app;

import com.cookmgmt.domain.policy.ChefAssignmentStrategy;
import com.cookmgmt.domain.policy.RoundRobinAssignment;
import com.cookmgmt.domain.rule.DietaryRuleEngine;
import com.cookmgmt.inventory.InMemoryInventory;
import com.cookmgmt.inventory.MutableInventory;
import com.cookmgmt.notify.InMemoryNotifier;
import com.cookmgmt.notify.Notifier;
import com.cookmgmt.repository.ChefRepository;
import com.cookmgmt.repository.CustomerRepository;
import com.cookmgmt.repository.MealRepository;
import com.cookmgmt.repository.OrderRepository;
import com.cookmgmt.service.CatalogService;
import com.cookmgmt.service.CustomerService;
import com.cookmgmt.service.InventoryService;
import com.cookmgmt.service.KitchenService;
import com.cookmgmt.service.OrderService;
import com.cookmgmt.service.PricingService;
import com.cookmgmt.service.StaffService;
import com.cookmgmt.service.SubstitutionService;

/**
 * The composition root: the one place where concrete implementations are chosen and wired together.
 *
 * <p>Every class below this point receives its collaborators through its constructor and depends
 * only on interfaces. That is what makes the Dependency Inversion claim real rather than
 * decorative - the old code created its own dependencies inline ({@code new ArrayList<>()} inside
 * {@code Admin}, {@code new Scanner(System.in)} inside {@code Chef}), so nothing could be replaced
 * for a test or for a different user interface.
 *
 * <p>It is also what lets the console and the JavaFX front ends be genuinely the same application:
 * each builds an {@code AppContext} and talks to the services it exposes. Neither contains business
 * logic, and a change to a rule takes effect in both without being written twice.
 *
 * <p>The only thing a user interface customises is the {@link Notifier}, so messages land in the
 * right place - the terminal, a GUI panel, or a list a test can assert on.
 */
public class AppContext {

    // --- storage (in memory by design; the project uses no database) --------
    private final MutableInventory inventory = new InMemoryInventory();
    private final MealRepository mealRepository = new MealRepository();
    private final CustomerRepository customerRepository = new CustomerRepository();
    private final ChefRepository chefRepository = new ChefRepository();
    private final OrderRepository orderRepository = new OrderRepository();

    // --- domain policy ------------------------------------------------------
    private final DietaryRuleEngine ruleEngine;
    private final ChefAssignmentStrategy assignmentStrategy;
    private final Notifier notifier;

    // --- services -----------------------------------------------------------
    private final PricingService pricingService;
    private final InventoryService inventoryService;
    private final SubstitutionService substitutionService;
    private final KitchenService kitchenService;
    private final OrderService orderService;
    private final CatalogService catalogService;
    private final CustomerService customerService;
    private final StaffService staffService;

    /** Builds a context that records notifications in memory. */
    public AppContext() {
        this(new InMemoryNotifier());
    }

    public AppContext(Notifier notifier) {
        this(notifier, DietaryRuleEngine.withDefaults(), new RoundRobinAssignment());
    }

    /**
     * Full control over the swappable policies, which is how tests pin down behaviour and how the
     * admin screens change the assignment strategy.
     */
    public AppContext(Notifier notifier,
                      DietaryRuleEngine ruleEngine,
                      ChefAssignmentStrategy assignmentStrategy) {
        this.notifier = notifier;
        this.ruleEngine = ruleEngine;
        this.assignmentStrategy = assignmentStrategy;

        this.pricingService = new PricingService(inventory);
        this.inventoryService = new InventoryService(inventory, notifier);
        this.substitutionService = new SubstitutionService(ruleEngine, inventory);
        this.kitchenService = new KitchenService(orderRepository, chefRepository,
                inventoryService, pricingService, assignmentStrategy, notifier);
        this.orderService = new OrderService(orderRepository, inventoryService, pricingService,
                ruleEngine, substitutionService, kitchenService, notifier);
        this.catalogService = new CatalogService(mealRepository, pricingService);
        this.customerService = new CustomerService(customerRepository);
        this.staffService = new StaffService(chefRepository);
    }

    /** A context pre-loaded with the demonstration menu, customers and chefs. */
    public static AppContext withSampleData() {
        AppContext context = new AppContext();
        SampleData.load(context);
        return context;
    }

    public static AppContext withSampleData(Notifier notifier) {
        AppContext context = new AppContext(notifier);
        SampleData.load(context);
        return context;
    }

    public MutableInventory inventory() {
        return inventory;
    }

    public MealRepository mealRepository() {
        return mealRepository;
    }

    public CustomerRepository customerRepository() {
        return customerRepository;
    }

    public ChefRepository chefRepository() {
        return chefRepository;
    }

    public OrderRepository orderRepository() {
        return orderRepository;
    }

    public DietaryRuleEngine ruleEngine() {
        return ruleEngine;
    }

    public ChefAssignmentStrategy assignmentStrategy() {
        return assignmentStrategy;
    }

    public Notifier notifier() {
        return notifier;
    }

    public PricingService pricingService() {
        return pricingService;
    }

    public InventoryService inventoryService() {
        return inventoryService;
    }

    public SubstitutionService substitutionService() {
        return substitutionService;
    }

    public KitchenService kitchenService() {
        return kitchenService;
    }

    public OrderService orderService() {
        return orderService;
    }

    public CatalogService catalogService() {
        return catalogService;
    }

    public CustomerService customerService() {
        return customerService;
    }

    public StaffService staffService() {
        return staffService;
    }

    /** Empties every store. Used to reset state between test scenarios. */
    public void reset() {
        mealRepository.clear();
        customerRepository.clear();
        chefRepository.clear();
        orderRepository.clear();
    }
}
