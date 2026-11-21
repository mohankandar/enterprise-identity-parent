package com.documentplatform.notifications;

import java.time.Instant;

/**
 * Something to tell someone: a published event, or a condition's alert, repeat or recovery.
 *
 * @param key      what it is (see {@link Keys}); with a destination it gives the message's idempotency key
 * @param at       when it happened (the event) or when the alert was raised
 * @param since    when the condition started; null for one-off events
 * @param failures failing reports seen for the condition; 0 for one-off events
 */
record Occurrence(String key, NotificationEvent event, EventType type, MessageKind kind, Instant at, Instant since,
                  long failures) {
}
