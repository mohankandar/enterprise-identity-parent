package com.documentplatform.notifications;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * One summary to be: everything for the same destination, schedule, entity and recipient roles, due at the same time,
 * goes out as one message. An item joins the bucket of {@code schedule.nextDue(now)}, which is always after now, so a
 * bucket that is due takes no more items: building its summary twice gives the same message.
 *
 * @param roles e-mail recipient roles; empty for Teams and Slack
 */
public record SummaryBucket(TenantId tenant, DestinationId destination, Schedule schedule, EntityId entity,
                            Set<RoleId> roles, Instant dueAt) {

    public SummaryBucket {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(schedule, "schedule");
        roles = Set.copyOf(roles);
        Objects.requireNonNull(dueAt, "dueAt");
    }
}
