package com.documentplatform.notifications;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What an app publishes. Structured fields only: there is deliberately no text, title, description or "details"
 * field, so document content, redacted values, sensitive case fields and secrets have nowhere to go. The only
 * per-record text is the {@link RecordIdentifier}; everything else is an id, a number, an instant or an app path.
 * Count and time names must be declared by the event type.
 *
 * @param entity     the entity the event belongs to; required for business events, optional for technical ones
 * @param identifier the record's primary identifier (for example a contract number); optional
 * @param link       where the message points in the app; optional
 * @param occurredAt when it happened; null means "now" when published
 */
public record NotificationEvent(EventTypeId type, TenantId tenant, EntityId entity, RecordIdentifier identifier,
                                Map<String, Long> counts, Map<String, Instant> times, AppLink link,
                                Instant occurredAt) {

    public NotificationEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(tenant, "tenant");
        counts = Map.copyOf(counts);
        times = Map.copyOf(times);
        counts.forEach((name, value) -> {
            Text.require(name, Text.FIELD_NAME, "Count name");
            if (value < 0) {
                throw new NotificationException(NotificationException.Reason.INVALID_VALUE, "Counts cannot be negative");
            }
        });
        times.keySet().forEach(name -> Text.require(name, Text.FIELD_NAME, "Time name"));
    }

    public static Builder builder(EventTypeId type, TenantId tenant) {
        return new Builder(type, tenant);
    }

    public static final class Builder {
        private final EventTypeId type;
        private final TenantId tenant;
        private EntityId entity;
        private RecordIdentifier identifier;
        private final Map<String, Long> counts = new LinkedHashMap<>();
        private final Map<String, Instant> times = new LinkedHashMap<>();
        private AppLink link;
        private Instant occurredAt;

        private Builder(EventTypeId type, TenantId tenant) {
            this.type = type;
            this.tenant = tenant;
        }

        public Builder entity(EntityId entity) {
            this.entity = entity;
            return this;
        }

        public Builder identifier(RecordIdentifier identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder count(String name, long value) {
            counts.put(name, value);
            return this;
        }

        public Builder time(String name, Instant value) {
            times.put(name, Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder link(AppLink link) {
            this.link = link;
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public NotificationEvent build() {
            return new NotificationEvent(type, tenant, entity, identifier, counts, times, link, occurredAt);
        }
    }
}
