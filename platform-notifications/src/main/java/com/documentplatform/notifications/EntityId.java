package com.documentplatform.notifications;

/** Entity id: letters, digits, dot, underscore and hyphen, at most 128 characters. */
public record EntityId(String value) {

    public EntityId {
        Text.require(value, Text.ID, "Entity id");
    }

    public static EntityId of(String value) {
        return new EntityId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
