package com.documentplatform.notifications.webhook;

import com.documentplatform.notifications.DestinationId;
import com.documentplatform.notifications.TenantId;
import com.documentplatform.notifications.secret.SecretRef;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Where the webhook address of each Teams or Slack destination is kept in the secret store. The address itself is a
 * secret (it carries the token that lets anyone post), so config and settings hold only this reference. Filled by the
 * app from its install config (Spring wiring in P005); {@link #of(Map)} serves a fixed set.
 */
public interface WebhookDirectory {

    Optional<SecretRef> find(TenantId tenant, DestinationId destination);

    /** A fixed set of references, the same for every tenant. */
    static WebhookDirectory of(Map<DestinationId, SecretRef> webhooks) {
        Map<DestinationId, SecretRef> copy = Map.copyOf(Objects.requireNonNull(webhooks, "webhooks"));
        return (tenant, destination) -> Optional.ofNullable(copy.get(destination));
    }
}
