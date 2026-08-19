package com.cookmgmt.domain.exception;

/**
 * Raised when registering a person with an email address already in use.
 *
 * <p>Email is the login key for both customers and chefs, but nothing previously enforced
 * uniqueness. Two customers could share an address and the lookup silently returned whichever
 * happened to be stored first.
 */
public class DuplicateEmailException extends RuntimeException {

    private final String email;

    public DuplicateEmailException(String email) {
        super("An account already exists for email \"" + email + "\"");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
