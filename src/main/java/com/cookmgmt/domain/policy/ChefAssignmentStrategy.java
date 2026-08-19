package com.cookmgmt.domain.policy;

import com.cookmgmt.domain.Chef;
import com.cookmgmt.repository.OrderRepository;

import java.util.List;
import java.util.Optional;

/**
 * Decides which chef a new order goes to.
 *
 * <p>This is the project's Liskov Substitution demonstration. Assignment used to be arithmetic
 * inlined in {@code Admin.receiveOrder} over a mutable {@code currentChefIndex} field, so there was
 * no way to change the policy without editing the class that also managed meals, customers, chefs
 * and inventory.
 *
 * <p>Every implementation honours the same contract, so {@link com.cookmgmt.service.KitchenService}
 * can be handed any of them and behaves correctly with all:
 * <ul>
 *   <li>returns empty if and only if {@code chefs} is empty;</li>
 *   <li>never returns a chef who is not in {@code chefs};</li>
 *   <li>never throws for any list, including one that shrank since the last call.</li>
 * </ul>
 * That last clause matters: the original code stored an index between calls and applied it to
 * whatever list existed later, so deleting a chef mid-rotation threw
 * {@link IndexOutOfBoundsException} on the next order.
 */
public interface ChefAssignmentStrategy {

    /**
     * @param chefs  the chefs available right now; may be empty
     * @param orders order storage, so load-aware strategies can see current workloads
     * @return the chosen chef, or empty when there are no chefs
     */
    Optional<Chef> selectChef(List<Chef> chefs, OrderRepository orders);

    /** Human-readable policy name, shown in the admin screens. */
    String name();
}
