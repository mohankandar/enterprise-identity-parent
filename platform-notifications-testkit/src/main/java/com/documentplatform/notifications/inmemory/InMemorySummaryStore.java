package com.documentplatform.notifications.inmemory;

import com.documentplatform.notifications.SummaryItem;
import com.documentplatform.notifications.SummaryStore;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Summary buckets in memory. For tests. */
public final class InMemorySummaryStore implements SummaryStore {

    private final Map<String, SummaryItem> items = new LinkedHashMap<>();

    @Override
    public synchronized void add(SummaryItem item) {
        items.putIfAbsent(item.key(), item);
    }

    @Override
    public synchronized List<SummaryItem> due(Instant now) {
        // Stable sort: arrival order within the same due time.
        return items.values().stream().filter(i -> !i.bucket().dueAt().isAfter(now))
                .sorted(Comparator.comparing(i -> i.bucket().dueAt())).toList();
    }

    @Override
    public synchronized void remove(Collection<String> itemKeys) {
        itemKeys.forEach(items::remove);
    }

    @Override
    public synchronized int pending() {
        return items.size();
    }
}
