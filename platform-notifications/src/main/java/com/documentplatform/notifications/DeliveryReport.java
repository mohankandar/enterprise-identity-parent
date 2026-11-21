package com.documentplatform.notifications;

import java.util.Objects;
import java.util.Set;

/**
 * The outcome for one destination. Ids and codes only, so apps can log it as is.
 *
 * @param eventTypes  the event type, or every type in a summary
 * @param destination null when nothing was routed (disabled, no destination)
 * @param failure     set when {@link DeliveryStatus#FAILED} or {@link DeliveryStatus#RETRY_SCHEDULED}
 */
public record DeliveryReport(TenantId tenant, Set<EventTypeId> eventTypes, DestinationId destination,
                             MessageKind kind, DeliveryStatus status, FailureCode failure) {

    public DeliveryReport {
        Objects.requireNonNull(tenant, "tenant");
        eventTypes = Set.copyOf(eventTypes);
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        boolean needsCode = status == DeliveryStatus.FAILED || status == DeliveryStatus.RETRY_SCHEDULED;
        if (needsCode != (failure != null)) {
            throw new IllegalArgumentException("A failure code goes with FAILED and RETRY_SCHEDULED only");
        }
    }
}
