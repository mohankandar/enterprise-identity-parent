package com.documentplatform.identity.admission;

import java.util.Objects;

/**
 * A sign-in or entity choice was refused. Always fail closed: the product maps every reason to a refusal of the
 * request, never to "allowed". The message is safe for logs; whether it is shown to users is the product's choice.
 *
 * <p>Deliberately not an {@link IllegalArgumentException}, so that callers parsing input cannot swallow it by accident.
 */
public final class IdentityAdmissionException extends RuntimeException {

    /** Why the admission was refused. Lets products choose a status and a safe message per reason. */
    public enum Reason {
        /** Shared-issuer selection is switched off, or connections are not persisted. */
        NOT_ENABLED,
        /** The verified identity has no admissible entity, or verified membership is not backed by an ACTIVE connection. */
        NO_VERIFIED_MEMBERSHIP,
        /** More than one entity is admissible and none was chosen. */
        AMBIGUOUS_SELECTION,
        /** The chosen entity is not one the verified identity may enter. */
        NOT_AUTHORIZED_FOR_ENTITY,
        /** The entity choice could not be read. */
        INVALID_SELECTION,
        /** The identity matched an entity's groups but no role resulted. */
        NO_ROLE_FOR_ENTITY,
        /** A grant names a role the product does not know, or a grant is empty. */
        INVALID_ROLE_GRANT,
        /** A grant gives the product's platform role through an entity group. */
        PLATFORM_ROLE_IN_ENTITY_GRANT,
        /** The admission policy or the ACTIVE bindings could not be read, or the groups claim is not a collection. */
        POLICY_UNAVAILABLE,
        /** A machine identity is bound to no entity, or to more than one. */
        MACHINE_BINDING_NOT_UNIQUE
    }

    private final Reason reason;

    public IdentityAdmissionException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
