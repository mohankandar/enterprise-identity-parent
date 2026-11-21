package com.documentplatform.notifications;

/** What a message says. */
public enum MessageKind {
    /** A one-off event. */
    EVENT,
    /** A continuous condition passed its threshold. */
    CONDITION_ALERT,
    /** The condition is still going on (repeat alert). */
    CONDITION_ONGOING,
    /** The condition cleared after it had been alerted. */
    RECOVERED,
    /** Everything since the last summary. */
    SUMMARY
}
