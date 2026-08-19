package com.cookmgmt.domain.policy;

import com.cookmgmt.domain.Chef;
import com.cookmgmt.repository.OrderRepository;

import java.util.List;
import java.util.Optional;

/**
 * Hands orders to chefs in rotation.
 *
 * <p>Fixes a crash in the original implementation:
 *
 * <pre>{@code
 * Chef chef = chefs.get(currentChefIndex);                     // <-- could be out of bounds
 * currentChefIndex = (currentChefIndex + 1) % chefs.size();
 * }</pre>
 *
 * <p>The index was advanced <em>after</em> reading, and wrapped against the list size as it was at
 * that moment. With two chefs the index would reach 1; deleting a chef then left a one-element list
 * and the next order called {@code get(1)} on it, throwing {@link IndexOutOfBoundsException}.
 *
 * <p>The counter here is a monotonically increasing tally that is only reduced modulo the list size
 * at the moment of use, so it is always in range no matter how the roster changed in between.
 */
public class RoundRobinAssignment implements ChefAssignmentStrategy {

    private int assignmentCount;

    @Override
    public Optional<Chef> selectChef(List<Chef> chefs, OrderRepository orders) {
        if (chefs == null || chefs.isEmpty()) {
            return Optional.empty();
        }
        int index = Math.floorMod(assignmentCount, chefs.size());
        assignmentCount++;
        return Optional.of(chefs.get(index));
    }

    @Override
    public String name() {
        return "Round robin";
    }
}
