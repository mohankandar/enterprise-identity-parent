package com.documentplatform.notifications.spring;

import com.documentplatform.notifications.Channel;
import com.documentplatform.notifications.ChannelKind;
import com.documentplatform.notifications.FailureCode;
import com.documentplatform.notifications.OutgoingMessage;
import com.documentplatform.notifications.SendResult;

import java.util.Objects;

/** Passes each send on and records its outcome in {@link DestinationHealth}. */
final class ObservedChannel implements Channel {

    private final Channel channel;
    private final DestinationHealth health;

    ObservedChannel(Channel channel, DestinationHealth health) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.health = Objects.requireNonNull(health, "health");
    }

    @Override
    public ChannelKind kind() {
        return channel.kind();
    }

    @Override
    public SendResult send(OutgoingMessage message) {
        SendResult result;
        try {
            result = channel.send(message);
        } catch (RuntimeException fault) {
            health.record(message.destination(), kind(), SendResult.failed(FailureCode.SENDER_FAILED));
            throw fault;
        }
        health.record(message.destination(), kind(), result);
        return result;
    }
}
