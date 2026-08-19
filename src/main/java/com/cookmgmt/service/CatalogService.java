package com.cookmgmt.service;

import com.cookmgmt.domain.Meal;
import com.cookmgmt.domain.Money;
import com.cookmgmt.repository.MealRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Managing the menu.
 *
 * <p>Edits go through {@link MealRepository#save} keyed on the meal's identity. The old
 * {@code Admin.editMeal} used {@code List.indexOf} to find the meal to replace, which relies on
 * {@code equals} - and {@code Meal} never overrode it, so the lookup was a reference comparison
 * that only worked by accident. In practice the console bypassed the method entirely and mutated
 * the object it had been handed by {@code getMeals()}, which was supposed to be a defensive copy;
 * because the copy was shallow, the mutation reached the stored meal anyway. Two mechanisms, both
 * relying on the wrong thing.
 */
public class CatalogService {

    private final MealRepository meals;
    private final PricingService pricingService;

    public CatalogService(MealRepository meals, PricingService pricingService) {
        this.meals = Objects.requireNonNull(meals, "meals");
        this.pricingService = Objects.requireNonNull(pricingService, "pricingService");
    }

    public Meal addMeal(Meal meal) {
        return meals.save(meal);
    }

    public Meal addMeal(String name, Map<String, Integer> ingredients, int cookingTimeMinutes) {
        return meals.save(Meal.of(name, ingredients, cookingTimeMinutes));
    }

    /** Applies changes to an existing meal and stores it. Null arguments leave a field alone. */
    public Meal updateMeal(Meal meal,
                           String newName,
                           Map<String, Integer> newIngredients,
                           Integer newCookingTime) {
        if (newName != null && !newName.isBlank()) {
            meal.setName(newName);
        }
        if (newIngredients != null && !newIngredients.isEmpty()) {
            // Empty is rejected rather than accepted: the old edit screen started with an empty
            // ingredient map and wrote it back if the user typed "done" straight away, silently
            // leaving a meal on the menu with no recipe at all.
            meal.setIngredients(newIngredients);
        }
        if (newCookingTime != null) {
            meal.setCookingTimeMinutes(newCookingTime);
        }
        return meals.save(meal);
    }

    public boolean removeMeal(Meal meal) {
        return meals.delete(meal);
    }

    public List<Meal> allMeals() {
        return meals.findAll();
    }

    public Optional<Meal> findByName(String name) {
        return meals.findByName(name);
    }

    public Optional<Meal> findById(UUID id) {
        return meals.findById(id);
    }

    /** Menu price of a meal as written, used by the listing screens. */
    public Money priceOf(Meal meal) {
        return pricingService.basePriceOf(meal);
    }

    public MealRepository repository() {
        return meals;
    }
}
