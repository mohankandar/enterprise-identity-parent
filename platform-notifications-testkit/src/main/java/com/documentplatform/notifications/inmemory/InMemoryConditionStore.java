package com.documentplatform.notifications.inmemory;

import com.documentplatform.notifications.ConditionKey;
import com.documentplatform.notifications.ConditionState;
import com.documentplatform.notifications.ConditionStore;
import com.documentplatform.notifications.NotificationEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Continuous conditions in memory. For tests. */
public final class InMemoryConditionStore implements ConditionStore {

    private final Map<ConditionKey, ConditionState> states = new LinkedHashMap<>();

    @Override
    public synchronized ConditionState recordFailure(ConditionKey key, NotificationEvent latest, Instant now) {
        ConditionState s = states.get(key);
        ConditionState next = s == null ? new ConditionState(key, latest, now, 1, 0, null)
                : new ConditionState(key, latest, s.since(), s.failures() + 1, s.alerts(), s.lastAlertAt());
        states.put(key, next);
        return next;
    }

    @Override
    public synchronized List<ConditionState> active() {
        return List.copyOf(states.values());
    }

    @Override
    public synchronized Optional<ConditionState> find(ConditionKey key) {
        return Optional.ofNullable(states.get(key));
    }

    @Override
    public synchronized void markAlerted(ConditionKey key, int alerts, Instant at) {
        ConditionState s = states.get(key);
        if (s != null) {
            states.put(key, new ConditionState(key, s.latest(), s.since(), s.failures(), alerts, at));
        }
    }

    @Override
    public synchronized void remove(ConditionKey key) {
        states.remove(key);
    }
}
