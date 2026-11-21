package com.documentplatform.notifications.support;

import com.documentplatform.notifications.ChannelKind;
import com.documentplatform.notifications.DestinationId;
import com.documentplatform.notifications.EntityId;
import com.documentplatform.notifications.EventCatalogue;
import com.documentplatform.notifications.EventKind;
import com.documentplatform.notifications.EventType;
import com.documentplatform.notifications.EventTypeId;
import com.documentplatform.notifications.Recipient;
import com.documentplatform.notifications.RecipientResolver;
import com.documentplatform.notifications.RoleId;
import com.documentplatform.notifications.Schedule;
import com.documentplatform.notifications.TenantId;
import com.documentplatform.notifications.TenantSettings;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

/** A made-up catalogue, tenant and role holders for the I/O tests. */
public final class TestCatalogue {

    public static final RoleId IT_ADMIN = RoleId.of("it-admin");
    public static final RoleId OPS = RoleId.of("ops");
    public static final RoleId APPROVER = RoleId.of("approver");

    public static final EventTypeId APPROVAL = EventTypeId.of("work.approval-waiting");
    public static final EventTypeId REJECTED = EventTypeId.of("intake.files-rejected");
    public static final EventTypeId STORE_DOWN = EventTypeId.of("service.store-down");

    public static final TenantId TENANT = TenantId.of("t1");
    public static final EntityId E1 = EntityId.of("e1");
    public static final DestinationId MAIL = DestinationId.of("mail");
    public static final DestinationId TEAMS = DestinationId.of("ops-teams");
    public static final DestinationId SLACK = DestinationId.of("ops-slack");

    public static final Recipient ANN = new Recipient("u-ann", "ann@customer.example");
    public static final Recipient CY = new Recipient("u-cy", "cy@customer.example");

    public static final Instant START = Instant.parse("2026-11-20T10:05:00Z");

    private TestCatalogue() {
    }

    public static EventCatalogue catalogue() {
        return EventCatalogue.builder(IT_ADMIN)
                .register(EventType.builder(APPROVAL.value(), "Approval waiting", EventKind.BUSINESS)
                        .schedule(Schedule.immediately()).recipients(APPROVER).time("due", "Due").build())
                .register(EventType.builder(REJECTED.value(), "Files rejected", EventKind.TECHNICAL)
                        .schedule(Schedule.everyHour()).recipients(OPS).count("files", "Files").build())
                .register(EventType.builder(STORE_DOWN.value(), "Store down", EventKind.TECHNICAL)
                        .critical().continuous().recipients(IT_ADMIN).build())
                .build();
    }

    public static TenantSettings tenant() {
        return new TenantSettings(URI.create("https://app.customer.example/"), ZoneOffset.UTC,
                Map.of(MAIL, ChannelKind.EMAIL, TEAMS, ChannelKind.TEAMS, SLACK, ChannelKind.SLACK));
    }

    /** IT Admin and ops: ANN (tenant level); approver: CY for entity E1. */
    public static RecipientResolver resolver() {
        return (tenant, entity, role) -> {
            if ((role.equals(IT_ADMIN) || role.equals(OPS)) && entity == null) {
                return Set.of(ANN);
            }
            if (role.equals(APPROVER) && E1.equals(entity)) {
                return Set.of(CY);
            }
            return Set.of();
        };
    }

    /** A clock the test moves. */
    public static final class MovingClock extends Clock {
        private Instant now;

        public MovingClock(Instant now) {
            this.now = now;
        }

        public synchronized void advance(Duration by) {
            now = now.plus(by);
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
        public synchronized Instant instant() {
            return now;
        }
    }
}
