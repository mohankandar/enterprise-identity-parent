package com.documentplatform.notifications;

/** Role id: letters, digits, dot, underscore and hyphen, at most 128 characters. */
public record RoleId(String value) {

    public RoleId {
        Text.require(value, Text.ID, "Role id");
    }

    public static RoleId of(String value) {
        return new RoleId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
