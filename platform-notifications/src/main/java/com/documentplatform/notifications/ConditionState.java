package com.documentplatform.notifications;

import java.time.Instant;
import java.util.Objects;

/**
 * A continuous condition that is going on.
 *
 * @param latest      the latest failing report (its counts, times and link go into the alert)
 * @param since       when the first failing report came
 * @param failures    failing reports seen so far
 * @param alerts      alerts sent so far (the first alert and its repeats); 0 until the threshold passes
 * @param lastAlertAt when the last alert was raised; null while {@code alerts} is 0
 */
public record ConditionState(ConditionKey key, NotificationEvent latest, Instant since, long failures, int alerts,
                             Instant lastAlertAt) {

    public ConditionState {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(latest, "latest");
        Objects.requireNonNull(since, "since");
        if ((alerts == 0) != (lastAlertAt == null)) {
            throw new IllegalArgumentException("lastAlertAt is set once an alert was raised");
        }
    }
}
