package com.documentplatform.notifications;

/**
 * The record's primary identifier, such as a contract or case number: the only per-record text a message may carry.
 * At most 64 characters of letters, digits and {@code . _ : / # -}; no spaces, no {@code //}. This keeps sentences,
 * key=value pairs, e-mail addresses and URLs out, so a field value or a secret cannot be passed off as an identifier.
 */
public record RecordIdentifier(String value) {

    public RecordIdentifier {
        Text.require(value, Text.IDENTIFIER, "Record identifier");
        if (value.contains("//")) {
            throw new NotificationException(NotificationException.Reason.INVALID_VALUE, "Record identifier is not valid");
        }
    }

    public static RecordIdentifier of(String value) {
        return new RecordIdentifier(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
