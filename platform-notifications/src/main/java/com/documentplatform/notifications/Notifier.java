package com.documentplatform.notifications;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.documentplatform.notifications.NotificationException.Reason.ENTITY_REQUIRED;
import static com.documentplatform.notifications.NotificationException.Reason.IS_A_CONDITION;
import static com.documentplatform.notifications.NotificationException.Reason.NOT_A_CONDITION;
import static com.documentplatform.notifications.NotificationException.Reason.UNKNOWN_FIELD;

/**
 * The entry point. Apps publish events and report conditions; the app's scheduler calls {@link #tick(Instant)}
 * regularly (every minute is enough) to raise condition alerts and send summaries that are due.
 *
 * <p>Routing, for each event: is the event type switched on for this tenant (technical) or entity (business)? →
 * which connected destinations does it go to? → for e-mail, which people hold its recipient roles (asked of the app's
 * {@link RecipientResolver} at send time)? Immediately events are sent at once; the others wait for their summary.
 *
 * <p>Thread-safe: calls are serialised. State (summary buckets, conditions) is in memory in P003.
 */
public final class Notifier {

    private final NotificationSettings settings;
    private final EventCatalogue catalogue;
    private final RecipientResolver recipients;
    private final Map<ChannelKind, Channel> channels = new EnumMap<>(ChannelKind.class);
    private final Clock clock;
    private final SummaryBuckets buckets = new SummaryBuckets();
    private final ConditionTracker conditions = new ConditionTracker();

