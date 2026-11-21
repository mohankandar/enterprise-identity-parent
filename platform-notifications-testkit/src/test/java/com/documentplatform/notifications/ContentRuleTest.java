package com.documentplatform.notifications;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.documentplatform.notifications.Fixture.APPROVAL_WAITING;
import static com.documentplatform.notifications.Fixture.E1;
import static com.documentplatform.notifications.Fixture.FILES_REJECTED;
import static com.documentplatform.notifications.Fixture.TEAMS;
import static com.documentplatform.notifications.Fixture.TENANT;
import static com.documentplatform.notifications.Fixture.event;
import static com.documentplatform.notifications.NotificationException.Reason.INVALID_VALUE;
import static com.documentplatform.notifications.NotificationException.Reason.UNKNOWN_FIELD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The content rule: a message carries the event's name, the record's identifier, counts/times and a link — never
 * document content, redacted values, sensitive fields, passwords, keys, tokens or webhook URLs.
 */
class ContentRuleTest {

    private static final Set<Class<?>> STRUCTURED = Set.of(EventTypeId.class, TenantId.class, EntityId.class,
            RecordIdentifier.class, AppLink.class, Instant.class, Map.class);

    @Test
    void thePublishApiHasNoFreeTextField() {
        for (RecordComponent c : NotificationEvent.class.getRecordComponents()) {
            assertTrue(STRUCTURED.contains(c.getType()), "unexpected field type: " + c.getName());
        }
        List<String> stringSetters = Arrays.stream(NotificationEvent.Builder.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> Arrays.asList(m.getParameterTypes()).contains(String.class))
                .map(Method::getName).sorted().toList();
        // Only the names of declared counts and times are strings, and those must be declared by the event type.
        assertEquals(List.of("count", "time"), stringSetters);
    }

    @ParameterizedTest
    @ValueSource(strings = {"double  space", "line\nbreak", "password=hunter2", "user@example.test",
            "https://hooks.example.test/x", "a?b", "a&b", "100%", "x+y", "", " leading", "trailing ", " ",
            "tab\tinside", "an-identifier-that-is-far-too-long-to-be-any-record-number-at-all-really",
            "an identifier that is far too long to be any record number at all"})
    void identifiersRefuseTextThatIsNotAnIdentifier(String value) {
        NotificationException e = assertThrows(NotificationException.class, () -> RecordIdentifier.of(value));
        assertEquals(INVALID_VALUE, e.reason());
        if (!value.isBlank()) {
            assertFalse(e.getMessage().contains(value), "a refusal must not repeat the refused value");
        }
    }

    @Test
    void identifiersAcceptRecordNumbers() {
        for (String ok : new String[]{"C-1001", "2026/000123", "INV.77#3", "case:42", "LN 12345", "A 1 B 2",
                "x".repeat(64)}) {
            assertEquals(ok, RecordIdentifier.of(ok).value());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example.test/x", "cases/1", "/cases/1?token=abc", "/cases/1#frag",
            "//evil.example.test/x", "/a/../b", "/a b", "/a%20b"})
    void linksArePathsInsideTheAppOnly(String value) {
        assertEquals(INVALID_VALUE, assertThrows(NotificationException.class, () -> AppLink.of(value)).reason());
    }

    @Test
    void undeclaredOrMistypedFieldsAreRefused() {
        Fixture f = new Fixture();
        assertEquals(UNKNOWN_FIELD, assertThrows(NotificationException.class,
                () -> f.notifier.publish(event(FILES_REJECTED).count("reason", 1).build())).reason());
        assertEquals(UNKNOWN_FIELD, assertThrows(NotificationException.class,
                () -> f.notifier.publish(event(FILES_REJECTED).time("files", Instant.EPOCH).build())).reason());
        assertEquals(INVALID_VALUE, assertThrows(NotificationException.class,
                () -> event(FILES_REJECTED).count("files", -1).build()).reason());
        assertEquals(INVALID_VALUE, assertThrows(NotificationException.class,
                () -> event(FILES_REJECTED).count("secret value", 1).build()).reason());
    }

    @Test
    void aMessageHoldsOnlyTheNameIdentifierFieldsAndLink() {
        Fixture f = new Fixture();
        f.notifier.publish(event(APPROVAL_WAITING).entity(E1).identifier(RecordIdentifier.of("C-1001"))
                .time("due", Instant.parse("2026-11-21T09:00:00Z")).link(AppLink.of("/approvals/C-1001")).build());
        Message m = f.email.sent().get(0).message();
        assertEquals("""
                Approval waiting: C-1001
                Time: 2026-11-20 10:05 Z
                Due: 2026-11-21 09:00 Z
                https://app.example.test/approvals/C-1001""", m.text());
    }

    @Test
    void destinationsAndAddressesNeverReachAMessage() {
        Fixture f = new Fixture();
        f.settings.setTechnicalEvent(TENANT, FILES_REJECTED,
                EventSettings.defaultsFor(f.settings.catalogue().require(FILES_REJECTED))
                        .withSchedule(Schedule.immediately()).withDestinations(TEAMS, Fixture.MAIL));
        f.notifier.publish(event(FILES_REJECTED).identifier(RecordIdentifier.of("batch-7")).count("files", 3).build());
        String text = f.teams.sent().get(0).message().text() + f.email.sent().get(0).message().text();
        for (String forbidden : List.of(TEAMS.value(), Fixture.MAIL.value(), Fixture.BOB.emailAddress(),
                Fixture.BOB.userId(), TENANT.value())) {
            assertFalse(text.contains(forbidden), forbidden);
        }
        assertFalse(Fixture.BOB.toString().contains(Fixture.BOB.emailAddress()));
    }

    @Test
    void failureReasonsAreSpecificAndFixed() {
        assertEquals("Teams webhook address no longer valid", FailureCode.WEBHOOK_ADDRESS_INVALID.describe(ChannelKind.TEAMS));
        assertEquals("Mail server refused the login", FailureCode.MAIL_LOGIN_REFUSED.describe(ChannelKind.EMAIL));
        for (FailureCode code : FailureCode.values()) {
            String text = code.describe(ChannelKind.SLACK).toLowerCase();
            assertFalse(text.contains("error occurred") || text.contains("%s"), code.name());
        }
    }
}
