package com.documentplatform.notifications;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Continuous problems alert once, not per failure. A condition starts with its first failing report. It alerts once
 * it has lasted {@code alertAfter}, repeats every {@code repeatEvery} while it continues, and yields one Recovered
 * when it clears — only if it had alerted. Further failing reports only count; they never alert by themselves.
 *
 * <p>In memory for P003; P004 stores it.
 */
final class ConditionTracker {

    private static final class State {
        final Instant since;
        NotificationEvent latest;
        EventType type;
        int failures;
        Instant lastAlert;

        State(Instant since) {
            this.since = since;
        }
    }

    private final Map<ConditionKey, State> states = new LinkedHashMap<>();

    void failing(NotificationEvent event, EventType type, Instant now) {
        State s = states.computeIfAbsent(ConditionKey.of(event), k -> new State(now));
        s.latest = event;
        s.type = type;
        s.failures++;
    }

    /** Alerts and repeats that are due at {@code now}. */
    List<Occurrence> evaluate(Instant now, Function<NotificationEvent, ConditionTiming> timing) {
        List<Occurrence> out = new ArrayList<>();
        for (State s : states.values()) {
            ConditionTiming t = timing.apply(s.latest);
            if (s.lastAlert == null) {
                if (!now.isBefore(s.since.plus(t.alertAfter()))) {
                    s.lastAlert = now;
                    out.add(occurrence(s, MessageKind.CONDITION_ALERT, now));
                }
            } else if (!now.isBefore(s.lastAlert.plus(t.repeatEvery()))) {
                s.lastAlert = now;
                out.add(occurrence(s, MessageKind.CONDITION_ONGOING, now));
            }
        }
        return out;
    }

    /** Ends the condition. Recovered only if it had alerted; a second clear finds nothing. */
    Optional<Occurrence> cleared(ConditionKey key, Instant now) {
        State s = states.remove(key);
        if (s == null || s.lastAlert == null) {
            return Optional.empty();
        }
        return Optional.of(occurrence(s, MessageKind.RECOVERED, now));
    }

    boolean isActive(ConditionKey key) {
        return states.containsKey(key);
    }

    private static Occurrence occurrence(State s, MessageKind kind, Instant at) {
        return new Occurrence(s.latest, s.type, kind, at, s.since, s.failures);
    }
}
