package com.documentplatform.notifications;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Occurrences held for the next summary, in a {@link SummaryStore}. One bucket per destination, schedule, entity,
 * set of recipient roles and due time: everything in a bucket goes to the same people at the same time, as one
 * message. The due time is the schedule's next time after the occurrence arrived (the next whole hour, next 4-hour
 * mark or next daily time), so a due bucket takes no more items and its summary covers what came since the last one.
 */
final class SummaryBuckets {

    record Due(SummaryBucket bucket, List<SummaryItem> items) {
    }

    private final SummaryStore store;

    SummaryBuckets(SummaryStore store) {
        this.store = store;
    }

    void add(TenantId tenant, DestinationId destination, Schedule schedule, EntityId entity, Set<RoleId> roles,
             Occurrence o, Instant now, ZoneId zone) {
        SummaryBucket bucket = new SummaryBucket(tenant, destination, schedule, entity, roles,
                schedule.nextDue(now, zone));
        store.add(new SummaryItem(Keys.forDestination(o.key(), destination), bucket, o.event(), o.kind(), o.at(),
                o.since(), o.failures()));
    }

    /** Every bucket due at {@code now}, with its items in arrival order. Items stay stored until {@link #remove}. */
    List<Due> due(Instant now) {
        Map<SummaryBucket, List<SummaryItem>> byBucket = new LinkedHashMap<>();
        for (SummaryItem item : store.due(now)) {
            byBucket.computeIfAbsent(item.bucket(), b -> new ArrayList<>()).add(item);
        }
        List<Due> due = new ArrayList<>();
        byBucket.forEach((bucket, items) -> due.add(new Due(bucket, List.copyOf(items))));
        return due;
    }

    void remove(Due due) {
        store.remove(due.items().stream().map(SummaryItem::key).toList());
    }

    int pending() {
        return store.pending();
    }
}
