package com.documentplatform.notifications.support;

import com.documentplatform.notifications.DeliveryReport;
import com.documentplatform.notifications.DeliveryStatus;
import com.documentplatform.notifications.Notifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Plays the background sender once, right after a publish, so a test can see what happened to the messages that
 * publish queued. Each PENDING report is replaced by the outcome of its attempt; other reports stay as they are.
 */
public final class SendNow {

    private SendNow() {
    }

    public static List<DeliveryReport> delivered(Notifier n, Instant now, List<DeliveryReport> queued) {
        List<DeliveryReport> attempted = new ArrayList<>(n.sendDue(now));
        List<DeliveryReport> out = new ArrayList<>();
        for (DeliveryReport r : queued) {
            Optional<DeliveryReport> outcome = r.status() != DeliveryStatus.PENDING ? Optional.empty()
                    : attempted.stream().filter(a -> Objects.equals(a.destination(), r.destination())
                    && a.eventTypes().equals(r.eventTypes()) && a.kind() == r.kind()).findFirst();
            outcome.ifPresent(attempted::remove);
            out.add(outcome.orElse(r));
        }
        return out;
    }
}
