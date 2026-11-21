package com.documentplatform.notifications;

import java.util.Objects;

/**
 * A person who holds a recipient role, as the app's {@link RecipientResolver} returns them. {@link #toString()} shows
 * the user id only, so the address does not end up in logs.
 */
public record Recipient(String userId, String emailAddress) {

    public Recipient {
        Text.require(userId, Text.ID, "User id");
        Objects.requireNonNull(emailAddress, "emailAddress");
        int at = emailAddress.indexOf('@');
        if (at <= 0 || at != emailAddress.lastIndexOf('@') || at == emailAddress.length() - 1
                || emailAddress.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new NotificationException(NotificationException.Reason.INVALID_VALUE, "E-mail address is not valid");
        }
    }

    @Override
    public String toString() {
        return "Recipient[" + userId + "]";
    }
}
