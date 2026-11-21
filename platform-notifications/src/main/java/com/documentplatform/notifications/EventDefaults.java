package com.documentplatform.notifications;

import java.util.Objects;
import java.util.Set;

/**
 * What an event type does until an admin changes it. Threshold defaults live on each {@link NumericThreshold}.
 *
 * @param conditionTiming default timing for continuous event types; null for one-off event types
 */
public record EventDefaults(boolean enabled, Schedule schedule, Set<RoleId> recipientRoles,
                            ConditionTiming conditionTiming) {

    public EventDefaults {
        Objects.requireNonNull(schedule, "schedule");
        recipientRoles = Set.copyOf(Objects.requireNonNull(recipientRoles, "recipientRoles"));
    }
}
