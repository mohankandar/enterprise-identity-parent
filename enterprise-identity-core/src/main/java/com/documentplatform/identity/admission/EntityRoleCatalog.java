package com.documentplatform.identity.admission;

import java.util.Optional;

/**
 * Implemented by the product: its role names. The library knows no role names of its own.
 *
 * <p>The product must guarantee:
 * <ul>
 *   <li>{@link #canonicalEntityRole} returns the canonical name for a configured role name, or empty for a name the
 *       product does not know (the library then refuses the grant);</li>
 *   <li>{@link #isPlatformRole} is true for any role that reaches platform-wide screens; such a role can never be
 *       granted through an entity's groups.</li>
 * </ul>
 */
public interface EntityRoleCatalog {
    Optional<String> canonicalEntityRole(String configuredName);

    boolean isPlatformRole(String canonicalRole);
}
