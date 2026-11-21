package com.documentplatform.notifications;

import java.util.regex.Pattern;

/**
 * The only shapes of text the module accepts. Everything that can reach a message is either a catalogue label (fixed
 * by the app at registration) or one of these narrow values. Refusals never repeat the refused value, because it may
 * be exactly the sensitive text the rule keeps out.
 */
final class Text {

    /** Ids: tenant, entity, event type, role, destination. */
    static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    /**
     * A record's primary identifier, such as a contract number: at most 64 characters, single spaces inside only (no
     * leading, trailing or double spaces), no '=', '@', '?', '&', '%', '+'.
     */
    static final Pattern IDENTIFIER =
            Pattern.compile("(?=.{1,64}\\z)[A-Za-z0-9][A-Za-z0-9._:/#-]*(?: [A-Za-z0-9._:/#-]+)*");

    /** Event names and field labels, fixed by the app's catalogue. One line, no markup characters. */
    static final Pattern LABEL = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} ().,'/%&-]{0,79}");

    /** Names of declared count, time and threshold fields. */
    static final Pattern FIELD_NAME = Pattern.compile("[a-z][A-Za-z0-9]{0,31}");

    /** A path inside the app. No scheme, host, query or fragment. */
    static final Pattern PATH = Pattern.compile("/[A-Za-z0-9/_.-]{0,255}");

    private Text() {
    }

    static String require(String value, Pattern pattern, String what) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new NotificationException(NotificationException.Reason.INVALID_VALUE, what + " is not valid");
        }
        return value;
    }
}
