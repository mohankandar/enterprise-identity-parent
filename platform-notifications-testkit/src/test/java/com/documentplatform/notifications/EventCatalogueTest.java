package com.documentplatform.notifications;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static com.documentplatform.notifications.Fixture.IT_ADMIN;
import static com.documentplatform.notifications.Fixture.OPS;
import static com.documentplatform.notifications.NotificationException.Reason.CRITICAL_FREQUENCY;
import static com.documentplatform.notifications.NotificationException.Reason.CRITICAL_MUST_BE_ON;
import static com.documentplatform.notifications.NotificationException.Reason.CRITICAL_NEEDS_IT_ADMIN;
import static com.documentplatform.notifications.NotificationException.Reason.DUPLICATE_EVENT_TYPE;
import static com.documentplatform.notifications.NotificationException.Reason.INVALID_VALUE;
import static com.documentplatform.notifications.NotificationException.Reason.NO_RECIPIENT_ROLES;
import static com.documentplatform.notifications.NotificationException.Reason.TIMING_OUT_OF_RANGE;
import static com.documentplatform.notifications.NotificationException.Reason.UNKNOWN_EVENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventCatalogueTest {

    private static EventType.Builder critical() {
        return EventType.builder("svc.down", "Service down", EventKind.TECHNICAL).critical().recipients(IT_ADMIN);
    }

    private static NotificationException.Reason refusal(EventType type) {
        return assertThrows(NotificationException.class, () -> EventCatalogue.builder(IT_ADMIN).register(type)).reason();
    }

    @Test
    void registersTypesInOrderAndFindsThem() {
        EventCatalogue c = Fixture.catalogue();
        assertEquals(9, c.all().size());
        assertEquals(Fixture.ITEMS_WAITING, c.all().iterator().next().id());
        assertEquals("Store down", c.require(Fixture.STORE_DOWN).name());
        assertTrue(c.find(EventTypeId.of("not.there")).isEmpty());
        assertEquals(UNKNOWN_EVENT_TYPE,
                assertThrows(NotificationException.class, () -> c.require(EventTypeId.of("not.there"))).reason());
    }

    @Test
    void criticalDefaultsToImmediatelyAndOn() {
        EventType t = critical().build();
        assertEquals(Schedule.immediately(), t.defaults().schedule());
        assertTrue(t.defaults().enabled());
        EventCatalogue.builder(IT_ADMIN).register(t).build();
    }

    @Test
    void criticalMayDefaultToEveryHour() {
        EventCatalogue.builder(IT_ADMIN).register(critical().schedule(Schedule.everyHour()).build()).build();
    }

    @Test
    void criticalSlowerThanEveryHourIsRefused() {
        assertEquals(CRITICAL_FREQUENCY, refusal(critical().schedule(Schedule.every4Hours()).build()));
        assertEquals(CRITICAL_FREQUENCY, refusal(critical().schedule(Schedule.onceADay(LocalTime.NOON)).build()));
    }

    @Test
    void criticalOffByDefaultIsRefused() {
        assertEquals(CRITICAL_MUST_BE_ON, refusal(critical().offByDefault().build()));
    }

    @Test
    void criticalMustGoToTheItAdmin() {
        EventType t = EventType.builder("svc.down", "Service down", EventKind.TECHNICAL).critical().recipients(OPS).build();
        assertEquals(CRITICAL_NEEDS_IT_ADMIN, refusal(t));
    }

    @Test
    void everyTypeNeedsARecipientRole() {
        EventType t = EventType.builder("work.x", "Work", EventKind.BUSINESS).schedule(Schedule.everyHour()).build();
        assertEquals(NO_RECIPIENT_ROLES, refusal(t));
    }

    @Test
    void nonCriticalMustNameItsSchedule() {
        assertEquals(INVALID_VALUE, assertThrows(NotificationException.class,
                () -> EventType.builder("work.x", "Work", EventKind.BUSINESS).recipients(OPS).build()).reason());
    }

    @Test
    void duplicateIdIsRefused() {
        EventCatalogue.Builder b = EventCatalogue.builder(IT_ADMIN).register(critical().build());
        assertEquals(DUPLICATE_EVENT_TYPE,
                assertThrows(NotificationException.class, () -> b.register(critical().build())).reason());
    }

    @Test
    void continuousTypesDefaultTo15MinutesAnd4Hours() {
        EventType t = critical().continuous().build();
        assertTrue(t.continuous());
        assertEquals(Duration.ofMinutes(15), t.defaults().conditionTiming().alertAfter());
        assertEquals(Duration.ofHours(4), t.defaults().conditionTiming().repeatEvery());
        assertFalse(critical().build().continuous());
        assertNull(critical().build().defaults().conditionTiming());
    }

    @Test
    void criticalConditionMustAlertWithinTheHour() {
        EventType slow = critical().continuous(new ConditionTiming(Duration.ofMinutes(61), Duration.ofHours(4))).build();
        assertEquals(TIMING_OUT_OF_RANGE, refusal(slow));
        EventType ok = EventType.builder("intake.x", "Intake", EventKind.TECHNICAL).recipients(OPS)
                .schedule(Schedule.immediately())
                .continuous(new ConditionTiming(Duration.ofHours(2), Duration.ofHours(4))).build();
        EventCatalogue.builder(IT_ADMIN).register(ok).build();
    }

    @Test
    void repeatIntervalHasBounds() {
        assertEquals(TIMING_OUT_OF_RANGE,
                refusal(critical().continuous(new ConditionTiming(Duration.ZERO, Duration.ofMinutes(1))).build()));
        assertEquals(TIMING_OUT_OF_RANGE,
                refusal(critical().continuous(new ConditionTiming(Duration.ZERO, Duration.ofHours(25))).build()));
    }

    @Test
    void labelsAndFieldNamesAreChecked() {
        assertEquals(INVALID_VALUE, assertThrows(NotificationException.class,
                () -> EventType.builder("a.b", "Bad\nname", EventKind.BUSINESS).schedule(Schedule.everyHour())
                        .recipients(OPS).build()).reason());
        assertThrows(NotificationException.class,
                () -> EventType.builder("a.b", "<b>name</b>", EventKind.BUSINESS).schedule(Schedule.everyHour())
                        .recipients(OPS).build());
        assertThrows(NotificationException.class, () -> FieldSpec.count("Bad Name", "Label"));
        assertThrows(NotificationException.class,
                () -> EventType.builder("a.b", "Name", EventKind.BUSINESS).schedule(Schedule.everyHour())
                        .recipients(OPS).count("n", "N").time("n", "N again").build());
    }

    @Test
    void thresholdDefaultMustLieInItsRange() {
        assertThrows(NotificationException.class, () -> new NumericThreshold("pct", "Percent", 120, 0, 100));
        assertEquals(List.of(new NumericThreshold("pct", "Percent", 80, 0, 100)),
                EventType.builder("a.b", "Name", EventKind.BUSINESS).schedule(Schedule.everyHour()).recipients(OPS)
                        .threshold("pct", "Percent", 80, 0, 100).build().thresholds());
    }
}
