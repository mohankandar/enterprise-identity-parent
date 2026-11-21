package com.documentplatform.notifications;

/** How often an event is sent. Anything except {@link #IMMEDIATELY} is a summary of everything since the last one. */
public enum Frequency {
    IMMEDIATELY,
    EVERY_HOUR,
    EVERY_4_HOURS,
    ONCE_A_DAY
}
