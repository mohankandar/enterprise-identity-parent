package com.documentplatform.notifications;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One event an app offers to its customers. The app registers its event types in an {@link EventCatalogue}; this
 * module has none of its own.
 *
 * @param id         stable id, for example {@code intake.source-unreachable}
 * @param name       the event's name as shown in messages
 * @param critical   something is down: goes Immediately to the IT Admin, may only be Immediately or Every hour
 * @param continuous a condition that lasts (alert after a threshold, repeat, Recovered), rather than a one-off event
 * @param fields     the counts and times events of this type may carry
 * @param thresholds numbers the app checks before publishing, adjustable by the admin within their range
 */
public record EventType(EventTypeId id, String name, EventKind kind, boolean critical, boolean continuous,
                        EventDefaults defaults, List<FieldSpec> fields, List<NumericThreshold> thresholds) {

    public EventType {
        Objects.requireNonNull(id, "id");
        Text.require(name, Text.LABEL, "Event name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(defaults, "defaults");
        fields = List.copyOf(fields);
        thresholds = List.copyOf(thresholds);
        if (continuous != (defaults.conditionTiming() != null)) {
            throw new NotificationException(NotificationException.Reason.NOT_A_CONDITION,
                    "Condition timing must be set for continuous event types and only for them");
        }
        Set<String> names = new HashSet<>();
        fields.forEach(f -> unique(names, f.name()));
        names.clear();
        thresholds.forEach(t -> unique(names, t.name()));
    }

    private static void unique(Set<String> names, String name) {
        if (!names.add(name)) {
            throw new NotificationException(NotificationException.Reason.INVALID_VALUE, "Field names must be unique");
        }
    }

    public Optional<FieldSpec> field(String name) {
        return fields.stream().filter(f -> f.name().equals(name)).findFirst();
    }

    public Optional<NumericThreshold> threshold(String name) {
        return thresholds.stream().filter(t -> t.name().equals(name)).findFirst();
    }

    public static Builder builder(String id, String name, EventKind kind) {
        return new Builder(EventTypeId.of(id), name, kind);
    }

    /** Builds an event type. Critical events default to Immediately; other events must name their schedule. */
    public static final class Builder {
        private final EventTypeId id;
        private final String name;
        private final EventKind kind;
        private boolean critical;
        private boolean enabled = true;
        private Schedule schedule;
        private final Set<RoleId> roles = new LinkedHashSet<>();
        private ConditionTiming conditionTiming;
        private final List<FieldSpec> fields = new ArrayList<>();
        private final List<NumericThreshold> thresholds = new ArrayList<>();

        private Builder(EventTypeId id, String name, EventKind kind) {
            this.id = id;
            this.name = name;
            this.kind = kind;
        }

        public Builder critical() {
            this.critical = true;
            return this;
        }

        public Builder offByDefault() {
            this.enabled = false;
            return this;
        }

        public Builder schedule(Schedule schedule) {
            this.schedule = schedule;
            return this;
        }

        public Builder recipients(RoleId... roles) {
            this.roles.addAll(List.of(roles));
            return this;
        }

        /** Makes this a continuous condition with the default timing (15 minutes, repeat every 4 hours). */
        public Builder continuous() {
            return continuous(ConditionTiming.DEFAULT);
        }

        public Builder continuous(ConditionTiming timing) {
            this.conditionTiming = Objects.requireNonNull(timing, "timing");
            return this;
        }

        public Builder count(String name, String label) {
            fields.add(FieldSpec.count(name, label));
            return this;
        }

        public Builder time(String name, String label) {
            fields.add(FieldSpec.time(name, label));
            return this;
        }

        public Builder threshold(String name, String label, long defaultValue, long min, long max) {
            thresholds.add(new NumericThreshold(name, label, defaultValue, min, max));
            return this;
        }

        public EventType build() {
            Schedule s = schedule;
            if (s == null) {
                if (!critical) {
                    throw new NotificationException(NotificationException.Reason.INVALID_VALUE,
                            "A non-critical event type must name its default schedule");
                }
                s = Schedule.immediately();
            }
            return new EventType(id, name, kind, critical, conditionTiming != null,
                    new EventDefaults(enabled, s, roles, conditionTiming), fields, thresholds);
        }
    }
}
