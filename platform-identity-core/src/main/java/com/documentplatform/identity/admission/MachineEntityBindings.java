package com.documentplatform.identity.admission;

import java.util.Set;

/**
 * Implemented by the product: the entities whose enabled machine integration is bound to this client principal
 * on this connection.
 *
 * <p>The product must guarantee:
 * <ul>
 *   <li>only entities with an ACTIVE connection for this key, an ACTIVE entity, and an enabled integration whose
 *       authentication proves a bearer-token client (the product decides which integration kinds qualify);</li>
 *   <li>an empty set for a blank provider id, audience or client principal.</li>
 * </ul>
 * The library admits a machine identity only when exactly one entity is returned. Human entity choices are never
 * used for machines.
 */
@FunctionalInterface
public interface MachineEntityBindings {
    Set<EntityId> find(ConnectionKey key, String clientPrincipal);
}
