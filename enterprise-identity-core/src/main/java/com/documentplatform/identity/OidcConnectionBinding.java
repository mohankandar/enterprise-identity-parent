package com.documentplatform.identity;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

/**
 * Product-neutral, secret-free view of an ACTIVE OIDC connection used at authentication time.
 * Product persistence owns the full configuration and audit history.
 */
public record OidcConnectionBinding(
        UUID connectionId,
        UUID tenantId,
        String providerId,
        URI issuer,
        String audience,
        long configurationVersion) {

    public OidcConnectionBinding {
        Objects.requireNonNull(connectionId, "connectionId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(issuer, "issuer");
        providerId = requireText(providerId, "providerId");
        audience = requireText(audience, "audience");
        if (!issuer.isAbsolute() || issuer.getHost() == null) {
            throw new IllegalArgumentException("issuer must be an absolute URI");
        }
        if (configurationVersion < 1) throw new IllegalArgumentException("configurationVersion must be positive");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
