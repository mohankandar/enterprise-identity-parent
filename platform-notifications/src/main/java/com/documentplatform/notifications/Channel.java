package com.documentplatform.notifications;

/**
 * Sends messages over one kind of channel. Real senders (mail server, Teams and Slack incoming webhooks) arrive in
 * P004; {@code inmemory.InMemoryChannel} is a fake for tests. A sender looks up the destination's secret (login,
 * webhook address) itself and must never put it in a {@link SendResult}, exception or log.
 */
public interface Channel {

    ChannelKind kind();

    /** Sends one message. Reports a failure with a specific {@link FailureCode} rather than throwing. */
    SendResult send(OutgoingMessage message);
}
