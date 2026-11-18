package com.documentplatform.identity.admission;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.INVALID_SELECTION;

/**
 * Applies a request-scoped entity choice. The choice is a request, never proof: the chosen entity must be in the
 * verified membership map. With no choice, exactly one entity must be admissible.
 */
public final class EntitySelection {

    private final TrustedEntityAdmission admission = new TrustedEntityAdmission();

    public EntityId select(String requestedEntity, Map<EntityId, Set<String>> memberships) {
        Set<EntityId> authorized = memberships == null ? null : memberships.keySet();
        if (requestedEntity == null || requestedEntity.isBlank()) {
            return admission.requireUnambiguous(authorized);
        }
        EntityId selected;
        try {
            selected = EntityId.of(UUID.fromString(requestedEntity.trim()));
        } catch (IllegalArgumentException invalid) {
            throw new IdentityAdmissionException(INVALID_SELECTION, "Invalid selected tenant identifier");
        }
        return admission.requireAuthorized(selected, authorized);
    }
}
