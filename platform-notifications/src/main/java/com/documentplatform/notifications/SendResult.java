package com.documentplatform.notifications;

import java.util.Objects;

/** The outcome of one send: sent, or failed with a specific code. */
public record SendResult(boolean sent, FailureCode failure) {

    private static final SendResult SENT = new SendResult(true, null);

    public SendResult {
        if (sent == (failure != null)) {
            throw new IllegalArgumentException("A result is either sent or failed with a code");
        }
    }

    public static SendResult ok() {
        return SENT;
    }

    public static SendResult failed(FailureCode code) {
        return new SendResult(false, Objects.requireNonNull(code, "code"));
    }
}