    public Notifier(NotificationSettings settings, RecipientResolver recipients, Collection<? extends Channel> channels,
                    Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.catalogue = settings.catalogue();
        this.recipients = Objects.requireNonNull(recipients, "recipients");
        channels.forEach(c -> {
            if (this.channels.put(c.kind(), c) != null) {
                throw new IllegalArgumentException("One channel per kind");
            }
        });
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Publishes a one-off event. Returns what happened for each destination. */
    public synchronized List<DeliveryReport> publish(NotificationEvent event) {
        EventType type = check(event);
        if (type.continuous()) {
            throw new NotificationException(IS_A_CONDITION, "Continuous event types are reported as conditions");
        }
        Instant now = clock.instant();
        Instant at = event.occurredAt() == null ? now : event.occurredAt();
        return route(new Occurrence(event, type, MessageKind.EVENT, at, null, 0), now);
    }

    /**
     * Reports that a continuous condition is failing (call it on every failure; repeats are counted, not alerted).
     * The event's type, tenant, entity and identifier name the condition.
     */
    public synchronized List<DeliveryReport> conditionFailing(NotificationEvent event) {
        EventType type = check(event);
        if (!type.continuous()) {
            throw new NotificationException(NOT_A_CONDITION, "Only continuous event types are reported as conditions");
        }
        Instant now = clock.instant();
        conditions.failing(event, type, now);
        return routeAll(conditions.evaluate(now, this::timing), now);
    }

    /** Reports that a condition has cleared. Sends Recovered once, and only if the condition had alerted. */
    public synchronized List<DeliveryReport> conditionCleared(ConditionKey key) {
        Instant now = clock.instant();
        return conditions.cleared(key, now).map(o -> route(o, now)).orElse(List.of());
    }

    /** Raises due condition alerts and sends due summaries. Called by the app's scheduler. */
    public synchronized List<DeliveryReport> tick(Instant now) {
        List<DeliveryReport> reports = new ArrayList<>(routeAll(conditions.evaluate(now, this::timing), now));
        for (SummaryBuckets.Due due : buckets.takeDue(now)) {
            reports.add(sendSummary(due));
        }
        return reports;
    }

    /** Whether the condition is currently being tracked (started and not cleared). */
    public synchronized boolean isConditionActive(ConditionKey key) {
        return conditions.isActive(key);
    }

    /** Occurrences waiting for a summary. */
    public synchronized int pendingSummaryItems() {
        return buckets.pending();
    }

    private EventType check(NotificationEvent event) {
        EventType type = catalogue.require(event.type());
        if (type.kind() == EventKind.BUSINESS && event.entity() == null) {
            throw new NotificationException(ENTITY_REQUIRED, "Business events belong to an entity");
        }
        event.counts().keySet().forEach(name -> declared(type, name, FieldSpec.Type.COUNT));
        event.times().keySet().forEach(name -> declared(type, name, FieldSpec.Type.TIME));
        return type;
    }

    private static void declared(EventType type, String name, FieldSpec.Type kind) {
        if (type.field(name).filter(f -> f.type() == kind).isEmpty()) {
            throw new NotificationException(UNKNOWN_FIELD, "The event type does not declare this field");
        }
    }

    private ConditionTiming timing(NotificationEvent event) {
        return settings.effective(event.tenant(), event.entity(), event.type()).conditionTiming();
    }

    private List<DeliveryReport> routeAll(List<Occurrence> occurrences, Instant now) {
        List<DeliveryReport> reports = new ArrayList<>();
        occurrences.forEach(o -> reports.addAll(route(o, now)));
        return reports;
    }

    private List<DeliveryReport> route(Occurrence o, Instant now) {
        NotificationEvent e = o.event();
        Set<EventTypeId> types = Set.of(e.type());
        EventSettings s = settings.effective(e.tenant(), e.entity(), e.type());
        if (!s.enabled()) {
            return List.of(new DeliveryReport(e.tenant(), types, null, o.kind(), DeliveryStatus.DISABLED, null));
        }
        Optional<TenantSettings> tenant = settings.tenant(e.tenant());
        List<DestinationId> targets = tenant.map(t -> t.targetsFor(s.destinations())).orElse(List.of());
        if (targets.isEmpty()) {
            return List.of(new DeliveryReport(e.tenant(), types, null, o.kind(), DeliveryStatus.NO_DESTINATION, null));
        }
        TenantSettings t = tenant.get();
        List<DeliveryReport> reports = new ArrayList<>();
        for (DestinationId destination : targets) {
            ChannelKind kind = t.kindOf(destination);
            Set<RoleId> roles = kind == ChannelKind.EMAIL ? s.recipientRoles() : Set.of();
            if (s.schedule().isSummary()) {
                buckets.add(new SummaryBuckets.Key(e.tenant(), destination, s.schedule(), e.entity(), roles), o, now,
                        t.zone());
                reports.add(new DeliveryReport(e.tenant(), types, destination, o.kind(),
                        DeliveryStatus.QUEUED_FOR_SUMMARY, null));
            } else {
                reports.add(deliver(e.tenant(), destination, kind, e.entity(), roles,
                        MessageBuilder.single(o, t), types));
            }
        }
        return reports;
    }

    private DeliveryReport sendSummary(SummaryBuckets.Due due) {
        SummaryBuckets.Key key = due.key();
        Set<EventTypeId> types = new LinkedHashSet<>();
        due.items().forEach(o -> types.add(o.type().id()));
        Optional<TenantSettings> tenant = settings.tenant(key.tenant());
        if (tenant.isEmpty() || !tenant.get().isConnected(key.destination())) {
            return new DeliveryReport(key.tenant(), types, key.destination(), MessageKind.SUMMARY,
                    DeliveryStatus.FAILED, FailureCode.DESTINATION_NOT_CONNECTED);
        }
        TenantSettings t = tenant.get();
        return deliver(key.tenant(), key.destination(), t.kindOf(key.destination()), key.entity(), key.roles(),
                MessageBuilder.summary(due.items(), t), types);
    }

    private DeliveryReport deliver(TenantId tenant, DestinationId destination, ChannelKind kind, EntityId entity,
                                   Set<RoleId> roles, Message message, Set<EventTypeId> types) {
        Channel channel = channels.get(kind);
        if (channel == null) {
            return failed(tenant, types, destination, message, FailureCode.CHANNEL_NOT_AVAILABLE);
        }
        Set<Recipient> people = new LinkedHashSet<>();
        if (kind == ChannelKind.EMAIL) {
            try {
                // The IT Admin is a tenant-level role: asked without the entity, even for a per-entity event.
                roles.forEach(role -> people.addAll(recipients.resolve(tenant,
                        role.equals(catalogue.itAdminRole()) ? null : entity, role)));
            } catch (RuntimeException lookupFailed) {
                return failed(tenant, types, destination, message, FailureCode.RECIPIENTS_UNAVAILABLE);
            }
            if (people.isEmpty()) {
                return failed(tenant, types, destination, message, FailureCode.NO_RECIPIENTS);
            }
        }
        SendResult result;
        try {
            result = channel.send(new OutgoingMessage(tenant, destination, kind, people, message));
        } catch (RuntimeException senderFault) {
            // The exception text may hold a server reply or an address: it is not kept.
            result = SendResult.failed(FailureCode.SENDER_FAILED);
        }
        return result.sent()
                ? new DeliveryReport(tenant, types, destination, message.kind(), DeliveryStatus.SENT, null)
                : failed(tenant, types, destination, message, result.failure());
    }

    private static DeliveryReport failed(TenantId tenant, Set<EventTypeId> types, DestinationId destination,
                                         Message message, FailureCode code) {
        return new DeliveryReport(tenant, types, destination, message.kind(), DeliveryStatus.FAILED, code);
    }
}
