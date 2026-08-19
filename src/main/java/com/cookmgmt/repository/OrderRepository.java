package com.cookmgmt.repository;

import com.cookmgmt.domain.Chef;
import com.cookmgmt.domain.Customer;
import com.cookmgmt.domain.Order;
import com.cookmgmt.domain.OrderStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stores every order ever placed, and owns the sequence that numbers them.
 *
 * <p>Two problems are solved here.
 *
 * <p><b>There was no order registry at all.</b> {@code Admin.receiveOrder} handed the order
 * straight to a chef's queue and kept no reference. The admin "orders report" therefore had to
 * reconstruct history by walking every customer, an order a chef rejected was unreachable from
 * anywhere, and completing an order cleared the customer's {@code currentOrder} field so it
 * vanished from the report it had just appeared in.
 *
 * <p><b>Order numbers came from a {@code static int nextId} on {@code Order}.</b> A static counter
 * is shared by the whole JVM and was never reset, so numbers carried over between Cucumber
 * scenarios and no test could assert on a specific one without depending on execution order. The
 * sequence now belongs to the repository instance, so a fresh repository starts again at 1 and
 * every scenario is isolated.
 */
public class OrderRepository extends InMemoryRepository<Order> {

    private final AtomicInteger sequence = new AtomicInteger(0);

    /** @return the next customer-facing order number, starting at 1 */
    public int nextOrderNumber() {
        return sequence.incrementAndGet();
    }

    public Optional<Order> findByOrderNumber(int orderNumber) {
        return findAll().stream()
                .filter(order -> order.getOrderNumber() == orderNumber)
                .findFirst();
    }

    public List<Order> findByCustomer(Customer customer) {
        return findAll().stream()
                .filter(order -> order.getCustomer().equals(customer))
                .toList();
    }

    /** Completed orders for a customer, newest first - the "order history" view. */
    public List<Order> findHistory(Customer customer) {
        return findByCustomer(customer).stream()
                .filter(Order::isCompleted)
                .sorted(Comparator.comparing(Order::getPlacedAt).reversed())
                .toList();
    }

    /** Orders for a customer that are still going through the kitchen. */
    public List<Order> findActiveFor(Customer customer) {
        return findByCustomer(customer).stream()
                .filter(order -> order.getStatus().isActive())
                .toList();
    }

    public List<Order> findByStatus(OrderStatus status) {
        return findAll().stream()
                .filter(order -> order.getStatus() == status)
                .toList();
    }

    /**
     * A chef's work queue: everything assigned to them that is not finished, oldest first.
     *
     * <p>Replaces the {@code Queue<Order>} that used to live on {@code Chef} and was handed out by
     * reference, letting any caller mutate it directly. Deriving the queue from stored orders means
     * there is one source of truth, and an order pulled off the queue for review is no longer lost
     * when the chef rejects it.
     */
    public List<Order> findQueueFor(Chef chef) {
        return findByChefId(chef.getId()).stream()
                .filter(order -> order.getStatus().isActive())
                .sorted(Comparator.comparingInt(Order::getOrderNumber))
                .toList();
    }

    public List<Order> findByChefId(UUID chefId) {
        return findAll().stream()
                .filter(order -> order.getAssignedChefId().map(chefId::equals).orElse(false))
                .toList();
    }

    /** @return how many unfinished orders a chef currently holds */
    public int activeLoadOf(Chef chef) {
        return findQueueFor(chef).size();
    }

    @Override
    public void clear() {
        super.clear();
        sequence.set(0);
    }
}
