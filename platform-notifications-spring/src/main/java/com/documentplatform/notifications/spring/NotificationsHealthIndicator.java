package com.documentplatform.notifications.spring;

import com.documentplatform.notifications.DestinationId;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Actuator health of notifications ({@code /actuator/health/notifications}): each destination with its state
 * (Working, Having problems, Down, Not used yet), and for a failure its stable code and fixed reason. Never an address,
 * a login or a secret.
 *
 * <p>The component reports {@code UP} when no destination has a problem and {@code DEGRADED} otherwise. A broken
 * webhook must not mark the whole app unhealthy (and get it restarted or taken out of a load balancer), so DEGRADED is
 * not counted in the overall status and maps to HTTP 200.
 */
public final class NotificationsHealthIndicator implements HealthIndicator {

    /** Some destination is having problems or is down. */
    public static final Status DEGRADED = new Status("DEGRADED", "A notification destination is having problems or is down");

    private final DestinationHealth health;

    public NotificationsHealthIndicator(DestinationHealth health) {
        this.health = health;
    }

    @Override
    public Health health() {
        Map<String, Object> destinations = new LinkedHashMap<>();
        boolean problem = false;
        for (Map.Entry<DestinationId, DestinationHealth.Status> e : health.statuses().entrySet()) {
            DestinationHealth.Status s = e.getValue();
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("channel", s.channel().displayName());
            d.put("state", s.state().label());
            if (s.failure() != null) {
                d.put("code", s.failure().name());
                d.put("reason", s.reason().orElseThrow());
                d.put("failingSince", s.failingSince().toString());
            }
            if (s.lastAttempt() != null) {
                d.put("lastAttempt", s.lastAttempt().toString());
            }
            destinations.put(e.getKey().value(), d);
            problem |= s.state() == DestinationHealth.State.HAVING_PROBLEMS || s.state() == DestinationHealth.State.DOWN;
        }
        return Health.status(problem ? DEGRADED : Status.UP).withDetail("destinations", destinations).build();
    }
}
