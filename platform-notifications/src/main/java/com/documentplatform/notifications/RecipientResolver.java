package com.documentplatform.notifications;

import java.util.Set;

/**
 * Supplied by the app: who holds a role. Recipients are roles, not named people, so this is asked at send time and
 * picks up changes in who holds the role.
 */
public interface RecipientResolver {

    /**
     * @param entity the event's entity ("Reviewers of entity X"), or null for a tenant-level role
     * @return the people holding the role; empty if none
     */
    Set<Recipient> resolve(TenantId tenant, EntityId entity, RoleId role);
}
