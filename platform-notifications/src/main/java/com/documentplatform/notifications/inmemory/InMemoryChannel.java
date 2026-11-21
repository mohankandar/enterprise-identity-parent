package com.documentplatform.notifications.inmemory;

import com.documentplatform.notifications.Channel;
import com.documentplatform.notifications.ChannelKind;
import com.documentplatform.notifications.FailureCode;
import com.documentplatform.notifications.OutgoingMessage;
import com.documentplatform.notifications.SendResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A channel that keeps what it is given. Can be told to fail with a code, for tests of failure reporting. */
public final class InMemoryChannel implements Channel {

    private final ChannelKind kind;
    private final List<OutgoingMessage> sent = new ArrayList<>();
    private FailureCode failWith;

    public InMemoryChannel(ChannelKind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    @Override
    public ChannelKind kind() {
        return kind;
    }

    @Override
    public synchronized SendResult send(OutgoingMessage message) {
        if (failWith != null) {
            return SendResult.failed(failWith);
        }
        sent.add(message);
        return SendResult.ok();
    }

    /** Every later send fails with this code, until {@link #succeed()}. */
    public synchronized void failWith(FailureCode code) {
        this.failWith = Objects.requireNonNull(code, "code");
    }

    public synchronized void succeed() {
        this.failWith = null;
    }

    public synchronized List<OutgoingMessage> sent() {
        return List.copyOf(sent);
    }

    public synchronized void clear() {
        sent.clear();
    }
}
