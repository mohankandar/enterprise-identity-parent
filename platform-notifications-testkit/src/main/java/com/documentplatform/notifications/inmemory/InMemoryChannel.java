package com.documentplatform.notifications.inmemory;

import com.documentplatform.notifications.Channel;
import com.documentplatform.notifications.ChannelKind;
import com.documentplatform.notifications.FailureCode;
import com.documentplatform.notifications.OutgoingMessage;
import com.documentplatform.notifications.SendResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A channel that keeps what it is given. Can be told to fail with a code (and a Retry-After), for tests of failure
 * reporting and retries. For tests.
 */
public final class InMemoryChannel implements Channel {

    private final ChannelKind kind;
    private final List<OutgoingMessage> sent = new ArrayList<>();
    private FailureCode failWith;
    private Duration retryAfter;
    private int attempts;

    public InMemoryChannel(ChannelKind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    @Override
    public ChannelKind kind() {
        return kind;
    }

    @Override
    public synchronized SendResult send(OutgoingMessage message) {
        attempts++;
        if (failWith != null) {
            return SendResult.failed(failWith, retryAfter);
        }
        sent.add(message);
        return SendResult.ok();
    }

    /** Every later send fails with this code, until {@link #succeed()}. */
    public synchronized void failWith(FailureCode code) {
        failWith(code, null);
    }

    /** Every later send fails with this code and Retry-After, until {@link #succeed()}. */
    public synchronized void failWith(FailureCode code, Duration retryAfter) {
        this.failWith = Objects.requireNonNull(code, "code");
        this.retryAfter = retryAfter;
    }

    public synchronized void succeed() {
        this.failWith = null;
        this.retryAfter = null;
    }

    public synchronized List<OutgoingMessage> sent() {
        return List.copyOf(sent);
    }

    /** Every send call, failed or not. */
    public synchronized int attempts() {
        return attempts;
    }

    public synchronized void clear() {
        sent.clear();
        attempts = 0;
    }
}
