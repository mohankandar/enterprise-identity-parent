package com.documentplatform.notifications;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * When a message that failed for a temporary reason is tried again: after {@code firstDelay}, then twice as long each
 * time up to {@code maxDelay}. A receiver's {@code Retry-After} replaces the backoff for that attempt. Once the next
 * attempt would fall more than {@code giveUpAfter} after the message was created, it is given up and reported.
 * Permanent failures are never retried.
 */
public record RetryPolicy(Duration firstDelay, Duration maxDelay, Duration giveUpAfter) {

    private static final Duration MIN_DELAY = Duration.ofSeconds(1);

    /** 1 minute, doubling up to 30 minutes, give up after 24 hours. */
    public static final RetryPolicy DEFAULT =
            new RetryPolicy(Duration.ofMinutes(1), Duration.ofMinutes(30), Duration.ofHours(24));

    public RetryPolicy {
        Objects.requireNonNull(firstDelay, "firstDelay");
        Objects.requireNonNull(maxDelay, "maxDelay");
        Objects.requireNonNull(giveUpAfter, "giveUpAfter");
        if (firstDelay.compareTo(MIN_DELAY) < 0 || maxDelay.compareTo(firstDelay) < 0 || giveUpAfter.isNegative()) {
            throw new IllegalArgumentException("Retry delays must be at least a second and the maximum not below the first");
        }
    }

    /** The delay after {@code attemptsMade} failed attempts, without a Retry-After. */
    public Duration backoff(int attemptsMade) {
        Duration d = firstDelay;
        for (int i = 1; i < attemptsMade && d.compareTo(maxDelay) < 0; i++) {
            d = d.multipliedBy(2);
        }
        return d.compareTo(maxDelay) > 0 ? maxDelay : d;
    }

    /**
     * When to try again, or empty to give up.
     *
     * @param retryAfter the receiver's Retry-After, or null
     */
    public Optional<Instant> next(Instant createdAt, int attemptsMade, Duration retryAfter, Instant now) {
        Duration delay = retryAfter == null ? backoff(attemptsMade)
                : retryAfter.compareTo(MIN_DELAY) < 0 ? MIN_DELAY : retryAfter;
        Instant at = now.plus(delay);
        return at.isAfter(createdAt.plus(giveUpAfter)) ? Optional.empty() : Optional.of(at);
    }
}
