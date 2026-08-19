package com.cookmgmt.repository;

import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.exception.DuplicateEmailException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Repositories")
class PersonRepositoryTest {

    private CustomerRepository customers;

    @BeforeEach
    void setUp() {
        customers = new CustomerRepository();
    }

    @Nested
    @DisplayName("unique email")
    class UniqueEmail {

        @Test
        @DisplayName("regression: a second account cannot reuse an email address")
        void rejectsDuplicateEmail() {
            // Nothing enforced uniqueness, and login was a findFirst() over the list - so the
            // second account could be created but never signed into.
            customers.save(new Customer("Layla", "layla@example.com"));

            assertThrows(DuplicateEmailException.class,
                    () -> customers.save(new Customer("Impostor", "layla@example.com")));
            assertEquals(1, customers.count());
        }

        @Test
        @DisplayName("treats email as case-insensitive")
        void treatsEmailCaseInsensitively() {
            customers.save(new Customer("Layla", "Layla@Example.COM"));

            assertThrows(DuplicateEmailException.class,
                    () -> customers.save(new Customer("Impostor", "layla@example.com")));
            assertTrue(customers.findByEmail("LAYLA@EXAMPLE.COM").isPresent());
        }

        @Test
        @DisplayName("saving the same person again is an update, not a duplicate")
        void savingTheSamePersonAgainIsAnUpdate() {
            Customer layla = customers.save(new Customer("Layla", "layla@example.com"));

            layla.setName("Layla A.");
            customers.save(layla);

            assertEquals(1, customers.count());
            assertEquals("Layla A.", customers.findByEmail("layla@example.com")
                    .orElseThrow().getName());
        }

        @Test
        @DisplayName("an email freed by a deletion can be reused")
        void freedEmailCanBeReused() {
            Customer layla = customers.save(new Customer("Layla", "layla@example.com"));
            customers.delete(layla);

            customers.save(new Customer("Someone Else", "layla@example.com"));

            assertEquals(1, customers.count());
            assertEquals("Someone Else",
                    customers.findByEmail("layla@example.com").orElseThrow().getName());
        }
    }

    @Nested
    @DisplayName("identity")
    class Identity {

        @Test
        @DisplayName("regression: entities are found by identity, not by field equality")
        void entitiesAreFoundByIdentity() {
            /*
             * Admin.editMeal/updateCustomer/updateChef used List.indexOf, which relies on equals -
             * and none of the entities overrode it. The lookup was therefore a reference
             * comparison that happened to work only when the caller passed the identical object.
             */
            MealRepository meals = new MealRepository();
            Meal stored = meals.save(Meal.of("Burger", Map.of("beef", 1), 20));
            Meal identicalLooking = Meal.of("Burger", Map.of("beef", 1), 20);

            assertFalse(stored.equals(identicalLooking),
                    "two separately created meals are different entities");
            assertTrue(meals.findById(stored.getId()).isPresent());
            assertFalse(meals.findById(identicalLooking.getId()).isPresent());

            stored.setName("Vegan Burger");
            meals.save(stored);

            assertEquals(1, meals.count(), "updating must not create a second row");
            assertEquals("Vegan Burger", meals.findById(stored.getId()).orElseThrow().getName());
        }

        @Test
        @DisplayName("an entity equals itself and nothing else")
        void entityEqualsOnlyItself() {
            Customer layla = new Customer("Layla", "layla@example.com");
            Customer sameDetails = new Customer("Layla", "layla@example.com");

            assertEquals(layla, layla);
            assertFalse(layla.equals(sameDetails));
        }
    }

    @Nested
    @DisplayName("order numbering")
    class OrderNumbering {

        @Test
        @DisplayName("regression: each repository owns its own sequence starting at 1")
        void eachRepositoryStartsAtOne() {
            // Order numbers came from a static counter shared by the whole JVM and never reset,
            // so they leaked between scenarios and no test could assert on a specific value.
            OrderRepository first = new OrderRepository();
            assertEquals(1, first.nextOrderNumber());
            assertEquals(2, first.nextOrderNumber());

            OrderRepository second = new OrderRepository();
            assertEquals(1, second.nextOrderNumber());
        }

        @Test
        @DisplayName("clearing resets the sequence")
        void clearingResetsTheSequence() {
            OrderRepository orders = new OrderRepository();
            orders.nextOrderNumber();
            orders.nextOrderNumber();

            orders.clear();

            assertEquals(1, orders.nextOrderNumber());
        }
    }

    @Test
    @DisplayName("findAll preserves insertion order")
    void findAllPreservesInsertionOrder() {
        // Deterministic ordering is what makes menu numbering and reports reproducible.
        customers.save(new Customer("Zoe", "zoe@example.com"));
        customers.save(new Customer("Adam", "adam@example.com"));
        customers.save(new Customer("Maya", "maya@example.com"));

        assertEquals(List.of("Zoe", "Adam", "Maya"),
                customers.findAll().stream().map(Customer::getName).toList());
    }
}
