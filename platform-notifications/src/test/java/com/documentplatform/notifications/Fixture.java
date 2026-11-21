package com.documentplatform.notifications;

import com.documentplatform.notifications.inmemory.InMemoryChannel;
import com.documentplatform.notifications.inmemory.InMemoryRecipientResolver;
import com.documentplatform.notifications.inmemory.InMemorySettingsStore;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** A made-up catalogue and a wired notifier with in-memory fakes and a clock the test moves. */
final class Fixture {

    static final RoleId IT_ADMIN = RoleId.of("it-admin");
    static final RoleId OPS = RoleId.of("ops");
    static final RoleId REVIEWER = RoleId.of("reviewer");
    static final RoleId APPROVER = RoleId.of("approver");

    static final EventTypeId ITEMS_WAITING = EventTypeId.of("work.items-waiting");
    static final EventTypeId APPROVAL_WAITING = EventTypeId.of("work.approval-waiting");
    static final EventTypeId QUOTA = EventTypeId.of("usage.quota");
    static final EventTypeId FILES_REJECTED = EventTypeId.of("intake.files-rejected");
    static final EventTypeId STORE_DOWN = EventTypeId.of("service.store-down");
    static final EventTypeId DELIVERY_FAILING = EventTypeId.of("delivery.failing");
    static final EventTypeId SOURCE_UNREACHABLE = EventTypeId.of("intake.source-unreachable");
    static final EventTypeId LICENCE_EXPIRED = EventTypeId.of("licence.expired");
    static final EventTypeId LEVEL_LOWERED = EventTypeId.of("security.level-lowered");

    static final TenantId TENANT = TenantId.of("t1");
    static final EntityId E1 = EntityId.of("e1");
    static final EntityId E2 = EntityId.of("e2");
    static final DestinationId MAIL = DestinationId.of("mail");
    static final DestinationId TEAMS = DestinationId.of("ops-teams");
    static final DestinationId SLACK = DestinationId.of("ops-slack");

    static final Recipient ANN = new Recipient("u-ann", "ann@example.test");
    static final Recipient BOB = new Recipient("u-bob", "bob@example.test");
    static final Recipient CY = new Recipient("u-cy", "cy@example.test");

    static final Instant START = Instant.parse("2026-11-20T10:05:00Z");

    static EventCatalogue catalogue() {
        return EventCatalogue.builder(IT_ADMIN)
                .register(EventType.builder(ITEMS_WAITING.value(), "Items waiting for review", EventKind.BUSINESS)
                        .schedule(Schedule.every4Hours()).recipients(REVIEWER).count("items", "Items").build())
                .register(EventType.builder(APPROVAL_WAITING.value(), "Approval waiting", EventKind.BUSINESS)
                        .schedule(Schedule.immediately()).recipients(APPROVER).time("due", "Due").build())
                .register(EventType.builder(QUOTA.value(), "Quota used", EventKind.BUSINESS)
                        .schedule(Schedule.onceADay(LocalTime.of(8, 0))).recipients(APPROVER)
                        .count("percent", "Percent used").threshold("percent", "Percent used", 80, 50, 100).build())
                .register(EventType.builder(FILES_REJECTED.value(), "Files rejected", EventKind.TECHNICAL)
                        .schedule(Schedule.everyHour()).recipients(OPS).count("files", "Files").build())
                .register(EventType.builder(STORE_DOWN.value(), "Store down", EventKind.TECHNICAL)
                        .critical().continuous().recipients(IT_ADMIN).build())
                .register(EventType.builder(DELIVERY_FAILING.value(), "Delivery failing", EventKind.TECHNICAL)
                        .critical().continuous().schedule(Schedule.everyHour()).recipients(IT_ADMIN, OPS).build())
                .register(EventType.builder(SOURCE_UNREACHABLE.value(), "Source unreachable", EventKind.TECHNICAL)
                        .continuous(new ConditionTiming(Duration.ofMinutes(30), Duration.ofHours(4)))
                        .schedule(Schedule.immediately()).recipients(OPS).build())
                .register(EventType.builder(LICENCE_EXPIRED.value(), "Licence expired", EventKind.BUSINESS)
                        .critical().recipients(IT_ADMIN, APPROVER).build())
                .register(EventType.builder(LEVEL_LOWERED.value(), "Level lowered", EventKind.BUSINESS)
                        .offByDefault().schedule(Schedule.immediately()).recipients(APPROVER).build())
                .build();
    }

    static TenantSettings tenantSettings(ZoneId zone) {
        return new TenantSettings(URI.create("https://app.example.test/"), zone,
                Map.of(MAIL, ChannelKind.EMAIL, TEAMS, ChannelKind.TEAMS, SLACK, ChannelKind.SLACK));
    }

    final MutableClock clock = new MutableClock(START);
    final InMemorySettingsStore store = new InMemorySettingsStore();
    final NotificationSettings settings = new NotificationSettings(catalogue(), store);
    final InMemoryRecipientResolver resolver = new InMemoryRecipientResolver();
    final InMemoryChannel email = new InMemoryChannel(ChannelKind.EMAIL);
    final InMemoryChannel teams = new InMemoryChannel(ChannelKind.TEAMS);
    final InMemoryChannel slack = new InMemoryChannel(ChannelKind.SLACK);
    final Notifier notifier = new Notifier(settings, resolver, List.of(email, teams, slack), clock);

    Fixture() {
        this(ZoneOffset.UTC);
    }

    Fixture(ZoneId zone) {
        settings.connectChannels(TENANT, tenantSettings(zone));
        resolver.grant(TENANT, null, IT_ADMIN, ANN)
                .grant(TENANT, null, OPS, BOB)
                .grant(TENANT, E1, REVIEWER, BOB, CY)
                .grant(TENANT, E1, APPROVER, CY)
                .grant(TENANT, E2, APPROVER, ANN);
    }

    /** Moves the clock and ticks. */
    List<DeliveryReport> advance(Duration by) {
        clock.advance(by);
        return notifier.tick(clock.instant());
    }

    static NotificationEvent.Builder event(EventTypeId type) {
        return NotificationEvent.builder(type, TENANT);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        void set(Instant at) {
            now = at;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
