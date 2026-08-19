package com.cookmgmt.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Base class for domain entities, giving each one a stable identity.
 *
 * <p>The previous design compared entities with the default {@code Object.equals}, so
 * {@code List.indexOf} in the old {@code Admin} class only worked when the caller happened to
 * pass back the exact same reference. Identity now lives in one place and is independent of
 * mutable fields such as name or email.
 */
public abstract class Entity {

    private final UUID id;

    protected Entity() {
        this(UUID.randomUUID());
    }

    protected Entity(UUID id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public UUID getId() {
        return id;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Entity entity)) {
            return false;
        }
        // Entities of different types never share an identity, so the class must match too.
        return getClass() == other.getClass() && id.equals(entity.id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }
}
