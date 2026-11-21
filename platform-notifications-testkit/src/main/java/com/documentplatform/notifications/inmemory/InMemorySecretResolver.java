package com.documentplatform.notifications.inmemory;

import com.documentplatform.notifications.secret.Secret;
import com.documentplatform.notifications.secret.SecretRef;
import com.documentplatform.notifications.secret.SecretResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Secrets kept in memory. Can be told the store is down, for tests of {@code SECRET_UNAVAILABLE}. For tests. */
public final class InMemorySecretResolver implements SecretResolver {

    private final Map<SecretRef, Secret> secrets = new HashMap<>();
    private boolean unavailable;

    public synchronized InMemorySecretResolver put(SecretRef ref, String secret) {
        secrets.put(ref, Secret.of(secret));
        return this;
    }

    public synchronized void unavailable(boolean down) {
        this.unavailable = down;
    }

    @Override
    public synchronized Optional<Secret> resolve(SecretRef ref) {
        if (unavailable) {
            throw new IllegalStateException("secret store down");
        }
        return Optional.ofNullable(secrets.get(ref));
    }
}
