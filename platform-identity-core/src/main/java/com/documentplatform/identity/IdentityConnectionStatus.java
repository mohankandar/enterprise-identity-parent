package com.documentplatform.identity;

/**
 * Lifecycle state for an enterprise identity connection. Only ACTIVE participates in authentication.
 */
public enum IdentityConnectionStatus {
    DRAFT,
    VALIDATED,
    ACTIVE,
    DISABLED
}
