package com.cookmgmt.repository;

import com.cookmgmt.domain.Person;
import com.cookmgmt.domain.exception.DuplicateEmailException;
import com.cookmgmt.support.Text;

import java.util.Optional;

/**
 * Repository for people, where the email address is the login key and must therefore be unique.
 *
 * <p>Nothing previously enforced that. Two customers could register the same address and the login
 * lookup - a {@code stream().filter(...).findFirst()} - simply returned whichever was stored first,
 * so the second person could never sign in and had no way to find out why.
 *
 * @param <T> the person subtype stored
 */
public abstract class PersonRepository<T extends Person> extends InMemoryRepository<T> {

    /**
     * {@inheritDoc}
     *
     * @throws DuplicateEmailException if a <em>different</em> person already uses this email
     */
    @Override
    public T save(T person) {
        findByEmail(person.getEmail())
                .filter(existing -> !existing.getId().equals(person.getId()))
                .ifPresent(existing -> {
                    throw new DuplicateEmailException(person.getEmail());
                });
        return super.save(person);
    }

    /** Case-insensitive lookup; emails are stored normalised. */
    public Optional<T> findByEmail(String email) {
        String key = Text.normalize(email);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(person -> person.getEmail().equals(key))
                .findFirst();
    }

    public boolean emailInUse(String email) {
        return findByEmail(email).isPresent();
    }
}
