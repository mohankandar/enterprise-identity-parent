package com.documentplatform.notifications;

/** Destination id: letters, digits, dot, underscore and hyphen, at most 128 characters. */
public record DestinationId(String value) {

    public DestinationId {
        Text.require(value, Text.ID, "Destination id");
    }

    public static DestinationId of(String value) {
        return new DestinationId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
