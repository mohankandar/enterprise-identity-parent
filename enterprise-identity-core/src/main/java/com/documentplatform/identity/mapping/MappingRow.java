package com.documentplatform.identity.mapping;

import com.documentplatform.identity.admission.EntityId;

/** One validated grant from a role mapping file: in this entity, members of this group get this role. */
public record MappingRow(int line, EntityId entity, String entityName, String group, String role) {

    public Grant grant() {
        return new Grant(group, role);
    }
}
