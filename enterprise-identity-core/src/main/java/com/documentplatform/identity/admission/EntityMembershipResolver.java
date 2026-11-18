package com.documentplatform.identity.admission;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.CONTEXT_UNAVAILABLE;
import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.POLICY_UNAVAILABLE;
import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.UNKNOWN_ROLE;

/**
 * Finds the entities a verified identity may enter, and its roles in each: an entity is admitted only when it has
 * an ACTIVE connection for this login system AND one of the identity's verified groups is granted in that entity.
 * Roles in one entity never come from another entity's grants.
 *
 * <p>The caller must pass the issuer and the groups from a cryptographically verified token, never from the request.
 *
 * <p>A matched grant that names a role the product does not know refuses only the entity it belongs to: the
 * identity's other entities are still admitted, and the fault is logged as an error. A grant of the platform role,
 * or any other invalid policy, still refuses the whole sign-in.
 */
public final class EntityMembershipResolver {

    private static final System.Logger LOG = System.getLogger(EntityMembershipResolver.class.getName());

    private final EntityGroupGrants grants;
    private final ActiveEntityCandidates candidates;
    private final EntityGroupAuthorization authorization;
    private final TrustedEntityAdmission admission = new TrustedEntityAdmission();
    private final String providerId;
    private final String audience;

    public EntityMembershipResolver(EntityGroupGrants grants, ActiveEntityCandidates candidates,
                                    EntityRoleCatalog roles, AdmissionSettings settings) {
        this.grants = Objects.requireNonNull(grants);
        this.candidates = Objects.requireNonNull(candidates);
        this.authorization = new EntityGroupAuthorization(Objects.requireNonNull(roles));
        this.providerId = Objects.requireNonNull(settings).providerId();
        this.audience = settings.audience();
    }

    public Map<EntityId, Set<String>> authorizedEntities(URI verifiedIssuer, Object authenticatedGroups) {
        if (verifiedIssuer == null || audience == null || audience.isBlank()) {
            throw new IdentityAdmissionException(CONTEXT_UNAVAILABLE, "No verified issuer, or no audience configured");
        }
        ConnectionKey key = new ConnectionKey(providerId, verifiedIssuer, audience);
        Map<EntityId, Map<String, Set<String>>> policies = grants.activeGrants(key);
        Set<EntityId> activeCandidates = candidates.find(key);
        if (policies == null || activeCandidates == null) {
            throw new IdentityAdmissionException(POLICY_UNAVAILABLE,
                    "The grants or the ACTIVE entities could not be read");
        }
        // Only an entity backed by both an ACTIVE connection and a matching verified group is admissible.
        Map<EntityId, Set<String>> authorized = new LinkedHashMap<>();
        for (EntityId entity : policies.keySet()) {
            if (!activeCandidates.contains(entity)) continue;
            // Absence of a matching group means no membership. An invalid policy is a hard failure.
            if (authorization.hasMatchingGroup(authenticatedGroups, policies.get(entity))) {
                try {
                    authorized.put(entity, authorization.authorizedRoles(entity, authenticatedGroups, policies));
                } catch (IdentityAdmissionException refused) {
                    if (refused.reason() != UNKNOWN_ROLE) throw refused;
                    LOG.log(System.Logger.Level.ERROR,
                            "Entity {0} is refused for this sign-in: a matched group grant names a role the product "
                                    + "does not know. Correct the entity''s grants.", entity);
                }
            }
        }
        return Map.copyOf(authorized);
    }

    public EntityId select(EntityId requestedEntity, Map<EntityId, Set<String>> authorizedEntities) {
        return admission.requireAuthorized(requestedEntity, authorizedEntities == null ? null : authorizedEntities.keySet());
    }

    public EntityId selectOnlyIfUnambiguous(Map<EntityId, Set<String>> authorizedEntities) {
        return admission.requireUnambiguous(authorizedEntities == null ? null : authorizedEntities.keySet());
    }
}
