package com.documentplatform.notifications;

/** Who chooses and routes the event. */
public enum EventKind {
    /** Work, approvals, licence/usage, security. Chosen per entity by the Business Admin. */
    BUSINESS,
    /** System health, intake, delivery. Chosen and routed at tenant level by the IT Admin. */
    TECHNICAL
}
