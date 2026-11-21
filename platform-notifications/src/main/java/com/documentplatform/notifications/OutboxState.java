package com.documentplatform.notifications;

/** Where a message is in the outbox. */
public enum OutboxState {
    /** Waiting for its (next) attempt at {@code nextAttemptAt}. */
    PENDING,
    /** Claimed by a worker until its lease ends. A lease that ran out means the worker stopped: it is tried again. */
    SENDING,
    /** Delivered to the channel. Final. */
    SENT,
    /** Permanently failed, or retries ran out. Final. */
    FAILED
}
