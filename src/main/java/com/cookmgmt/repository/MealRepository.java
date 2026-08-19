package com.cookmgmt.repository;

import com.cookmgmt.domain.Meal;
import com.cookmgmt.support.Text;

import java.util.List;
import java.util.Optional;

/** Stores the menu. */
public class MealRepository extends InMemoryRepository<Meal> {

    /** Case-insensitive lookup by dish name. */
    public Optional<Meal> findByName(String name) {
        String key = Text.normalize(name);
        return findAll().stream()
                .filter(meal -> Text.normalize(meal.getName()).equals(key))
                .findFirst();
    }

    /** @return every meal whose recipe uses the given ingredient */
    public List<Meal> findUsing(String ingredient) {
        return findAll().stream()
                .filter(meal -> meal.contains(ingredient))
                .toList();
    }
}
