package com.cookmgmt.domain;

import com.cookmgmt.support.Text;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A customer, with the dietary preferences and allergies that drive conflict detection.
 *
 * <p>Preferences and allergies are stored normalised and de-duplicated, so {@code "Vegan"},
 * {@code "vegan"} and a stray {@code " Vegan"} left over from splitting comma-separated input are
 * one entry that matches reliably.
 *
 * <p>This class no longer holds order history or a "current order". Orders live in
 * {@link com.cookmgmt.repository.OrderRepository}, which is the single source of truth; the old
 * arrangement kept a partial copy on the customer and a second copy in a chef's queue, and the two
 * regularly disagreed - completing an order cleared the customer's current order, after which the
 * admin report showed "None" for an order that had just been served.
 */
public class Customer extends Person {

    private final Set<String> dietaryPreferences = new LinkedHashSet<>();
    private final Set<String> allergies = new LinkedHashSet<>();

    public Customer(String name, String email) {
        super(name, email);
    }

    public Customer(String name, String email,
                    Collection<String> dietaryPreferences,
                    Collection<String> allergies) {
        super(name, email);
        setDietaryPreferences(dietaryPreferences);
        setAllergies(allergies);
    }

    private static Set<String> normalizedSet(Collection<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String item = Text.normalize(value);
                if (!item.isEmpty()) {
                    normalized.add(item);
                }
            }
        }
        return normalized;
    }

    /** @return normalised dietary preferences, for example {@code ["vegan", "gluten-free"]} */
    public Set<String> getDietaryPreferences() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(dietaryPreferences));
    }

    public void setDietaryPreferences(Collection<String> preferences) {
        this.dietaryPreferences.clear();
        this.dietaryPreferences.addAll(normalizedSet(preferences));
    }

    public void addDietaryPreference(String preference) {
        String value = Text.normalize(preference);
        if (!value.isEmpty()) {
            dietaryPreferences.add(value);
        }
    }

    public void removeDietaryPreference(String preference) {
        dietaryPreferences.remove(Text.normalize(preference));
    }

    public boolean prefers(String preference) {
        return dietaryPreferences.contains(Text.normalize(preference));
    }

    /** @return normalised allergy list, for example {@code ["milk", "nuts"]} */
    public Set<String> getAllergies() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(allergies));
    }

    public void setAllergies(Collection<String> allergies) {
        this.allergies.clear();
        this.allergies.addAll(normalizedSet(allergies));
    }

    public void addAllergy(String allergy) {
        String value = Text.normalize(allergy);
        if (!value.isEmpty()) {
            allergies.add(value);
        }
    }

    public void removeAllergy(String allergy) {
        allergies.remove(Text.normalize(allergy));
    }

    public boolean isAllergicTo(String ingredient) {
        return allergies.contains(Text.normalize(ingredient));
    }

    /** Convenience for the console and GUI, which collect these as comma-separated text. */
    public void setDietaryPreferencesFromCsv(String csv) {
        setDietaryPreferences(Text.parseCsv(csv));
    }

    public void setAllergiesFromCsv(String csv) {
        setAllergies(Text.parseCsv(csv));
    }

    public List<String> dietaryPreferencesAsList() {
        return List.copyOf(dietaryPreferences);
    }

    public List<String> allergiesAsList() {
        return List.copyOf(allergies);
    }
}
