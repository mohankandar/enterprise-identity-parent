package com.documentplatform.notifications;

import java.util.Objects;

/**
 * A count or time an event type carries, declared by the app in its catalogue. Events may fill declared fields only,
 * with numbers or instants, so the label is the only text and it is fixed at registration.
 */
public record FieldSpec(String name, String label, Type type) {

    public enum Type {
        COUNT,
        TIME
    }

    public FieldSpec {
        Text.require(name, Text.FIELD_NAME, "Field name");
        Text.require(label, Text.LABEL, "Field label");
        Objects.requireNonNull(type, "type");
    }

    public static FieldSpec count(String name, String label) {
        return new FieldSpec(name, label, Type.COUNT);
    }

    public static FieldSpec time(String name, String label) {
        return new FieldSpec(name, label, Type.TIME);
    }
}
