package com.documentplatform.identity.admission;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.INVALID_ROLE_GRANT;
import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.NO_ROLE_FOR_ENTITY;
import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.PLATFORM_ROLE_IN_ENTITY_GRANT;
import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.POLICY_UNAVAILABLE;

/**
 * Turns verified login-system group memberships into roles for one entity, from that entity's grants only:
 * a group granted in another entity never adds a role here. Grants come from server configuration, never the request.
 */
public final class EntityGroupAuthorization {

    private final EntityRoleCatalog roles;

    public EntityGroupAuthorization(EntityRoleCatalog roles) {
        this.roles = Objects.requireNonNull(roles, "roles");
    }

    /** No matching group is ordinary non-membership; an invalid policy is never suppressed. */
    public boolean hasMatchingGroup(Object signedGroups, Map<String, Set<String>> grants) {
        if (!(signedGroups instanceof Collection<?> groups)) {
            throw new IdentityAdmissionException(POLICY_UNAVAILABLE, "Trusted IdP group collection is required");
        }
        if (grants == null || grants.isEmpty()) {
            throw new IdentityAdmissionException(POLICY_UNAVAILABLE, "Tenant has no trusted group admission policy");
        }
        for (Object candidate : groups) {
            if (candidate instanceof String group && grants.containsKey(group.trim())) return true;
        }
        return false;
    }

    public Set<String> authorizedRoles(EntityId requestedEntity, Object signedGroups,
                                       Map<EntityId, Map<String, Set<String>>> mappings) {
        Objects.requireNonNull(requestedEntity, "requestedTenant");
        if (mappings == null || !mappings.containsKey(requestedEntity)) {
            throw new IdentityAdmissionException(POLICY_UNAVAILABLE, "Tenant has no trusted group admission policy");
        }
        Map<String, Set<String>> grants = mappings.get(requestedEntity);
        if (grants == null || grants.isEmpty()) {
            throw new IdentityAdmissionException(POLICY_UNAVAILABLE, "Tenant has no trusted group admission policy");
        }
        Set<String> external = new LinkedHashSet<>();
        if (signedGroups instanceof Collection<?> groups) {
            for (Object group : groups) {
                if (group instanceof String value && !value.isBlank()) external.add(value.trim());
            }
        } else {
            throw new IdentityAdmissionException(POLICY_UNAVAILABLE, "Trusted IdP group collection is required");
        }
        Set<String> authorized = new LinkedHashSet<>();
        for (String group : external) {
            Set<String> granted = grants.get(group);
            if (granted != null) {
                for (String role : granted) {
                    String canonical = roles.canonicalEntityRole(role)
                            .orElseThrow(() -> new IdentityAdmissionException(INVALID_ROLE_GRANT, "Invalid tenant role configuration"));
                    if (roles.isPlatformRole(canonical)) {
                        throw new IdentityAdmissionException(PLATFORM_ROLE_IN_ENTITY_GRANT,
                                "Platform Operations cannot be granted through tenant IdP groups");
                    }
                    authorized.add(canonical);
                }
            }
        }
        if (authorized.isEmpty()) {
            throw new IdentityAdmissionException(NO_ROLE_FOR_ENTITY, "Authenticated identity has no tenant-authorized role");
        }
        return Set.copyOf(authorized);
    }
}
