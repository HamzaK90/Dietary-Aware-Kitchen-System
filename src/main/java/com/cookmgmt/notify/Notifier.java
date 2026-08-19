package com.cookmgmt.notify;

/**
 * Delivers a message to someone - a low-stock warning to the kitchen, an approval request to a
 * chef, an invoice to a customer.
 *
 * <p>Services depend on this interface rather than calling {@code System.out.println} directly.
 * That was the single biggest obstacle to testing the old code: business rules and their reporting
 * were fused, so verifying that a low-stock warning fired meant capturing standard output, and the
 * planned GUI had no way to surface any of these messages at all.
 *
 * <p>The console supplies {@link ConsoleNotifier}, the GUI supplies its own, and tests supply
 * {@link InMemoryNotifier} and simply assert on the list.
 */
public interface Notifier {

    /**
     * @param recipient who the message is for - an email address, a name, or
     *                  {@link #EVERYONE} for a general announcement
     * @param message   the text to deliver
     */
    void notify(String recipient, String message);

    /** Recipient value meaning "not addressed to one person". */
    String EVERYONE = "*";

    default void broadcast(String message) {
        notify(EVERYONE, message);
    }
}
