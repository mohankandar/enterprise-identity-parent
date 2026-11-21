package com.documentplatform.notifications;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static com.documentplatform.notifications.Fixture.BOB;
import static com.documentplatform.notifications.Fixture.CY;
import static com.documentplatform.notifications.Fixture.E1;
import static com.documentplatform.notifications.Fixture.E2;
import static com.documentplatform.notifications.Fixture.FILES_REJECTED;
import static com.documentplatform.notifications.Fixture.ITEMS_WAITING;
import static com.documentplatform.notifications.Fixture.QUOTA;
import static com.documentplatform.notifications.Fixture.TEAMS;
import static com.documentplatform.notifications.Fixture.TENANT;
import static com.documentplatform.notifications.Fixture.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryTest {

    private final Fixture f = new Fixture();

    private static NotificationEvent rejected(String batch, long files) {
        return event(FILES_REJECTED).identifier(RecordIdentifier.of(batch)).count("files", files)
                .link(AppLink.of("/intake/" + batch)).build();
    }

    @Test
    void hourlyEventsWaitForTheNextWholeHourAndGoAsOneSummary() {
        assertEquals(DeliveryStatus.QUEUED_FOR_SUMMARY, f.notifier.publish(rejected("b-1", 2)).get(0).status());
        f.clock.advance(Duration.ofMinutes(20));
        f.notifier.publish(rejected("b-2", 3));
        assertTrue(f.notifier.tick(Instant.parse("2026-11-20T10:59:59Z")).isEmpty());
        assertTrue(f.email.sent().isEmpty());

        List<DeliveryReport> reports = f.notifier.tick(Instant.parse("2026-11-20T11:00:00Z"));
        assertEquals(1, reports.size());
        assertEquals(MessageKind.SUMMARY, reports.get(0).kind());
        assertEquals(DeliveryStatus.SENT, reports.get(0).status());
        assertEquals(Set.of(FILES_REJECTED), reports.get(0).eventTypes());

        OutgoingMessage out = f.email.sent().get(0);
        assertEquals(Set.of(BOB), out.recipients());
        assertEquals("""
                Summary: 2 notifications
                Period: 2026-11-20 10:05 Z to 2026-11-20 10:25 Z
                Files rejected: 2
                  Files (total): 5
                  - b-1, 2026-11-20 10:05 Z https://app.example.test/intake/b-1
                  - b-2, 2026-11-20 10:25 Z https://app.example.test/intake/b-2
                https://app.example.test/""", out.message().text());
    }

    @Test
    void aSentSummaryIsNotSentAgainAndTheNextHoldsOnlyNewItems() {
        f.notifier.publish(rejected("b-1", 1));
        f.notifier.tick(Instant.parse("2026-11-20T11:00:00Z"));
        assertTrue(f.notifier.tick(Instant.parse("2026-11-20T12:00:00Z")).isEmpty());
        assertEquals(1, f.email.sent().size());
        assertEquals(0, f.notifier.pendingSummaryItems());

        f.clock.set(Instant.parse("2026-11-20T12:10:00Z"));
        f.notifier.publish(rejected("b-2", 1));
        f.notifier.tick(Instant.parse("2026-11-20T13:00:00Z"));
        String second = f.email.sent().get(1).message().text();
        assertTrue(second.contains("b-2") && !second.contains("b-1"), second);
    }

    @Test
    void every4HoursGoesAtTheNext4HourMarkPerEntity() {
        f.notifier.publish(event(ITEMS_WAITING).entity(E1).identifier(RecordIdentifier.of("C-1")).count("items", 4).build());
        f.notifier.publish(event(ITEMS_WAITING).entity(E2).identifier(RecordIdentifier.of("C-2")).count("items", 1).build());
        assertTrue(f.notifier.tick(Instant.parse("2026-11-20T11:00:00Z")).isEmpty());
        List<DeliveryReport> reports = f.notifier.tick(Instant.parse("2026-11-20T12:00:00Z"));
        // E1's reviewers get E1's summary; E2 has no reviewers
        assertEquals(List.of(DeliveryStatus.SENT, DeliveryStatus.FAILED),
                reports.stream().map(DeliveryReport::status).toList());
        assertEquals(FailureCode.NO_RECIPIENTS, reports.get(1).failure());
        assertEquals(Set.of(BOB, CY), f.email.sent().get(0).recipients());
        assertTrue(f.email.sent().get(0).message().text().contains("C-1"));
        assertTrue(!f.email.sent().get(0).message().text().contains("C-2"));
    }

    @Test
    void dailySummaryGoesAtTheChosenTimeInTheTenantsZone() {
        Fixture amsterdam = new Fixture(ZoneId.of("Europe/Amsterdam"));
        amsterdam.notifier.publish(event(QUOTA).entity(E1).count("percent", 85).build());
        assertTrue(amsterdam.notifier.tick(Instant.parse("2026-11-21T06:59:00Z")).isEmpty());
        assertEquals(DeliveryStatus.SENT,
                amsterdam.notifier.tick(Instant.parse("2026-11-21T07:00:00Z")).get(0).status());
        assertTrue(amsterdam.email.sent().get(0).message().text().contains("Period: 2026-11-20 11:05 CET"));
    }

    @Test
    void eachDestinationGetsItsOwnSummary() {
        f.settings.setTechnicalEvent(TENANT, FILES_REJECTED,
                EventSettings.defaultsFor(f.settings.catalogue().require(FILES_REJECTED))
                        .withDestinations(TEAMS, Fixture.MAIL));
        f.notifier.publish(rejected("b-1", 1));
        assertEquals(2, f.notifier.tick(Instant.parse("2026-11-20T11:00:00Z")).size());
        assertEquals(1, f.teams.sent().size());
        assertEquals(1, f.email.sent().size());
    }

    @Test
    void longSummariesListTenItemsPerTypeAndCountTheRest() {
        for (int i = 1; i <= 13; i++) {
            f.notifier.publish(rejected("b-" + i, 1));
        }
        f.notifier.tick(Instant.parse("2026-11-20T11:00:00Z"));
        String text = f.email.sent().get(0).message().text();
        assertTrue(text.startsWith("Summary: 13 notifications"));
        assertTrue(text.contains("Files (total): 13"));
        assertTrue(text.contains("b-10,") && !text.contains("b-11,"));
        assertTrue(text.contains("  ... and 3 more"));
    }

    @Test
    void nothingPublishedMeansNoSummary() {
        assertTrue(f.advance(Duration.ofDays(2)).isEmpty());
        assertTrue(f.email.sent().isEmpty());
    }
}
