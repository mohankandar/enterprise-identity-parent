package com.documentplatform.notifications;

import java.time.Duration;
import java.util.Objects;

/**
 * When a continuous condition alerts: once it has lasted {@code alertAfter}, then again every {@code repeatEvery}
 * while it continues. Ranges are checked against the event type (critical events are stricter) at registration and
 * when settings are saved.
 */
public record ConditionTiming(Duration alertAfter, Duration repeatEvery) {

    /** 15 minutes, then every 4 hours. */
    public static final ConditionTiming DEFAULT = new ConditionTiming(Duration.ofMinutes(15), Duration.ofHours(4));

    public ConditionTiming {
        Objects.requireNonNull(alertAfter, "alertAfter");
        Objects.requireNonNull(repeatEvery, "repeatEvery");
    }
}
