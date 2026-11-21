package com.documentplatform.notifications;

/** What happened to an event (or a summary) for one destination. */
public enum DeliveryStatus {
    /** Handed to the channel. */
    SENT,
    /** Held for the next summary. */
    QUEUED_FOR_SUMMARY,
    /** The event type is switched off for this tenant or entity. */
    DISABLED,
    /** The tenant has no connected destination for it. */
    NO_DESTINATION,
    /** The channel or the recipient lookup failed; see the failure code. */
    FAILED
}
