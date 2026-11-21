package com.documentplatform.notifications;

import java.util.Objects;
import java.util.Optional;

import static com.documentplatform.notifications.NotificationException.Reason.ENTITY_REQUIRED;
import static com.documentplatform.notifications.NotificationException.Reason.THRESHOLD_OUT_OF_RANGE;
import static com.documentplatform.notifications.NotificationException.Reason.UNKNOWN_DESTINATION;
import static com.documentplatform.notifications.NotificationException.Reason.UNKNOWN_THRESHOLD;
import static com.documentplatform.notifications.NotificationException.Reason.WRONG_SETTINGS_LEVEL;

/**
 * Reads and saves settings, enforcing the rules. Who may call which method (IT Admin, Business Admin) is checked by
 * the app; this class keeps the levels apart: technical events are set per tenant, business events per entity.
 * There is no personal opt-out: only these admin settings decide.
 */
public final class NotificationSettings {

    private final EventCatalogue catalogue;
    private final SettingsStore store;

    public NotificationSettings(EventCatalogue catalogue, SettingsStore store) {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.store = Objects.requireNonNull(store, "store");
    }

    public EventCatalogue catalogue() {
        return catalogue;
    }

    /** IT Admin: connect the tenant's mail server and webhooks, and set the app address and time zone. */
    public void connectChannels(TenantId tenant, TenantSettings settings) {
        store.saveTenant(Objects.requireNonNull(tenant, "tenant"), Objects.requireNonNull(settings, "settings"));
    }

    public Optional<TenantSettings> tenant(TenantId tenant) {
        return store.tenant(tenant);
    }

    /** IT Admin: choose and route a technical event for the whole tenant. */
    public void setTechnicalEvent(TenantId tenant, EventTypeId typeId, EventSettings settings) {
        EventType type = catalogue.require(typeId);
        if (type.kind() != EventKind.TECHNICAL) {
            throw new NotificationException(WRONG_SETTINGS_LEVEL, "Business events are set per entity");
        }
        check(tenant, type, settings);
        store.saveTechnical(tenant, typeId, settings);
    }

    /** Business Admin: choose a business event for one entity. */
    public void setBusinessEvent(TenantId tenant, EntityId entity, EventTypeId typeId, EventSettings settings) {
        EventType type = catalogue.require(typeId);
        if (type.kind() != EventKind.BUSINESS) {
            throw new NotificationException(WRONG_SETTINGS_LEVEL, "Technical events are set at tenant level");
        }
        Objects.requireNonNull(entity, "entity");
        check(tenant, type, settings);
        store.saveBusiness(tenant, entity, typeId, settings);
    }

    /**
     * The settings that apply now: the saved ones, or the event type's defaults when none are saved. Saved settings
     * that break a rule of the current catalogue (the type became critical in a later release, a threshold range
     * narrowed, a destination was disconnected) fall back to the defaults rather than being used.
     *
     * @param entity the event's entity; required for business events, ignored for technical ones
     */
    public EventSettings effective(TenantId tenant, EntityId entity, EventTypeId typeId) {
        EventType type = catalogue.require(typeId);
        Optional<EventSettings> saved;
        if (type.kind() == EventKind.TECHNICAL) {
            saved = store.technical(tenant, typeId);
        } else {
            if (entity == null) {
                throw new NotificationException(ENTITY_REQUIRED, "Business events belong to an entity");
            }
            saved = store.business(tenant, entity, typeId);
        }
        if (saved.isPresent()) {
            try {
                check(tenant, type, saved.get());
                return saved.get();
            } catch (NotificationException brokenByCatalogueChange) {
                // fall through to the defaults
            }
        }
        return EventSettings.defaultsFor(type);
    }

    /** The value the app compares against for one of the event type's declared thresholds. */
    public long threshold(TenantId tenant, EntityId entity, EventTypeId typeId, String name) {
        NumericThreshold declared = catalogue.require(typeId).threshold(name)
                .orElseThrow(() -> new NotificationException(UNKNOWN_THRESHOLD, "Threshold is not declared"));
        Long value = effective(tenant, entity, typeId).thresholds().get(name);
        return value == null ? declared.defaultValue() : value;
    }

    private void check(TenantId tenant, EventType type, EventSettings s) {
        Objects.requireNonNull(s, "settings");
        Rules.check(type, catalogue.itAdminRole(), s.enabled(), s.schedule(), s.recipientRoles(), s.conditionTiming());
        s.thresholds().forEach((name, value) -> {
            NumericThreshold declared = type.threshold(name)
                    .orElseThrow(() -> new NotificationException(UNKNOWN_THRESHOLD, "Threshold is not declared"));
            if (!declared.allows(value)) {
                throw new NotificationException(THRESHOLD_OUT_OF_RANGE, "Threshold is outside its allowed range");
            }
        });
        if (!s.destinations().isEmpty()) {
            TenantSettings t = store.tenant(tenant).orElseThrow(
                    () -> new NotificationException(UNKNOWN_DESTINATION, "The tenant has connected no channels"));
            if (!s.destinations().stream().allMatch(t::isConnected)) {
                throw new NotificationException(UNKNOWN_DESTINATION, "A destination is not connected for the tenant");
            }
        }
    }
}
