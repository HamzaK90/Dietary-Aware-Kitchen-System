package com.cookmgmt.domain.policy;

import com.cookmgmt.domain.Chef;
import com.cookmgmt.repository.OrderRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Hands each order to whichever chef currently has the fewest unfinished orders.
 *
 * <p>Fairer than plain rotation when dishes differ in effort, since a chef stuck on a long order is
 * not handed more work simply because their turn came round. Ties break on name so the choice is
 * reproducible - important for tests, and the reason the outcome does not depend on map ordering
 * the way the old substitution logic did.
 *
 * <p>Exists mainly to show that {@link ChefAssignmentStrategy} really is substitutable: swapping
 * this in requires no change to {@link com.cookmgmt.service.KitchenService} or anything above it.
 */
public class LeastLoadedAssignment implements ChefAssignmentStrategy {

    @Override
    public Optional<Chef> selectChef(List<Chef> chefs, OrderRepository orders) {
        if (chefs == null || chefs.isEmpty()) {
            return Optional.empty();
        }
        return chefs.stream().min(Comparator
                .comparingInt(orders::activeLoadOf)
                .thenComparing(Chef::getName));
    }

    @Override
    public String name() {
        return "Least loaded";
    }
}
