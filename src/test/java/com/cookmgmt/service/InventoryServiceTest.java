package com.cookmgmt.service;

import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.exception.InsufficientStockException;
import com.cookmgmt.inventory.InMemoryInventory;
import com.cookmgmt.inventory.MutableInventory;
import com.cookmgmt.inventory.ReadableInventory;
import com.cookmgmt.notify.InMemoryNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InventoryService")
class InventoryServiceTest {

    private MutableInventory inventory;
    private InMemoryNotifier notifier;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        inventory = new InMemoryInventory();
        notifier = new InMemoryNotifier();
        service = new InventoryService(inventory, notifier);

        service.addIngredient("tofu", 10, Money.of("3.50"));
        service.addIngredient("rice", 20, Money.of("1.50"));
    }

    @Test
    @DisplayName("reserving a recipe removes exactly the quantities it needs")
    void reservingRemovesExactQuantities() {
        service.reserve(Map.of("tofu", 1, "rice", 2));

        assertEquals(9, inventory.stockOf("tofu"));
        assertEquals(18, inventory.stockOf("rice"));
    }

    @Test
    @DisplayName("reservation is all or nothing")
    void reservationIsAllOrNothing() {
        // Checking and consuming in a single pass would leave the inventory half-deducted when the
        // last ingredient turned out to be short, with no record of what to put back.
        Map<String, Integer> recipe = new LinkedHashMap<>();
        recipe.put("rice", 2);          // available
        recipe.put("tofu", 999);        // not available

        assertThrows(InsufficientStockException.class, () -> service.reserve(recipe));

        assertEquals(20, inventory.stockOf("rice"), "rice must not have been deducted");
        assertEquals(10, inventory.stockOf("tofu"));
    }

    @Test
    @DisplayName("releasing puts a reservation back")
    void releasingPutsStockBack() {
        Map<String, Integer> recipe = Map.of("tofu", 1, "rice", 2);
        service.reserve(recipe);

        service.release(recipe);

        assertEquals(10, inventory.stockOf("tofu"));
        assertEquals(20, inventory.stockOf("rice"));
    }

    @Test
    @DisplayName("reports which ingredients are short and by how much")
    void reportsShortages() {
        List<InventoryService.Shortage> shortages =
                service.shortages(Map.of("tofu", 15, "rice", 2));

        assertEquals(1, shortages.size());
        InventoryService.Shortage shortage = shortages.get(0);
        assertEquals("tofu", shortage.ingredient());
        assertEquals(15, shortage.required());
        assertEquals(10, shortage.available());
        assertEquals(5, shortage.missing());
    }

    @Test
    @DisplayName("canFulfil answers without changing anything")
    void canFulfilIsSideEffectFree() {
        assertTrue(service.canFulfil(Map.of("tofu", 1)));
        assertFalse(service.canFulfil(Map.of("tofu", 99)));
        assertEquals(10, inventory.stockOf("tofu"));
    }

    @Test
    @DisplayName("raises a restock warning when an ingredient falls below the threshold")
    void raisesRestockWarning() {
        // Business rules used to print their own warnings, so nothing could observe them but a
        // human reading the terminal.
        service.reserve(Map.of("tofu", 7));   // 10 -> 3, below the threshold of 5

        assertTrue(notifier.anyContaining("Low stock: \"tofu\""),
                "Notifications were " + notifier.all());
    }

    @Test
    @DisplayName("does not warn about well stocked ingredients")
    void doesNotWarnAboutWellStockedIngredients() {
        service.reserve(Map.of("rice", 1));

        assertFalse(notifier.anyContaining("rice"));
    }

    @Test
    @DisplayName("uses the shared low stock threshold")
    void usesSharedThreshold() {
        service.addIngredient("saffron", ReadableInventory.LOW_STOCK_THRESHOLD - 1, Money.of("9.00"));

        assertTrue(service.lowStock().contains("saffron"));
    }

    @Test
    @DisplayName("exposes only a read-only view of the inventory")
    void exposesReadOnlyView() {
        // Interface segregation: callers that only display stock cannot accidentally change it.
        ReadableInventory view = service.inventory();

        assertEquals(10, view.stockOf("tofu"));
        assertEquals(Money.of("3.50"), view.priceOf("tofu"));
    }
}
