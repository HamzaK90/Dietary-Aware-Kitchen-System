package com.cookmgmt.domain;

import com.cookmgmt.support.Text;

/**
 * Shared identity of anyone the system knows about: a display name and an email address, which is
 * also the login key.
 */
public abstract class Person extends Entity {

    private String name;
    private String email;

    protected Person(String name, String email) {
        this.name = Text.requireText(name, "Name");
        this.email = normalizedEmail(email);
    }

    private static String normalizedEmail(String email) {
        String trimmed = Text.requireText(email, "Email");
        if (!trimmed.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("\"" + trimmed + "\" is not a valid email address");
        }
        // Stored lower-cased so lookups are consistently case-insensitive.
        return Text.normalize(trimmed);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Text.requireText(name, "Name");
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalizedEmail(email);
    }

    @Override
    public String toString() {
        return name + " <" + email + ">";
    }
}
