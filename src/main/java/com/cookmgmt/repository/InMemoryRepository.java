package com.cookmgmt.repository;

import com.cookmgmt.domain.Entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link Repository} backed by a map held in memory. The project has no database by design.
 *
 * <p>A {@link LinkedHashMap} is used rather than a {@code HashMap} so listings, reports and
 * menu numbering come out in a stable, predictable order. The original code iterated
 * {@code HashMap} key sets when suggesting ingredient substitutions and picked
 * {@code alternatives.get(0)}, which meant the suggestion offered to the customer could differ
 * between two runs of the same scenario.
 *
 * @param <T> the entity type stored
 */
public class InMemoryRepository<T extends Entity> implements Repository<T> {

    private final Map<UUID, T> entities = new LinkedHashMap<>();

    @Override
    public T save(T entity) {
        Objects.requireNonNull(entity, "entity");
        entities.put(entity.getId(), entity);
        return entity;
    }

    @Override
    public Optional<T> findById(UUID id) {
        return id == null ? Optional.empty() : Optional.ofNullable(entities.get(id));
    }

    @Override
    public List<T> findAll() {
        return List.copyOf(entities.values());
    }

    @Override
    public boolean deleteById(UUID id) {
        return id != null && entities.remove(id) != null;
    }

    @Override
    public boolean existsById(UUID id) {
        return id != null && entities.containsKey(id);
    }

    @Override
    public int count() {
        return entities.size();
    }

    @Override
    public void clear() {
        entities.clear();
    }
}
