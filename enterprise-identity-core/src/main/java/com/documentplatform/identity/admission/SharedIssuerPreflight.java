package com.documentplatform.identity.admission;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.INVALID_ROLE_GRANT;
import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.MEMBERSHIP_NOT_BACKED;
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
            throw new IdentityAdmissionException(NOT_ENABLED, "Several entities behind one login system is not switched on");
        }
        if (activeCandidates == null || activeCandidates.isEmpty()
                || verifiedMemberships == null || verifiedMemberships.isEmpty()) {
            throw new IdentityAdmissionException(NO_VERIFIED_MEMBERSHIP, "The verified identity has no admissible entity");
        }
        if (!activeCandidates.containsAll(verifiedMemberships.keySet())) {
            throw new IdentityAdmissionException(MEMBERSHIP_NOT_BACKED, "A membership is not backed by an ACTIVE connection");
        }
        for (Set<String> granted : verifiedMemberships.values()) {
            if (granted == null || granted.isEmpty() || granted.stream().anyMatch(roles::isPlatformRole)) {
                throw new IdentityAdmissionException(INVALID_ROLE_GRANT, "A membership has no role or holds the platform role");
            }
        }
    }
}
