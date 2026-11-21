package com.documentplatform.notifications.inmemory;

import com.documentplatform.notifications.EntityId;
import com.documentplatform.notifications.EventSettings;
import com.documentplatform.notifications.EventTypeId;
import com.documentplatform.notifications.SettingsStore;
import com.documentplatform.notifications.TenantId;
import com.documentplatform.notifications.TenantSettings;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Settings kept in memory. Stores whatever it is given; the rules are checked by {@code NotificationSettings}. */
public final class InMemorySettingsStore implements SettingsStore {

    private record EntityKey(TenantId tenant, EntityId entity, EventTypeId type) {
    }

    private record TenantKey(TenantId tenant, EventTypeId type) {
    }

    private final Map<TenantId, TenantSettings> tenants = new ConcurrentHashMap<>();
    private final Map<TenantKey, EventSettings> technical = new ConcurrentHashMap<>();
    private final Map<EntityKey, EventSettings> business = new ConcurrentHashMap<>();

    @Override
    public Optional<TenantSettings> tenant(TenantId tenant) {
        return Optional.ofNullable(tenants.get(tenant));
    }

    @Override
    public void saveTenant(TenantId tenant, TenantSettings settings) {
        tenants.put(tenant, settings);
    }

    @Override
    public Optional<EventSettings> technical(TenantId tenant, EventTypeId type) {
        return Optional.ofNullable(technical.get(new TenantKey(tenant, type)));
    }

    @Override
    public void saveTechnical(TenantId tenant, EventTypeId type, EventSettings settings) {
        technical.put(new TenantKey(tenant, type), settings);
    }

    @Override
    public Optional<EventSettings> business(TenantId tenant, EntityId entity, EventTypeId type) {
        return Optional.ofNullable(business.get(new EntityKey(tenant, entity, type)));
    }

    @Override
    public void saveBusiness(TenantId tenant, EntityId entity, EventTypeId type, EventSettings settings) {
        business.put(new EntityKey(tenant, entity, type), settings);
    }
}
