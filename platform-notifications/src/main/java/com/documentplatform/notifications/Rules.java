package com.documentplatform.notifications;

import java.time.Duration;
import java.util.Set;

import static com.documentplatform.notifications.NotificationException.Reason.CRITICAL_FREQUENCY;
import static com.documentplatform.notifications.NotificationException.Reason.CRITICAL_MUST_BE_ON;
import static com.documentplatform.notifications.NotificationException.Reason.CRITICAL_NEEDS_IT_ADMIN;
import static com.documentplatform.notifications.NotificationException.Reason.NOT_A_CONDITION;
import static com.documentplatform.notifications.NotificationException.Reason.NO_RECIPIENT_ROLES;
import static com.documentplatform.notifications.NotificationException.Reason.TIMING_OUT_OF_RANGE;

/** The rules that apply both to an event type's defaults (at registration) and to an admin's settings (on save). */
final class Rules {

    static final Duration MAX_ALERT_AFTER = Duration.ofHours(24);
    /** A critical condition must alert within the hour, in line with "Immediately or Every hour". */
    static final Duration MAX_CRITICAL_ALERT_AFTER = Duration.ofHours(1);
    static final Duration MIN_REPEAT = Duration.ofMinutes(15);
    static final Duration MAX_REPEAT = Duration.ofHours(24);

    private Rules() {
    }

    static void check(EventType type, RoleId itAdminRole, boolean enabled, Schedule schedule, Set<RoleId> roles,
                      ConditionTiming timing) {
        if (roles.isEmpty()) {
            throw new NotificationException(NO_RECIPIENT_ROLES, "An event needs at least one recipient role");
        }
        if (type.critical()) {
            if (!schedule.allowedForCritical()) {
                throw new NotificationException(CRITICAL_FREQUENCY,
                        "A critical event may only be sent Immediately or Every hour");
            }
            if (!enabled) {
                throw new NotificationException(CRITICAL_MUST_BE_ON, "A critical event cannot be switched off");
            }
            if (!roles.contains(itAdminRole)) {
                throw new NotificationException(CRITICAL_NEEDS_IT_ADMIN, "A critical event always goes to the IT Admin");
            }
        }
        if (type.continuous() != (timing != null)) {
            throw new NotificationException(NOT_A_CONDITION,
                    "Condition timing applies to continuous event types only, and they need it");
        }
        if (timing != null) {
            Duration maxAlert = type.critical() ? MAX_CRITICAL_ALERT_AFTER : MAX_ALERT_AFTER;
            if (timing.alertAfter().isNegative() || timing.alertAfter().compareTo(maxAlert) > 0
                    || timing.repeatEvery().compareTo(MIN_REPEAT) < 0 || timing.repeatEvery().compareTo(MAX_REPEAT) > 0) {
                throw new NotificationException(TIMING_OUT_OF_RANGE, "Condition threshold or repeat is out of range");
            }
        }
    }
}
