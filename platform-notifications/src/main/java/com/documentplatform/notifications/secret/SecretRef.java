package com.documentplatform.notifications.secret;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Where a secret is kept in the secret store, for example {@code notifications/t1/teams-ops}. Settings, config and
 * tables hold this reference only; the secret itself (a mail password, a webhook address) is read through a
 * {@link SecretResolver} at the moment of sending and is not kept. A web address is refused as a reference, so a
 * webhook address pasted in its place is caught.
 */
public record SecretRef(String value) {

    private static final Pattern SHAPE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");

    public SecretRef {
        Objects.requireNonNull(value, "value");
        // "//" refuses a pasted address (https://...), the most likely way a secret ends up where its reference goes.
        if (!SHAPE.matcher(value).matches() || value.contains("//")) {
            // The value is not repeated: someone may have pasted the secret itself here.
            throw new IllegalArgumentException("Secret reference is not valid");
        }
    }

    public static SecretRef of(String value) {
        return new SecretRef(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
