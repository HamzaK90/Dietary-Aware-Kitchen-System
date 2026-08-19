package com.cookmgmt.service;

import com.cookmgmt.domain.Chef;
import com.cookmgmt.repository.ChefRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Chef accounts.
 *
 * <p>Editing a chef now mutates and re-saves the same entity. The old {@code Admin.updateChef}
 * replaced the stored object with a brand new {@code Chef}, which silently discarded the work queue
 * the previous instance was holding - renaming a chef lost their orders.
 */
public class StaffService {

    private final ChefRepository chefs;

    public StaffService(ChefRepository chefs) {
        this.chefs = Objects.requireNonNull(chefs, "chefs");
    }

    /**
     * @throws com.cookmgmt.domain.exception.DuplicateEmailException if the email is already taken
     */
    public Chef hire(String name, String email) {
        return chefs.save(new Chef(name, email));
    }

    public Chef hire(Chef chef) {
        return chefs.save(chef);
    }

    public Optional<Chef> login(String email) {
        return chefs.findByEmail(email);
    }

    public Chef updateProfile(Chef chef, String newName, String newEmail) {
        if (newName != null && !newName.isBlank()) {
            chef.setName(newName);
        }
        if (newEmail != null && !newEmail.isBlank()) {
            chef.setEmail(newEmail);
        }
        return chefs.save(chef);
    }

    public boolean remove(Chef chef) {
        return chefs.delete(chef);
    }

    public List<Chef> allChefs() {
        return chefs.findAll();
    }

    public ChefRepository repository() {
        return chefs;
    }
}
