package com.cookmgmt.service;

import com.cookmgmt.domain.Customer;
import com.cookmgmt.repository.CustomerRepository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Customer accounts and profiles.
 *
 * <p>Registration goes through {@link CustomerRepository}, which rejects an email already in use -
 * previously nothing did, so a second account on the same address could be created and then never
 * logged into, because the lookup always returned the first match.
 */
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = Objects.requireNonNull(customers, "customers");
    }

    /**
     * @throws com.cookmgmt.domain.exception.DuplicateEmailException if the email is already taken
     */
    public Customer register(String name, String email,
                             Collection<String> dietaryPreferences,
                             Collection<String> allergies) {
        return customers.save(new Customer(name, email, dietaryPreferences, allergies));
    }

    public Customer register(Customer customer) {
        return customers.save(customer);
    }

    /** Case-insensitive login by email. */
    public Optional<Customer> login(String email) {
        return customers.findByEmail(email);
    }

    public Customer updateProfile(Customer customer,
                                  String newName,
                                  String newEmail,
                                  Collection<String> newPreferences,
                                  Collection<String> newAllergies) {
        if (newName != null && !newName.isBlank()) {
            customer.setName(newName);
        }
        if (newEmail != null && !newEmail.isBlank()) {
            customer.setEmail(newEmail);
        }
        if (newPreferences != null) {
            customer.setDietaryPreferences(newPreferences);
        }
        if (newAllergies != null) {
            customer.setAllergies(newAllergies);
        }
        return customers.save(customer);
    }

    public boolean remove(Customer customer) {
        return customers.delete(customer);
    }

    public List<Customer> allCustomers() {
        return customers.findAll();
    }

    public boolean emailInUse(String email) {
        return customers.emailInUse(email);
    }

    public CustomerRepository repository() {
        return customers;
    }
}
