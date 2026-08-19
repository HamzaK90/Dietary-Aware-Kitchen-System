package com.cookmgmt.notify;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps notifications in a list instead of printing them.
 *
 * <p>Serves two purposes: it is the test double that lets a scenario assert "the chef was told
 * about the substitution" without capturing standard output, and it backs the GUI's notification
 * panel.
 */
public class InMemoryNotifier implements Notifier {

    private final List<Notification> notifications = new ArrayList<>();

    @Override
    public void notify(String recipient, String message) {
        notifications.add(Notification.of(recipient, message));
    }

    public List<Notification> all() {
        return List.copyOf(notifications);
    }

    /** Notifications addressed to one recipient, plus every broadcast. */
    public List<Notification> forRecipient(String recipient) {
        return notifications.stream()
                .filter(n -> n.isBroadcast() || n.recipient().equalsIgnoreCase(recipient))
                .toList();
    }

    /** @return {@code true} if any message contains {@code fragment}, ignoring case */
    public boolean anyContaining(String fragment) {
        String needle = fragment.toLowerCase();
        return notifications.stream()
                .anyMatch(n -> n.message().toLowerCase().contains(needle));
    }

    public int size() {
        return notifications.size();
    }

    public void clear() {
        notifications.clear();
    }
}
