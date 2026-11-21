package com.documentplatform.notifications;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleTest {

    private static final ZoneId UTC = ZoneOffset.UTC;

    private static Instant at(String s) {
        return Instant.parse(s);
    }

    @Test
    void hourlyIsDueAtTheNextWholeHour() {
        assertEquals(at("2026-11-20T11:00:00Z"), Schedule.everyHour().nextDue(at("2026-11-20T10:05:00Z"), UTC));
        assertEquals(at("2026-11-20T11:00:00Z"), Schedule.everyHour().nextDue(at("2026-11-20T10:00:00Z"), UTC));
    }

    @Test
    void every4HoursIsDueAtTheNext4HourMark() {
        assertEquals(at("2026-11-20T12:00:00Z"), Schedule.every4Hours().nextDue(at("2026-11-20T10:05:00Z"), UTC));
        assertEquals(at("2026-11-20T16:00:00Z"), Schedule.every4Hours().nextDue(at("2026-11-20T12:00:00Z"), UTC));
        assertEquals(at("2026-11-21T00:00:00Z"), Schedule.every4Hours().nextDue(at("2026-11-20T23:59:00Z"), UTC));
    }

    @Test
    void dailyIsDueAtTheChosenTimeTodayOrTomorrow() {
        Schedule eight = Schedule.onceADay(LocalTime.of(8, 0));
        assertEquals(at("2026-11-20T08:00:00Z"), eight.nextDue(at("2026-11-20T07:59:00Z"), UTC));
        assertEquals(at("2026-11-21T08:00:00Z"), eight.nextDue(at("2026-11-20T08:00:00Z"), UTC));
        assertEquals(at("2026-11-21T08:00:00Z"), eight.nextDue(at("2026-11-20T10:05:00Z"), UTC));
    }

    @Test
    void dailyTimeIsInTheTenantsZone() {
        Schedule eight = Schedule.onceADay(LocalTime.of(8, 0));
        // 08:00 in Amsterdam in November is 07:00 UTC
        assertEquals(at("2026-11-21T07:00:00Z"), eight.nextDue(at("2026-11-20T10:05:00Z"), ZoneId.of("Europe/Amsterdam")));
    }

    @Test
    void onlyImmediatelyAndHourlyAreAllowedForCritical() {
        assertTrue(Schedule.immediately().allowedForCritical());
        assertTrue(Schedule.everyHour().allowedForCritical());
        assertFalse(Schedule.every4Hours().allowedForCritical());
        assertFalse(Schedule.onceADay(LocalTime.NOON).allowedForCritical());
        assertFalse(Schedule.immediately().isSummary());
        assertTrue(Schedule.everyHour().isSummary());
    }

    @Test
    void dailyNeedsATimeAndOnlyDailyHasOne() {
        assertThrows(NotificationException.class, () -> new Schedule(Frequency.ONCE_A_DAY, null));
        assertThrows(NotificationException.class, () -> new Schedule(Frequency.EVERY_HOUR, LocalTime.NOON));
    }
}
