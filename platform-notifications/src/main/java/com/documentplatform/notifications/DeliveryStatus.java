package com.documentplatform.notifications;

/** What happened to an event (or a summary) for one destination. */
public enum DeliveryStatus {
    /** Handed to the channel. */
    SENT,
    /** Held for the next summary. */
    QUEUED_FOR_SUMMARY,
    /** Saved in the outbox and not sent yet: another worker is sending it. */
    PENDING,
    /** A temporary failure (see the failure code); saved in the outbox and tried again later. */
    RETRY_SCHEDULED,
    /** The event type is switched off for this tenant or entity. */
    DISABLED,
    /** The tenant has no connected destination for it. */
    NO_DESTINATION,
    /** Not delivered and not tried again: a permanent failure, or retries ran out. See the failure code. */
    FAILED
}
