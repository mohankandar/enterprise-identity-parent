package com.documentplatform.notifications;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * How often an event is sent: Immediately, or as a summary every hour, every 4 hours or once a day at a chosen time.
 *
 * @param frequency the frequency
 * @param dailyAt   the time of day for {@link Frequency#ONCE_A_DAY}, in the tenant's time zone; null otherwise
 */
public record Schedule(Frequency frequency, LocalTime dailyAt) {

    public Schedule {
        Objects.requireNonNull(frequency, "frequency");
        if ((frequency == Frequency.ONCE_A_DAY) != (dailyAt != null)) {
            throw new NotificationException(NotificationException.Reason.INVALID_VALUE,
                    "A time of day is required for Once a day and only for Once a day");
        }
    }

    public static Schedule immediately() {
        return new Schedule(Frequency.IMMEDIATELY, null);
    }

    public static Schedule everyHour() {
        return new Schedule(Frequency.EVERY_HOUR, null);
    }

    public static Schedule every4Hours() {
        return new Schedule(Frequency.EVERY_4_HOURS, null);
    }

    public static Schedule onceADay(LocalTime at) {
        return new Schedule(Frequency.ONCE_A_DAY, Objects.requireNonNull(at, "at"));
    }

    public boolean isSummary() {
        return frequency != Frequency.IMMEDIATELY;
    }

    /** Critical events may only be Immediately or Every hour. */
    public boolean allowedForCritical() {
        return frequency == Frequency.IMMEDIATELY || frequency == Frequency.EVERY_HOUR;
    }

    /**
     * The first summary time strictly after {@code after}: the next whole hour, the next of 00:00/04:00/08:00/...,
     * or the next {@code dailyAt}, in the given zone.
     */
    public Instant nextDue(Instant after, ZoneId zone) {
        ZonedDateTime t = after.atZone(zone);
        return switch (frequency) {
            case IMMEDIATELY -> after;
            case EVERY_HOUR -> t.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant();
            case EVERY_4_HOURS -> {
                ZonedDateTime hour = t.truncatedTo(ChronoUnit.HOURS);
                yield hour.minusHours(hour.getHour() % 4).plusHours(4).toInstant();
            }
            case ONCE_A_DAY -> {
                ZonedDateTime today = t.toLocalDate().atTime(dailyAt).atZone(zone);
                yield (today.isAfter(t) ? today : t.toLocalDate().plusDays(1).atTime(dailyAt).atZone(zone)).toInstant();
            }
        };
    }
}
