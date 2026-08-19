package com.cookmgmt.repository;

import com.cookmgmt.domain.Entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage for one kind of entity.
 *
 * <p>The old {@code Admin} class owned four raw {@code ArrayList} fields directly and exposed
 * hand-written add/remove/update methods for each. Because it created its own collections, nothing
 * could substitute a different store, and its update methods relied on {@code List.indexOf} - which
 * uses {@code equals}, which none of the entities overrode, so an update only worked if the caller
 * passed back the identical object reference.
 *
 * <p>Services now depend on this interface (Dependency Inversion): they state what they need
 * storage to do, and the in-memory implementation satisfies it. Swapping in a database-backed
 * implementation later would not require touching a single service.
 *
 * @param <T> the entity type stored
 */
public interface Repository<T extends Entity> {

    /** Inserts or updates by identity. @return the stored entity */
    T save(T entity);

    Optional<T> findById(UUID id);

    /** @return every entity, in insertion order */
    List<T> findAll();

    boolean deleteById(UUID id);

    default boolean delete(T entity) {
        return entity != null && deleteById(entity.getId());
    }

    boolean existsById(UUID id);

    int count();

    default boolean isEmpty() {
        return count() == 0;
    }

    /** Removes everything. Used to reset state between test scenarios. */
    void clear();
}
