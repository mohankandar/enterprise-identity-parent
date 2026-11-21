package com.documentplatform.identity.admission;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A grant with a role the product does not know (a typo, or a role removed from the product) refuses only its own
 * entity. Before 0.4.0 it refused every sign-in through the login system.
 */
class EntityMembershipUnknownRoleTest {
    private static final EntityId A = EntityId.of(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final EntityId B = EntityId.of(UUID.fromString("00000000-0000-0000-0000-00000000000b"));
    private static final URI ISSUER = URI.create("https://identity.example.test/realms/shared");
    private static final ConnectionKey KEY = new ConnectionKey("customer-oidc", ISSUER, "product-api");
    private static final AdmissionSettings SETTINGS = new AdmissionSettings(true, true, "customer-oidc", "product-api");

    private static EntityMembershipResolver resolver(Map<EntityId, Map<String, Set<String>>> grants) {
        return new EntityMembershipResolver(key -> KEY.equals(key) ? grants : Map.of(),
                key -> KEY.equals(key) ? Set.of(A, B) : Set.of(), new TestRoleCatalog(), SETTINGS);
    }

    private static final Map<EntityId, Map<String, Set<String>>> A_BROKEN = Map.of(
            A, Map.of("a-staff", Set.of("DOCUMENT_PROCESOR"), "a-admins", Set.of("BUSINESS_ADMIN")),
            B, Map.of("b-staff", Set.of("DOCUMENT_PROCESSOR")));

    @Test
    void theBrokenEntityIsRefusedAndThePersonsOtherEntitiesAreAdmitted() {
        var admitted = resolver(A_BROKEN).authorizedEntities(ISSUER, Set.of("a-staff", "b-staff"));
        assertEquals(Map.of(B, Set.of("DOCUMENT_PROCESSOR")), admitted);
    }

    @Test
    void choosingTheBrokenEntityIsRefused() {
        var resolver = resolver(A_BROKEN);
        var admitted = resolver.authorizedEntities(ISSUER, Set.of("a-staff", "b-staff"));
        var refused = assertThrows(IdentityAdmissionException.class, () -> resolver.select(A, admitted));
        assertEquals(IdentityAdmissionException.Reason.NOT_AUTHORIZED_FOR_ENTITY, refused.reason());
    }

    @Test
    void otherPeopleInTheSameEntityAreNotAffected() {
        var admitted = resolver(A_BROKEN).authorizedEntities(ISSUER, Set.of("a-admins"));
        assertEquals(Map.of(A, Set.of("BUSINESS_ADMIN")), admitted);
    }

    @Test
    void aPersonOnlyInTheBrokenGrantHasNoEntity() {
        assertEquals(Map.of(), resolver(A_BROKEN).authorizedEntities(ISSUER, Set.of("a-staff")));
    }

    @Test
    void thePlatformRoleInAnEntityGrantStillRefusesTheWholeSignIn() {
        var grants = Map.of(
                A, Map.of("a-staff", Set.of(TestRoleCatalog.PLATFORM)),
                B, Map.of("b-staff", Set.of("DOCUMENT_PROCESSOR")));
        var refused = assertThrows(IdentityAdmissionException.class,
                () -> resolver(grants).authorizedEntities(ISSUER, Set.of("a-staff", "b-staff")));
        assertEquals(IdentityAdmissionException.Reason.PLATFORM_ROLE_IN_ENTITY_GRANT, refused.reason());
    }
}
