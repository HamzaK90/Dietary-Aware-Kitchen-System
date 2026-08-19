package com.cookmgmt.domain.policy;

import com.cookmgmt.domain.Chef;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Order;
import com.cookmgmt.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Chef assignment strategies")
class ChefAssignmentStrategyTest {

    private OrderRepository orders;
    private Chef nora;
    private Chef zaid;

    @BeforeEach
    void setUp() {
        orders = new OrderRepository();
        nora = new Chef("Chef Nora", "nora@kitchen.com");
        zaid = new Chef("Chef Zaid", "zaid@kitchen.com");
    }

    /** Every implementation must honour the shared contract - this is the Liskov check. */
    static Stream<ChefAssignmentStrategy> strategies() {
        return Stream.of(new RoundRobinAssignment(), new LeastLoadedAssignment());
    }

    @Nested
    @DisplayName("contract honoured by every implementation")
    class SharedContract {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.cookmgmt.domain.policy.ChefAssignmentStrategyTest#strategies")
        @DisplayName("returns empty when there are no chefs")
        void returnsEmptyWhenNoChefs(ChefAssignmentStrategy strategy) {
            assertTrue(strategy.selectChef(List.of(), orders).isEmpty());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.cookmgmt.domain.policy.ChefAssignmentStrategyTest#strategies")
        @DisplayName("never returns a chef outside the supplied roster")
        void neverReturnsAChefOutsideTheRoster(ChefAssignmentStrategy strategy) {
            List<Chef> roster = List.of(nora, zaid);
            for (int i = 0; i < 10; i++) {
                Optional<Chef> chosen = strategy.selectChef(roster, orders);
                assertTrue(chosen.isPresent());
                assertTrue(roster.contains(chosen.get()));
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.cookmgmt.domain.policy.ChefAssignmentStrategyTest#strategies")
        @DisplayName("survives the roster shrinking between calls")
        void survivesRosterShrinking(ChefAssignmentStrategy strategy) {
            List<Chef> roster = new ArrayList<>(List.of(nora, zaid));
            strategy.selectChef(roster, orders);
            strategy.selectChef(roster, orders);

            roster.remove(zaid);

            assertDoesNotThrow(() -> strategy.selectChef(roster, orders));
            assertEquals(Optional.of(nora), strategy.selectChef(roster, orders));
        }
    }

    @Nested
    @DisplayName("RoundRobinAssignment")
    class RoundRobin {

        @Test
        @DisplayName("hands orders to each chef in turn")
        void rotatesThroughChefs() {
            RoundRobinAssignment strategy = new RoundRobinAssignment();
            List<Chef> roster = List.of(nora, zaid);

            assertEquals(Optional.of(nora), strategy.selectChef(roster, orders));
            assertEquals(Optional.of(zaid), strategy.selectChef(roster, orders));
            assertEquals(Optional.of(nora), strategy.selectChef(roster, orders));
        }

        @Test
        @DisplayName("regression: removing a chef mid-rotation no longer throws")
        void removingAChefMidRotationDoesNotThrow() {
            /*
             * The original code in Admin.receiveOrder was:
             *     Chef chef = chefs.get(currentChefIndex);
             *     currentChefIndex = (currentChefIndex + 1) % chefs.size();
             *
             * With two chefs the stored index reached 1. Deleting a chef left a one-element list,
             * and the next order called get(1) on it - IndexOutOfBoundsException, taking down the
             * whole order flow. The index is now only reduced modulo the roster size at the moment
             * it is used, so it is always in range.
             */
            RoundRobinAssignment strategy = new RoundRobinAssignment();
            List<Chef> twoChefs = List.of(nora, zaid);

            strategy.selectChef(twoChefs, orders);   // index advances to 1
            strategy.selectChef(twoChefs, orders);

            List<Chef> oneChef = List.of(nora);
            Optional<Chef> chosen = assertDoesNotThrow(() -> strategy.selectChef(oneChef, orders));
            assertEquals(Optional.of(nora), chosen);
        }

        @Test
        @DisplayName("keeps working through many roster changes")
        void keepsWorkingThroughManyRosterChanges() {
            RoundRobinAssignment strategy = new RoundRobinAssignment();
            List<Chef> roster = new ArrayList<>(List.of(nora, zaid));

            for (int i = 0; i < 20; i++) {
                assertDoesNotThrow(() -> strategy.selectChef(roster, orders));
                if (i == 5) {
                    roster.remove(zaid);
                }
                if (i == 12) {
                    roster.add(zaid);
                }
            }
        }
    }

    @Nested
    @DisplayName("LeastLoadedAssignment")
    class LeastLoaded {

        @Test
        @DisplayName("prefers the chef with the fewest unfinished orders")
        void prefersTheLeastBusyChef() {
            giveOrderTo(nora);
            giveOrderTo(nora);
            giveOrderTo(zaid);

            LeastLoadedAssignment strategy = new LeastLoadedAssignment();
            assertEquals(Optional.of(zaid), strategy.selectChef(List.of(nora, zaid), orders));
        }

        @Test
        @DisplayName("breaks ties by name so the choice is reproducible")
        void breaksTiesDeterministically() {
            LeastLoadedAssignment strategy = new LeastLoadedAssignment();
            assertEquals(Optional.of(nora), strategy.selectChef(List.of(zaid, nora), orders));
        }

        private void giveOrderTo(Chef chef) {
            Customer customer = new Customer("Diner", "diner@example.com");
            Meal meal = Meal.of("Rice", Map.of("rice", 1), 10);
            Order order = new Order(orders.nextOrderNumber(), customer, meal);
            order.assignTo(chef);
            orders.save(order);
        }
    }
}
