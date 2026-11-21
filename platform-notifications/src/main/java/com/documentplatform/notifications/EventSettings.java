package com.documentplatform.notifications;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An admin's choices for one event type: technical events at tenant level (IT Admin), business events per entity
 * (Business Admin). Start from {@link #defaultsFor(EventType)} and change what the admin changed. Checked against the
 * rules when saved through {@link NotificationSettings}.
 *
 * @param recipientRoles  who gets it by e-mail
 * @param destinations    which connected destinations it goes to; empty means the tenant's e-mail destination
 * @param conditionTiming for continuous event types only; null otherwise
 * @param thresholds      values for the event type's declared thresholds; missing ones use the default
 */
public record EventSettings(boolean enabled, Schedule schedule, Set<RoleId> recipientRoles,
                            Set<DestinationId> destinations, ConditionTiming conditionTiming,
                            Map<String, Long> thresholds) {

    public EventSettings {
        Objects.requireNonNull(schedule, "schedule");
        recipientRoles = Set.copyOf(recipientRoles);
        destinations = Set.copyOf(destinations);
        thresholds = Map.copyOf(thresholds);
    }

    public static EventSettings defaultsFor(EventType type) {
        EventDefaults d = type.defaults();
        Map<String, Long> thresholds = new HashMap<>();
        type.thresholds().forEach(t -> thresholds.put(t.name(), t.defaultValue()));
        return new EventSettings(d.enabled(), d.schedule(), d.recipientRoles(), Set.of(), d.conditionTiming(),
                thresholds);
    }

    public EventSettings withEnabled(boolean enabled) {
        return new EventSettings(enabled, schedule, recipientRoles, destinations, conditionTiming, thresholds);
    }

    public EventSettings withSchedule(Schedule schedule) {
        return new EventSettings(enabled, schedule, recipientRoles, destinations, conditionTiming, thresholds);
    }

    public EventSettings withRecipientRoles(RoleId... roles) {
        return new EventSettings(enabled, schedule, Set.of(roles), destinations, conditionTiming, thresholds);
    }

    public EventSettings withDestinations(DestinationId... destinations) {
        return new EventSettings(enabled, schedule, recipientRoles, Set.of(destinations), conditionTiming, thresholds);
    }

    public EventSettings withConditionTiming(ConditionTiming timing) {
        return new EventSettings(enabled, schedule, recipientRoles, destinations, timing, thresholds);
    }

    public EventSettings withThreshold(String name, long value) {
        Map<String, Long> copy = new HashMap<>(thresholds);
        copy.put(name, value);
        return new EventSettings(enabled, schedule, recipientRoles, destinations, conditionTiming, copy);
    }
}
