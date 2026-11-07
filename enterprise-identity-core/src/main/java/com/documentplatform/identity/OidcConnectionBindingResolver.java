package com.documentplatform.identity;

import java.net.URI;
import java.util.Optional;

/**
 * Resolves a trusted ACTIVE OIDC connection to its internal tenant. Implementations must fail closed on ambiguity.
 */
@FunctionalInterface
public interface OidcConnectionBindingResolver {
    Optional<OidcConnectionBinding> findActive(String providerId, URI issuer, String audience);
}
