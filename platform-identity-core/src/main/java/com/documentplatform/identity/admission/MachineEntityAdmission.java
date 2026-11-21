package com.documentplatform.identity.admission;

import java.util.Set;

import static com.documentplatform.identity.admission.IdentityAdmissionException.Reason.MACHINE_BINDING_NOT_UNIQUE;

/** A machine identity must be bound to exactly one ACTIVE entity integration. There is no choice for machines. */
public final class MachineEntityAdmission {

    public EntityId requireUnique(Set<EntityId> candidates) {
        if (candidates == null || candidates.size() != 1) {
            throw new IdentityAdmissionException(MACHINE_BINDING_NOT_UNIQUE,
                    "The machine identity is not bound to exactly one entity");
        }
        EntityId selected = candidates.iterator().next();
        if (selected == null) {
            throw new IdentityAdmissionException(MACHINE_BINDING_NOT_UNIQUE,
                    "The machine identity is not bound to exactly one entity");
        }
        return selected;
    }
}
