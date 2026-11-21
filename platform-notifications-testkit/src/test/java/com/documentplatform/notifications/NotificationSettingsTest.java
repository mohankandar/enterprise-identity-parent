package com.documentplatform.notifications;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static com.documentplatform.notifications.Fixture.APPROVER;
import static com.documentplatform.notifications.Fixture.DELIVERY_FAILING;
import static com.documentplatform.notifications.Fixture.E1;
import static com.documentplatform.notifications.Fixture.E2;
import static com.documentplatform.notifications.Fixture.FILES_REJECTED;
import static com.documentplatform.notifications.Fixture.ITEMS_WAITING;
import static com.documentplatform.notifications.Fixture.IT_ADMIN;
import static com.documentplatform.notifications.Fixture.LICENCE_EXPIRED;
import static com.documentplatform.notifications.Fixture.OPS;
import static com.documentplatform.notifications.Fixture.QUOTA;
import static com.documentplatform.notifications.Fixture.SOURCE_UNREACHABLE;
import static com.documentplatform.notifications.Fixture.STORE_DOWN;
import static com.documentplatform.notifications.Fixture.TEAMS;
import static com.documentplatform.notifications.Fixture.TENANT;
import static com.documentplatform.notifications.NotificationException.Reason.CRITICAL_FREQUENCY;
import static com.documentplatform.notifications.NotificationException.Reason.CRITICAL_MUST_BE_ON;
import static com.documentplatform.notifications.NotificationException.Reason.CRITICAL_NEEDS_IT_ADMIN;
import static com.documentplatform.notifications.NotificationException.Reason.ENTITY_REQUIRED;
import static com.documentplatform.notifications.NotificationException.Reason.INVALID_VALUE;
import static com.documentplatform.notifications.NotificationException.Reason.NOT_A_CONDITION;
import static com.documentplatform.notifications.NotificationException.Reason.THRESHOLD_OUT_OF_RANGE;
import static com.documentplatform.notifications.NotificationException.Reason.TIMING_OUT_OF_RANGE;
import static com.documentplatform.notifications.NotificationException.Reason.UNKNOWN_DESTINATION;
import static com.documentplatform.notifications.NotificationException.Reason.UNKNOWN_THRESHOLD;
import static com.documentplatform.notifications.NotificationException.Reason.WRONG_SETTINGS_LEVEL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationSettingsTest {

    private final Fixture f = new Fixture();
    private final NotificationSettings settings = f.settings;

    private EventSettings defaults(EventTypeId type) {
        return EventSettings.defaultsFor(settings.catalogue().require(type));
    }

    private NotificationException.Reason technicalRefusal(EventTypeId type, EventSettings s) {
        return assertThrows(NotificationException.class, () -> settings.setTechnicalEvent(TENANT, type, s)).reason();
    }

    @Test
    void defaultsApplyUntilAnAdminSavesSettings() {
        EventSettings s = settings.effective(TENANT, null, FILES_REJECTED);
        assertTrue(s.enabled());
        assertEquals(Schedule.everyHour(), s.schedule());
        assertEquals(Set.of(OPS), s.recipientRoles());
        assertEquals(Schedule.every4Hours(), settings.effective(TENANT, E1, ITEMS_WAITING).schedule());
        assertFalse(settings.effective(TENANT, E1, Fixture.LEVEL_LOWERED).enabled());
    }

    @Test
    void itAdminSetsTechnicalEventsForTheTenant() {
        settings.setTechnicalEvent(TENANT, FILES_REJECTED, defaults(FILES_REJECTED)
                .withSchedule(Schedule.onceADay(LocalTime.of(7, 30))).withDestinations(TEAMS));
        EventSettings s = settings.effective(TENANT, E1, FILES_REJECTED);
        assertEquals(Schedule.onceADay(LocalTime.of(7, 30)), s.schedule());
        assertEquals(Set.of(TEAMS), s.destinations());
    }

    @Test
    void businessAdminSetsBusinessEventsPerEntity() {
        settings.setBusinessEvent(TENANT, E1, ITEMS_WAITING, defaults(ITEMS_WAITING).withEnabled(false));
        assertFalse(settings.effective(TENANT, E1, ITEMS_WAITING).enabled());
        assertTrue(settings.effective(TENANT, E2, ITEMS_WAITING).enabled());
    }

    @Test
    void levelsAreKeptApart() {
        assertEquals(WRONG_SETTINGS_LEVEL, assertThrows(NotificationException.class,
                () -> settings.setTechnicalEvent(TENANT, ITEMS_WAITING, defaults(ITEMS_WAITING))).reason());
        assertEquals(WRONG_SETTINGS_LEVEL, assertThrows(NotificationException.class,
                () -> settings.setBusinessEvent(TENANT, E1, FILES_REJECTED, defaults(FILES_REJECTED))).reason());
        assertEquals(ENTITY_REQUIRED, assertThrows(NotificationException.class,
                () -> settings.effective(TENANT, null, ITEMS_WAITING)).reason());
    }

    @Test
    void criticalFrequencySetSlowerThanEveryHourIsRefused() {
        assertEquals(CRITICAL_FREQUENCY,
                technicalRefusal(STORE_DOWN, defaults(STORE_DOWN).withSchedule(Schedule.every4Hours())));
        assertEquals(CRITICAL_FREQUENCY,
                technicalRefusal(STORE_DOWN, defaults(STORE_DOWN).withSchedule(Schedule.onceADay(LocalTime.NOON))));
        assertEquals(CRITICAL_FREQUENCY, assertThrows(NotificationException.class,
                () -> settings.setBusinessEvent(TENANT, E1, LICENCE_EXPIRED,
                        defaults(LICENCE_EXPIRED).withSchedule(Schedule.every4Hours()))).reason());
        // nothing was saved
        assertEquals(Schedule.immediately(), settings.effective(TENANT, null, STORE_DOWN).schedule());
    }

    @Test
    void criticalMayBeSetToEveryHour() {
        settings.setTechnicalEvent(TENANT, STORE_DOWN, defaults(STORE_DOWN).withSchedule(Schedule.everyHour()));
        assertEquals(Schedule.everyHour(), settings.effective(TENANT, null, STORE_DOWN).schedule());
    }

    @Test
    void criticalCannotBeSwitchedOffOrLoseTheItAdmin() {
        assertEquals(CRITICAL_MUST_BE_ON, technicalRefusal(STORE_DOWN, defaults(STORE_DOWN).withEnabled(false)));
        assertEquals(CRITICAL_NEEDS_IT_ADMIN,
                technicalRefusal(DELIVERY_FAILING, defaults(DELIVERY_FAILING).withRecipientRoles(OPS)));
        settings.setTechnicalEvent(TENANT, DELIVERY_FAILING,
                defaults(DELIVERY_FAILING).withRecipientRoles(IT_ADMIN, OPS, APPROVER));
    }

    @Test
    void conditionTimingIsAdjustableWithinTheRules() {
        settings.setTechnicalEvent(TENANT, STORE_DOWN, defaults(STORE_DOWN)
                .withConditionTiming(new ConditionTiming(Duration.ofMinutes(5), Duration.ofHours(1))));
        assertEquals(Duration.ofMinutes(5), settings.effective(TENANT, null, STORE_DOWN).conditionTiming().alertAfter());
        assertEquals(TIMING_OUT_OF_RANGE, technicalRefusal(STORE_DOWN, defaults(STORE_DOWN)
                .withConditionTiming(new ConditionTiming(Duration.ofHours(2), Duration.ofHours(4)))));
        settings.setTechnicalEvent(TENANT, SOURCE_UNREACHABLE, defaults(SOURCE_UNREACHABLE)
                .withConditionTiming(new ConditionTiming(Duration.ofHours(2), Duration.ofHours(4))));
        assertEquals(NOT_A_CONDITION, technicalRefusal(FILES_REJECTED, defaults(FILES_REJECTED)
                .withConditionTiming(ConditionTiming.DEFAULT)));
        assertEquals(NOT_A_CONDITION, technicalRefusal(STORE_DOWN, defaults(STORE_DOWN).withConditionTiming(null)));
    }

    @Test
    void numericThresholdsAreAdjustableWithinTheirRange() {
        assertEquals(80, settings.threshold(TENANT, E1, QUOTA, "percent"));
        settings.setBusinessEvent(TENANT, E1, QUOTA, defaults(QUOTA).withThreshold("percent", 90));
        assertEquals(90, settings.threshold(TENANT, E1, QUOTA, "percent"));
        assertEquals(80, settings.threshold(TENANT, E2, QUOTA, "percent"));
        assertEquals(THRESHOLD_OUT_OF_RANGE, assertThrows(NotificationException.class,
                () -> settings.setBusinessEvent(TENANT, E1, QUOTA, defaults(QUOTA).withThreshold("percent", 101))).reason());
        assertEquals(UNKNOWN_THRESHOLD, assertThrows(NotificationException.class,
                () -> settings.setBusinessEvent(TENANT, E1, QUOTA, defaults(QUOTA).withThreshold("other", 1))).reason());
        assertEquals(UNKNOWN_THRESHOLD, assertThrows(NotificationException.class,
                () -> settings.threshold(TENANT, E1, QUOTA, "other")).reason());
    }

    @Test
    void destinationsMustBeConnected() {
        assertEquals(UNKNOWN_DESTINATION, technicalRefusal(FILES_REJECTED,
                defaults(FILES_REJECTED).withDestinations(DestinationId.of("not-connected"))));
        assertEquals(UNKNOWN_DESTINATION, assertThrows(NotificationException.class,
                () -> new NotificationSettings(Fixture.catalogue(), new com.documentplatform.notifications.inmemory.InMemorySettingsStore())
                        .setTechnicalEvent(TENANT, FILES_REJECTED, defaults(FILES_REJECTED).withDestinations(TEAMS))).reason());
    }

    @Test
    void savedSettingsThatBreakTheCurrentRulesFallBackToDefaults() {
        // e.g. saved before a release made the event critical: the store holds a slow schedule
        f.store.saveTechnical(TENANT, STORE_DOWN, defaults(STORE_DOWN).withSchedule(Schedule.onceADay(LocalTime.NOON)));
        assertEquals(Schedule.immediately(), settings.effective(TENANT, null, STORE_DOWN).schedule());
    }

    @Test
    void tenantAddressMustBeAPlainWebAddress() {
        for (String bad : new String[]{"ftp://app.example.test", "https://user:pw@app.example.test",
                "https://app.example.test/?token=x", "https://app.example.test/#x", "/relative"}) {
            assertEquals(INVALID_VALUE, assertThrows(NotificationException.class,
                    () -> new TenantSettings(URI.create(bad), ZoneOffset.UTC, Map.of())).reason(), bad);
        }
    }
}
