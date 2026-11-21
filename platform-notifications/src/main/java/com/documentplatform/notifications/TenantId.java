package com.documentplatform.notifications;

/** Tenant id: letters, digits, dot, underscore and hyphen, at most 128 characters. */
public record TenantId(String value) {

    public TenantId {
        Text.require(value, Text.ID, "Tenant id");
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
