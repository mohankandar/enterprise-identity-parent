package com.documentplatform.notifications;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.documentplatform.notifications.NotificationException.Reason.DUPLICATE_EVENT_TYPE;
import static com.documentplatform.notifications.NotificationException.Reason.UNKNOWN_EVENT_TYPE;

/**
 * The event types one app offers, registered at start-up. Registration checks every type's defaults against the
 * rules: critical events default on, Immediately or Every hour, to the IT Admin role; every type has a recipient role;
 * continuous types carry valid timing. Event types come and go between releases; settings for a type that is no
 * longer registered are ignored.
 */
public final class EventCatalogue {

    private final RoleId itAdminRole;
    private final Map<EventTypeId, EventType> types;
    private final List<EventType> order;

    private EventCatalogue(RoleId itAdminRole, Map<EventTypeId, EventType> types) {
        this.itAdminRole = itAdminRole;
        this.types = Map.copyOf(types);
        this.order = List.copyOf(types.values());
    }

    /** @param itAdminRole the app's role for the customer's IT Admin, who receives every critical event */
    public static Builder builder(RoleId itAdminRole) {
        return new Builder(Objects.requireNonNull(itAdminRole, "itAdminRole"));
    }

    public RoleId itAdminRole() {
        return itAdminRole;
    }

    public Optional<EventType> find(EventTypeId id) {
        return Optional.ofNullable(types.get(id));
    }

    public EventType require(EventTypeId id) {
        return find(id).orElseThrow(() -> new NotificationException(UNKNOWN_EVENT_TYPE, "Event type is not registered"));
    }

    /** All event types, in registration order. */
    public Collection<EventType> all() {
        return order;
    }

    public static final class Builder {
        private final RoleId itAdminRole;
        private final Map<EventTypeId, EventType> types = new LinkedHashMap<>();

        private Builder(RoleId itAdminRole) {
            this.itAdminRole = itAdminRole;
        }

        public Builder register(EventType type) {
            Objects.requireNonNull(type, "type");
            if (types.containsKey(type.id())) {
                throw new NotificationException(DUPLICATE_EVENT_TYPE, "Event type is registered twice");
            }
            EventDefaults d = type.defaults();
            Rules.check(type, itAdminRole, d.enabled(), d.schedule(), d.recipientRoles(), d.conditionTiming());
            types.put(type.id(), type);
            return this;
        }

        public EventCatalogue build() {
            return new EventCatalogue(itAdminRole, types);
        }
    }
}
