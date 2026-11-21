package com.documentplatform.notifications;

/** Event type id: letters, digits, dot, underscore and hyphen, at most 128 characters. */
public record EventTypeId(String value) {

    public EventTypeId {
        Text.require(value, Text.ID, "Event type id");
    }

    public static EventTypeId of(String value) {
        return new EventTypeId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
