package com.cookmgmt.notify;

import java.io.PrintStream;

/** Prints notifications to a stream. Used by the console interface. */
public class ConsoleNotifier implements Notifier {

    private final PrintStream out;

    public ConsoleNotifier() {
        this(System.out);
    }

    public ConsoleNotifier(PrintStream out) {
        this.out = out;
    }

    @Override
    public void notify(String recipient, String message) {
        out.println(Notification.of(recipient, message));
    }
}
