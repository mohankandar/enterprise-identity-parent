package com.documentplatform.notifications.secret;

import java.util.Objects;

/**
 * A secret read from the secret store: a mail password or a webhook address (which carries its own token).
 * {@link #toString()} never shows it, so it cannot reach a log or an exception text by accident; only the sender that
 * needs it calls {@link #reveal()}, at the moment of use.
 */
public final class Secret {

    private final String value;

    private Secret(String value) {
        this.value = value;
    }

    public static Secret of(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("A secret cannot be empty");
        }
        return new Secret(value);
    }

    /** The secret itself. Use it, never log or store it. */
    public String reveal() {
        return value;
    }

    @Override
    public String toString() {
        return "Secret[redacted]";
    }
}
