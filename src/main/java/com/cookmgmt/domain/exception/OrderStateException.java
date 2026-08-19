package com.cookmgmt.domain.exception;

import com.cookmgmt.domain.OrderStatus;

/**
 * Raised when an order is asked to make a transition its current status does not allow, such as
 * completing an order that was already rejected.
 *
 * <p>The old model let any caller assign any status at any time via a public setter, so illegal
 * sequences were possible and went unnoticed.
 */
public class OrderStateException extends RuntimeException {

    public OrderStateException(int orderId, OrderStatus from, OrderStatus to) {
        super("Order #" + orderId + " cannot move from " + from + " to " + to);
    }

    public OrderStateException(String message) {
        super(message);
    }
}
