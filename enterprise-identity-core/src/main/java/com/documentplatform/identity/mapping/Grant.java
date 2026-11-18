package com.documentplatform.identity.mapping;

import java.util.Objects;

/** One group-to-role grant inside one entity. Values are exact: no trimming, no case folding. */
public record Grant(String group, String role) {
    public Grant {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(role, "role");
    }
}
