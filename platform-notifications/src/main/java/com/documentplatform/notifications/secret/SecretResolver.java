package com.documentplatform.notifications.secret;

import java.util.Optional;

/**
 * Reads secrets from the secret store. Supplied by the app; the Vault-backed implementation comes with
 * platform-crypto. Called at send time for every attempt, so a rotated password or webhook is picked up without a
 * restart.
 */
public interface SecretResolver {

    /**
     * @return the secret, or empty if the store has nothing under this reference (a permanent failure:
     *         {@code SECRET_NOT_FOUND})
     * @throws RuntimeException if the store cannot be reached (a temporary failure: {@code SECRET_UNAVAILABLE}).
     *                          The exception text is not kept.
     */
    Optional<Secret> resolve(SecretRef ref);
}
