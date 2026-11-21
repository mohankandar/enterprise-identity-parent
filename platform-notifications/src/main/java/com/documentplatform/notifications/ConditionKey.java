package com.documentplatform.notifications;

import java.util.Objects;

/**
 * Identifies one continuous condition: which event type, for which tenant, entity and record (for example "intake
 * source unreachable" for source {@code mail-1}). Failing reports with the same key are the same condition.
 */
public record ConditionKey(EventTypeId type, TenantId tenant, EntityId entity, RecordIdentifier identifier) {

    public ConditionKey {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(tenant, "tenant");
    }

    public static ConditionKey of(NotificationEvent event) {
        return new ConditionKey(event.type(), event.tenant(), event.entity(), event.identifier());
    }
}
