package com.documentplatform.identity.admission;

import java.util.Set;

/**
 * Implemented by the product: lists the entities that have an ACTIVE login-system connection for this key.
 *
 * <p>The product must guarantee:
 * <ul>
 *   <li>every entity returned has an ACTIVE connection matching provider id, issuer and audience exactly, and is
 *       itself ACTIVE in the product;</li>
 *   <li>an empty set for a blank provider id or audience;</li>
 *   <li>{@code null} only when the store could not be read (the library then refuses).</li>
 * </ul>
 * Candidates are not memberships: the library admits an entity only if it is also matched by a verified group grant.
 */
@FunctionalInterface
public interface ActiveEntityCandidates {
    Set<EntityId> find(ConnectionKey key);
}
