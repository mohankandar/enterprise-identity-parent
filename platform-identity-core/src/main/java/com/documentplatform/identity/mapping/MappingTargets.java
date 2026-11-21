package com.documentplatform.identity.mapping;

import com.documentplatform.identity.admission.EntityId;

import java.util.Optional;

/**
 * Implemented by the product: what the product knows about an entity a mapping file names. The library asks; the
 * product answers from its own tables.
 */
@FunctionalInterface
public interface MappingTargets {

    /**
     * @param name                  the entity's name in the product, compared exactly with the file's {@code entity_name}
     * @param active                the entity is ACTIVE in the product
     * @param activeLoginConnection the entity has an ACTIVE login-system connection, so its grants can apply
     */
    record Target(String name, boolean active, boolean activeLoginConnection) {
    }

    /** The entity, or empty when the product has no entity with this id. */
    Optional<Target> describe(EntityId entity);
}
