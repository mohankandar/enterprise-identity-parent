package com.documentplatform.identity.admission;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.INVALID_ROLE_GRANT;
import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.NOT_ENABLED;
import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.NO_VERIFIED_MEMBERSHIP;

/**
 * Per-request guard for the shared-issuer mode: the mode must be explicitly on with persisted connections, every
 * verified membership must be backed by an ACTIVE candidate, and no membership may carry an empty role set or the
 * product's platform role. A configuration guard only: it does not prove the schema change is applied.
 */
public final class SharedIssuerPreflight {

    private final EntityRoleCatalog roles;

    public SharedIssuerPreflight(EntityRoleCatalog roles) {
        this.roles = Objects.requireNonNull(roles, "roles");
    }

    public void requireReady(boolean persistedConnections, boolean explicitlyEnabled,
                             Set<EntityId> activeCandidates,
                             Map<EntityId, Set<String>> verifiedMemberships) {
        if (!persistedConnections || !explicitlyEnabled) {
            throw new IdentityAdmissionException(NOT_ENABLED, "Shared-issuer tenant selection is not enabled");
        }
        if (activeCandidates == null || activeCandidates.isEmpty()
                || verifiedMemberships == null || verifiedMemberships.isEmpty()) {
            throw new IdentityAdmissionException(NO_VERIFIED_MEMBERSHIP, "No verified ACTIVE tenant membership is available");
        }
        if (!activeCandidates.containsAll(verifiedMemberships.keySet())) {
            throw new IdentityAdmissionException(NO_VERIFIED_MEMBERSHIP, "Tenant membership is not backed by ACTIVE identity connections");
        }
        for (Set<String> granted : verifiedMemberships.values()) {
            if (granted == null || granted.isEmpty() || granted.stream().anyMatch(roles::isPlatformRole)) {
                throw new IdentityAdmissionException(INVALID_ROLE_GRANT, "Invalid tenant-scoped role grant");
            }
        }
    }
}
