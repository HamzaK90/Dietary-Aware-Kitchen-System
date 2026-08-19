package com.cookmgmt.notify;

import java.time.Instant;

/**
 * A single delivered message, kept so it can be asserted on in tests and listed in the GUI.
 *
 * @param recipient who it was addressed to
 * @param message   the text
 * @param at        when it was raised
 */
public record Notification(String recipient, String message, Instant at) {

    public static Notification of(String recipient, String message) {
        return new Notification(recipient, message, Instant.now());
    }

    public boolean isBroadcast() {
        return Notifier.EVERYONE.equals(recipient);
    }

    @Override
    public String toString() {
        return isBroadcast() ? message : "[" + recipient + "] " + message;
    }
}
