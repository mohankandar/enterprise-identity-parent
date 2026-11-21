package com.documentplatform.identity.admission;

import java.util.Objects;
import java.util.UUID;

/**
 * An entity (an organization a person signs in to) as the library sees it: the product's own entity or tenant id.
 * The product converts to and from its own id type at the seam.
 */
public record EntityId(UUID value) {
    public EntityId {
        Objects.requireNonNull(value, "EntityId value must not be null");
    }

    public static EntityId of(UUID value) {
        return new EntityId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
