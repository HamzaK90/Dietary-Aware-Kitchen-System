package com.cookmgmt.inventory;

import com.cookmgmt.domain.Money;
import com.cookmgmt.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InMemoryInventory")
class InMemoryInventoryTest {

    private MutableInventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new InMemoryInventory();
        inventory.addIngredient("tofu", 10, Money.of("3.50"));
        inventory.addIngredient("rice", 20, Money.of("1.50"));
    }

    @Test
    @DisplayName("consumes stock")
    void consumesStock() {
        inventory.consume("tofu", 4);
        assertEquals(6, inventory.stockOf("tofu"));
    }

    @Test
    @DisplayName("refuses to consume more than is available")
    void refusesToOverConsume() {
        // updateStock() applied any delta unconditionally, so stock silently went negative and
        // the caller was never told the kitchen could not supply the order.
        InsufficientStockException thrown = assertThrows(InsufficientStockException.class,
                () -> inventory.consume("tofu", 11));

        assertEquals("tofu", thrown.getIngredient());
        assertEquals(11, thrown.getRequested());
        assertEquals(10, thrown.getAvailable());
    }

    @Test
    @DisplayName("leaves stock untouched when a consume is refused")
    void leavesStockUntouchedOnRefusal() {
        assertThrows(InsufficientStockException.class, () -> inventory.consume("tofu", 999));
        assertEquals(10, inventory.stockOf("tofu"));
    }

    @Test
    @DisplayName("stock can never go negative")
    void stockNeverGoesNegative() {
        assertThrows(InsufficientStockException.class, () -> inventory.consume("rice", 21));
        assertTrue(inventory.stockOf("rice") >= 0);
    }

    @Test
    @DisplayName("releases stock back")
    void releasesStockBack() {
        inventory.consume("tofu", 4);
        inventory.release("tofu", 4);
        assertEquals(10, inventory.stockOf("tofu"));
    }

    @Test
    @DisplayName("restock adds quantity without changing the price")
    void restockDoesNotChangePrice() {
        // addIngredient() used to be the only way to top up, and it overwrote the stored price
        // every time - so a routine restock could silently reprice the whole menu.
        inventory.restock("tofu", 5);

        assertEquals(15, inventory.stockOf("tofu"));
        assertEquals(Money.of("3.50"), inventory.priceOf("tofu"));
    }

    @Test
    @DisplayName("reprice changes the price without changing stock")
    void repriceDoesNotChangeStock() {
        inventory.reprice("tofu", Money.of("4.00"));

        assertEquals(Money.of("4.00"), inventory.priceOf("tofu"));
        assertEquals(10, inventory.stockOf("tofu"));
    }

    @Test
    @DisplayName("treats ingredient names case-insensitively")
    void treatsNamesCaseInsensitively() {
        // "Tofu" and "tofu" used to be two separate entries, so a meal could report a shortage of
        // an ingredient the kitchen had plenty of.
        inventory.restock("  TOFU  ", 5);
        assertEquals(15, inventory.stockOf("Tofu"));
        assertEquals(2, inventory.ingredients().size(),
                "Different spellings must not create separate entries");
    }

    @Test
    @DisplayName("reports unknown ingredients as zero stock and zero price")
    void reportsUnknownIngredientsAsZero() {
        assertEquals(0, inventory.stockOf("caviar"));
        assertEquals(Money.ZERO, inventory.priceOf("caviar"));
        assertFalse(inventory.isKnown("caviar"));
    }

    @Test
    @DisplayName("lists low stock in ascending quantity order")
    void listsLowStockDeterministically() {
        inventory.addIngredient("saffron", 1, Money.of("9.00"));
        inventory.addIngredient("basil", 3, Money.of("1.00"));

        List<String> low = inventory.lowStock();

        assertEquals(List.of("saffron", "basil"), low);
    }

    @Test
    @DisplayName("does not report well-stocked ingredients as low")
    void doesNotReportWellStockedIngredients() {
        assertFalse(inventory.lowStock().contains("rice"));
    }

    @Test
    @DisplayName("rejects negative quantities and prices")
    void rejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> inventory.addIngredient("salt", -1, Money.of("1.00")));
        assertThrows(IllegalArgumentException.class,
                () -> inventory.addIngredient("salt", 1, Money.ZERO.minus(Money.of("1.00"))));
        assertThrows(IllegalArgumentException.class, () -> inventory.restock("tofu", -5));
    }

    @Test
    @DisplayName("rejects blank ingredient names")
    void rejectsBlankNames() {
        assertThrows(IllegalArgumentException.class,
                () -> inventory.addIngredient("   ", 1, Money.of("1.00")));
    }

    @Test
    @DisplayName("removes an ingredient entirely")
    void removesAnIngredient() {
        assertTrue(inventory.remove("tofu"));
        assertFalse(inventory.isKnown("tofu"));
        assertFalse(inventory.remove("tofu"));
    }
}
