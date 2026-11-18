package com.documentplatform.identity.admission;

import java.util.Objects;
import java.util.Set;

import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.AMBIGUOUS_SELECTION;
import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.NOT_AUTHORIZED_FOR_ENTITY;

/**
 * The admission rule: a requested entity is admitted only if it is in the server-verified membership set, and
 * without an explicit choice exactly one entity must be admissible. Independent of headers, claims and client state;
 * the caller supplies the verified set.
 */
public final class TrustedEntityAdmission {

    public EntityId requireAuthorized(EntityId requestedEntity, Set<EntityId> trustedMemberships) {
        Objects.requireNonNull(requestedEntity, "requestedTenant");
        if (trustedMemberships == null || !trustedMemberships.contains(requestedEntity)) {
            throw new IdentityAdmissionException(NOT_AUTHORIZED_FOR_ENTITY,
                    "Authenticated identity is not authorized for the requested tenant");
        }
        return requestedEntity;
    }

    /** Selects an entity only when exactly one is authorized; otherwise an explicit, verified choice is required. */
    public EntityId requireUnambiguous(Set<EntityId> trustedMemberships) {
        if (trustedMemberships == null || trustedMemberships.size() != 1) {
            throw new IdentityAdmissionException(AMBIGUOUS_SELECTION, "A unique authorized tenant context is required");
        }
        return trustedMemberships.iterator().next();
    }
}
