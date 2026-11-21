package com.documentplatform.notifications;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Occurrences held for the next summary. One bucket per destination, schedule, entity and set of recipient roles:
 * everything in a bucket goes to the same people at the same time, as one message. A bucket's due time is fixed when
 * its first item arrives (the next whole hour, next 4-hour mark or next daily time); when it is sent the bucket is
 * emptied, so the next summary holds only what came after.
 *
 * <p>In memory for P003; the outbox in P004 makes it survive restarts.
 */
final class SummaryBuckets {

    record Key(TenantId tenant, DestinationId destination, Schedule schedule, EntityId entity, Set<RoleId> roles) {
        Key {
            roles = Set.copyOf(roles);
        }
    }

    record Due(Key key, List<Occurrence> items) {
    }

    private static final class Bucket {
        final Instant due;
        final List<Occurrence> items = new ArrayList<>();

        Bucket(Instant due) {
            this.due = due;
        }
    }

    private final Map<Key, Bucket> buckets = new LinkedHashMap<>();

    void add(Key key, Occurrence occurrence, Instant now, ZoneId zone) {
        buckets.computeIfAbsent(key, k -> new Bucket(k.schedule().nextDue(now, zone))).items.add(occurrence);
    }

    /** Removes and returns every bucket due at {@code now}. */
    List<Due> takeDue(Instant now) {
        List<Due> due = new ArrayList<>();
        for (Iterator<Map.Entry<Key, Bucket>> it = buckets.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Key, Bucket> e = it.next();
            if (!e.getValue().due.isAfter(now)) {
                due.add(new Due(e.getKey(), List.copyOf(e.getValue().items)));
                it.remove();
            }
        }
        return due;
    }

    int pending() {
        return buckets.values().stream().mapToInt(b -> b.items.size()).sum();
    }
}
