package com.documentplatform.notifications;

import java.util.Objects;
import java.util.Set;

/**
 * One message to one destination.
 *
 * @param recipients     the people holding the recipient roles, for e-mail; empty for Teams and Slack, which post to
 *                       the webhook the admin assigned
 * @param idempotencyKey the outbox key of this message: the same on every attempt, different for every message.
 *                       Senders pass it on where the channel supports it (the e-mail {@code Message-ID})
 */
public record OutgoingMessage(TenantId tenant, DestinationId destination, ChannelKind kind, Set<Recipient> recipients,
                              Message message, String idempotencyKey) {

    public OutgoingMessage {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(kind, "kind");
        recipients = Set.copyOf(recipients);
        Objects.requireNonNull(message, "message");
        Text.require(idempotencyKey, Text.ID, "Idempotency key");
    }
}
