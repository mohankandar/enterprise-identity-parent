package com.documentplatform.notifications;

import java.time.Duration;
import java.util.Objects;

/**
 * The outcome of one send: sent, or failed with a specific code.
 *
 * @param retryAfter how long the receiver asked us to wait (HTTP {@code Retry-After}); null when it said nothing
 */
public record SendResult(boolean sent, FailureCode failure, Duration retryAfter) {

    private static final SendResult SENT = new SendResult(true, null, null);

    public SendResult {
        if (sent == (failure != null)) {
            throw new IllegalArgumentException("A result is either sent or failed with a code");
        }
        if (retryAfter != null && (sent || retryAfter.isNegative())) {
            throw new IllegalArgumentException("Retry-After goes with a failure and is not negative");
        }
    }

    public static SendResult ok() {
        return SENT;
    }

    public static SendResult failed(FailureCode code) {
        return new SendResult(false, Objects.requireNonNull(code, "code"), null);
    }

    public static SendResult failed(FailureCode code, Duration retryAfter) {
        return new SendResult(false, Objects.requireNonNull(code, "code"), retryAfter);
    }
}
