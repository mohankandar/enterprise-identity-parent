package com.documentplatform.notifications.email;

/** How the connection to the customer's mail server is protected. */
public enum MailSecurity {
    /** Plain connection upgraded with STARTTLS, which is required (usually port 587). The recommended setting. */
    STARTTLS,
    /** TLS from the first byte, "SMTPS" (usually port 465). */
    TLS,
    /** No encryption. Only for a relay inside the customer's own network that offers nothing else (port 25). */
    NONE
}
