package com.documentplatform.notifications;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Continuous problems alert once, not per failure. A condition starts with its first failing report. It alerts once
 * it has lasted {@code alertAfter}, repeats every {@code repeatEvery} while it continues, and yields one Recovered
 * when it clears — only if it had alerted. Further failing reports only count; they never alert by themselves.
 *
 * <p>State lives in a {@link ConditionStore}. Alerts are numbered, and the number is part of the message key, so an
 * alert that was routed but not yet recorded (the process stopped in between) is routed again with the same key and
 * found already saved, rather than sent twice.
 */
final class ConditionTracker {

    /** An alert that is due: route it, then {@link #markAlerted} it. */
    record Due(Occurrence occurrence, ConditionKey key, int alertNumber) {
    }

    private final ConditionStore store;
    private final EventCatalogue catalogue;

    ConditionTracker(ConditionStore store, EventCatalogue catalogue) {
        this.store = store;
        this.catalogue = catalogue;
    }

    void failing(NotificationEvent event, Instant now) {
        store.recordFailure(ConditionKey.of(event), event, now);
    }

    /** Alerts and repeats that are due at {@code now}. */
    List<Due> due(Instant now, Function<NotificationEvent, ConditionTiming> timing) {
        List<Due> out = new ArrayList<>();
        for (ConditionState s : store.active()) {
            Optional<EventType> type = catalogue.find(s.key().type());
            if (type.isEmpty() || !type.get().continuous()) {
                continue; // removed from the catalogue in a later release: nothing to say about it
            }
            ConditionTiming t = timing.apply(s.latest());
            if (s.alerts() == 0) {
                if (!now.isBefore(s.since().plus(t.alertAfter()))) {
                    out.add(due(s, type.get(), MessageKind.CONDITION_ALERT, now));
                }
            } else if (!now.isBefore(s.lastAlertAt().plus(t.repeatEvery()))) {
                out.add(due(s, type.get(), MessageKind.CONDITION_ONGOING, now));
            }
        }
        return out;
    }

    void markAlerted(Due due, Instant now) {
        store.markAlerted(due.key(), due.alertNumber(), now);
    }

    /**
     * The Recovered occurrence for a condition that clears, if it had alerted; route it, then {@link #remove}. A
     * condition that clears before alerting is removed at once and yields nothing; a second clear finds nothing.
     */
    Optional<Occurrence> recovered(ConditionKey key, Instant now) {
        Optional<ConditionState> found = store.find(key);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ConditionState s = found.get();
        Optional<EventType> type = catalogue.find(key.type());
        if (s.alerts() == 0 || type.isEmpty()) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(new Occurrence(Keys.recovered(key, s.since()), s.latest(), type.get(), MessageKind.RECOVERED,
                now, s.since(), s.failures()));
    }

    void remove(ConditionKey key) {
        store.remove(key);
    }

    boolean isActive(ConditionKey key) {
        return store.find(key).isPresent();
    }

    private static Due due(ConditionState s, EventType type, MessageKind kind, Instant now) {
        int n = s.alerts() + 1;
        return new Due(new Occurrence(Keys.alert(s.key(), s.since(), n), s.latest(), type, kind, now, s.since(),
                s.failures()), s.key(), n);
    }
}
