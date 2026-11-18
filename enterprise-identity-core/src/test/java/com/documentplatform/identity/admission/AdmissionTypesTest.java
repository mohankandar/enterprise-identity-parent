package com.documentplatform.identity.admission;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdmissionTypesTest {

    @Test
    void entityIdWrapsANonNullUuid() {
        UUID value = UUID.randomUUID();
        assertEquals(EntityId.of(value), new EntityId(value));
        assertEquals(value.toString(), EntityId.of(value).toString());
        assertThrows(NullPointerException.class, () -> EntityId.of(null));
    }

    @Test
    void connectionKeyPassesValuesThroughUnchanged() {
        ConnectionKey key = new ConnectionKey(" p ", URI.create("https://idp.test/realms/a/"), " aud ");
        assertEquals(" p ", key.providerId());
        assertEquals("https://idp.test/realms/a/", key.issuer().toString());
        assertEquals(" aud ", key.audience());
    }

    @Test
    void admissionExceptionCarriesItsReasonAndIsNotAnIllegalArgument() {
        IdentityAdmissionException ex = new IdentityAdmissionException(IdentityAdmissionException.Reason.AMBIGUOUS_SELECTION, "m");
        assertSame(IdentityAdmissionException.Reason.AMBIGUOUS_SELECTION, ex.reason());
        assertEquals("m", ex.getMessage());
        assertFalse(IllegalArgumentException.class.isAssignableFrom(IdentityAdmissionException.class));
        assertThrows(NullPointerException.class, () -> new IdentityAdmissionException(null, "m"));
    }

    @Test
    void settingsAreAPlainValue() {
        AdmissionSettings settings = new AdmissionSettings(false, true, "p", "aud");
        assertFalse(settings.sharedIssuerSelectionEnabled());
        assertEquals("aud", settings.audience());
    }
}
