package com.documentplatform.identity.admission;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Test-only role catalogue. It uses the same role names as the ported product tests so every case maps one to one;
 * the library itself knows no role names.
 */
final class TestRoleCatalog implements EntityRoleCatalog {
    static final String PLATFORM = "PLATFORM_OPERATIONS_ADMIN";
    private static final Set<String> KNOWN = Set.of("DOCUMENT_PROCESSOR", "DOCUMENT_SUPERVISOR", "BUSINESS_ADMIN",
            "REPORT_VIEWER", "TENANT_IT_ADMIN", PLATFORM);

    @Override
    public Optional<String> canonicalEntityRole(String configuredName) {
        if (configuredName == null || configuredName.isBlank()) return Optional.empty();
        String normalized = configuredName.trim().toUpperCase(Locale.ROOT);
        return KNOWN.contains(normalized) ? Optional.of(normalized) : Optional.empty();
    }

    @Override
    public boolean isPlatformRole(String canonicalRole) {
        return PLATFORM.equals(canonicalRole);
    }
}
