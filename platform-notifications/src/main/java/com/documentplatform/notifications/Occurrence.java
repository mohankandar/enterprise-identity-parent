package com.documentplatform.notifications;

import java.time.Instant;

/**
 * Something to tell someone: a published event, or a condition's alert, repeat or recovery.
 *
 * @param at       when it happened (the event) or when the alert was raised
 * @param since    when the condition started; null for one-off events
 * @param failures failing reports seen for the condition; 0 for one-off events
 */
record Occurrence(NotificationEvent event, EventType type, MessageKind kind, Instant at, Instant since, int failures) {
}
