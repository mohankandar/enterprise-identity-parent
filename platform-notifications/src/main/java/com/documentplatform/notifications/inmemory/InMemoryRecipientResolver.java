package com.documentplatform.notifications.inmemory;

import com.documentplatform.notifications.EntityId;
import com.documentplatform.notifications.Recipient;
import com.documentplatform.notifications.RecipientResolver;
import com.documentplatform.notifications.RoleId;
import com.documentplatform.notifications.TenantId;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Role holders kept in memory. A null entity means a tenant-level role. */
public final class InMemoryRecipientResolver implements RecipientResolver {

    private record Key(TenantId tenant, EntityId entity, RoleId role) {
    }

    private final Map<Key, Set<Recipient>> holders = new HashMap<>();

    public synchronized InMemoryRecipientResolver grant(TenantId tenant, EntityId entity, RoleId role,
                                                        Recipient... people) {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(role, "role");
        holders.computeIfAbsent(new Key(tenant, entity, role), k -> new LinkedHashSet<>()).addAll(List.of(people));
        return this;
    }

    @Override
    public synchronized Set<Recipient> resolve(TenantId tenant, EntityId entity, RoleId role) {
        return Set.copyOf(holders.getOrDefault(new Key(tenant, entity, role), Set.of()));
    }
}
