package com.documentplatform.notifications;

/**
 * Sends messages over one kind of channel. Real senders: {@code email.EmailChannel}, {@code teams.TeamsChannel} and
 * {@code slack.SlackChannel}; a fake for tests is in the testkit module. A sender looks up the destination's secret
 * (mail password, webhook address) itself, through a {@code secret.SecretResolver}, and must never put it in a
 * {@link SendResult}, exception or log.
 */
public interface Channel {

    ChannelKind kind();

    /**
     * Sends one message. Reports a failure with a specific {@link FailureCode} rather than throwing; temporary
     * failures are retried by the outbox, permanent ones are not.
     */
    SendResult send(OutgoingMessage message);
}
