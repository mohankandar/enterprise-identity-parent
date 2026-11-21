package com.documentplatform.notifications;

import java.time.Instant;
import java.util.Objects;

/**
 * One occurrence waiting in a summary bucket: a published event, or a condition's alert, repeat or recovery.
 *
 * @param key      unique per occurrence and destination, so adding the same item twice (after a restart) keeps one
 * @param at       when it happened, or when the alert was raised
 * @param since    when the condition started; null for one-off events
 * @param failures failing reports seen for the condition; 0 for one-off events
 */
public record SummaryItem(String key, SummaryBucket bucket, NotificationEvent event, MessageKind kind, Instant at,
                          Instant since, long failures) {

    public SummaryItem {
        Text.require(key, Text.ID, "Summary item key");
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(at, "at");
        if (!bucket.tenant().equals(event.tenant()) || !Objects.equals(bucket.entity(), event.entity())) {
            throw new IllegalArgumentException("An item belongs to the bucket of its own tenant and entity");
        }
    }
}
