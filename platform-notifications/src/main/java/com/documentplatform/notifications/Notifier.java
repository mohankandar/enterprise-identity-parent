package com.documentplatform.notifications;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * regularly (every minute is enough) to raise condition alerts, send summaries that are due and retry messages that
 * failed for a temporary reason.
 *
 * <p>Routing, for each event: is the event type switched on for this tenant (technical) or entity (business)? →
 * which connected destinations does it go to? → for e-mail, which people hold its recipient roles (asked of the app's
 * {@link RecipientResolver} at send time)? Immediately events are sent at once; the others wait for their summary.
 *
 * <p>Reliability: every message is saved in the {@link OutboxStore} before it is sent, under an idempotency key that
 * names what the message is. A restart loses nothing (saved messages are sent on the next tick) and re-creating a
 * message gives the saved one, not a second. Temporary failures are retried with backoff ({@link RetryPolicy});
 * permanent ones stop at once and are reported. Summary buckets and condition state are stored too.
 *
 * <p>Logs ids, codes and fixed reasons only (to the {@code System.Logger} of this class), never message content,
 * addresses or secrets. Thread-safe: calls are serialised per instance; several instances may share the stores.
 */
public final class Notifier {

    private static final System.Logger LOG = System.getLogger(Notifier.class.getName());

    private final NotificationSettings settings;
    private final EventCatalogue catalogue;
    private final RecipientResolver recipients;
    private final Map<ChannelKind, Channel> channels;
    private final Clock clock;
    private final OutboxStore outbox;
    private final SummaryBuckets buckets;
    private final ConditionTracker conditions;
    private final RetryPolicy retry;
    private final Duration lease;
    private final Duration keepFinishedFor;
    private final int batchSize;

    private Notifier(Builder b) {
        this.settings = Objects.requireNonNull(b.settings, "settings");
        this.catalogue = settings.catalogue();
        this.recipients = Objects.requireNonNull(b.recipients, "recipients");
        this.channels = new EnumMap<>(ChannelKind.class);
        b.channels.forEach(c -> {
            if (this.channels.put(c.kind(), c) != null) {
                throw new IllegalArgumentException("One channel per kind");
            }
        });
        this.clock = Objects.requireNonNull(b.clock, "clock");
        this.outbox = Objects.requireNonNull(b.outbox, "outbox store");
        this.buckets = new SummaryBuckets(Objects.requireNonNull(b.summaries, "summary store"));
        this.conditions = new ConditionTracker(Objects.requireNonNull(b.conditions, "condition store"), catalogue);
        this.retry = Objects.requireNonNull(b.retry, "retry policy");
        this.lease = b.lease;
        this.keepFinishedFor = b.keepFinishedFor;
        this.batchSize = b.batchSize;
    }

    public static Builder builder(NotificationSettings settings, RecipientResolver recipients) {
        return new Builder(settings, recipients);
    }

    /** Publishes a one-off event. Returns what happened for each destination. */
    public synchronized List<DeliveryReport> publish(NotificationEvent event) {
        EventType type = check(event);
        if (type.continuous()) {
            throw new NotificationException(IS_A_CONDITION, "Continuous event types are reported as conditions");
        }
        Instant now = now();
        Instant at = event.occurredAt() == null ? now : event.occurredAt();
        return route(new Occurrence(Keys.newEvent(), event, type, MessageKind.EVENT, at, null, 0), now);
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
        Instant now = now();
        conditions.failing(event, now);
        return raiseDueAlerts(now);
    }

    /** Reports that a condition has cleared. Sends Recovered once, and only if the condition had alerted. */
    public synchronized List<DeliveryReport> conditionCleared(ConditionKey key) {
        Instant now = now();
        Optional<Occurrence> recovered = conditions.recovered(key, now);
        if (recovered.isEmpty()) {
            return List.of();
        }
        List<DeliveryReport> reports = route(recovered.get(), now);
        conditions.remove(key);
        return reports;
    }

    /**
     * Raises due condition alerts, sends due summaries, retries due messages (including any a stopped process left
     * behind) and clears out finished messages older than the retention. Called by the app's scheduler.
     */
    public synchronized List<DeliveryReport> tick(Instant now) {
        now = now.truncatedTo(ChronoUnit.MILLIS);
        List<DeliveryReport> reports = new ArrayList<>(raiseDueAlerts(now));
        for (SummaryBuckets.Due due : buckets.due(now)) {
            reports.addAll(sendSummary(due, now));
        }
        for (OutboxEntry entry : outbox.claimDue(now, now.plus(lease), batchSize)) {
            reports.add(attempt(entry, now));
        }
        outbox.purgeFinishedBefore(now.minus(keepFinishedFor));
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

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
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

    private List<DeliveryReport> raiseDueAlerts(Instant now) {
        List<DeliveryReport> reports = new ArrayList<>();
        for (ConditionTracker.Due due : conditions.due(now, this::timing)) {
            reports.addAll(route(due.occurrence(), now));
            conditions.markAlerted(due, now);
        }
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
                buckets.add(e.tenant(), destination, s.schedule(), e.entity(), roles, o, now, t.zone());
                reports.add(new DeliveryReport(e.tenant(), types, destination, o.kind(),
                        DeliveryStatus.QUEUED_FOR_SUMMARY, null));
            } else {
                reports.add(enqueueAndSend(OutboxEntry.pending(Keys.forDestination(o.key(), destination), e.tenant(),
                        destination, kind, e.entity(), roles, types, MessageBuilder.single(o, t), now), now));
            }
        }
        return reports;
    }

    private List<DeliveryReport> sendSummary(SummaryBuckets.Due due, Instant now) {
        SummaryBucket bucket = due.bucket();
        Set<EventTypeId> types = new LinkedHashSet<>();
        List<Occurrence> items = new ArrayList<>();
        for (SummaryItem item : due.items()) {
            // A type removed from the catalogue in a later release has nothing left to say.
            catalogue.find(item.event().type()).ifPresent(type -> {
                types.add(type.id());
                items.add(new Occurrence(item.key(), item.event(), type, item.kind(), item.at(), item.since(),
                        item.failures()));
            });
        }
        if (items.isEmpty()) {
            buckets.remove(due);
            return List.of();
        }
        Optional<TenantSettings> tenant = settings.tenant(bucket.tenant());
        if (tenant.isEmpty() || !tenant.get().isConnected(bucket.destination())) {
            buckets.remove(due);
            return List.of(failedReport(bucket.tenant(), types, bucket.destination(), null, MessageKind.SUMMARY,
                    FailureCode.DESTINATION_NOT_CONNECTED, "summary dropped"));
        }
        TenantSettings t = tenant.get();
        OutboxEntry entry = OutboxEntry.pending(Keys.summary(bucket), bucket.tenant(), bucket.destination(),
                t.kindOf(bucket.destination()), bucket.entity(), bucket.roles(), types,
                MessageBuilder.summary(items, t), now);
        // Saved first, then the bucket emptied: stopping in between rebuilds the same summary under the same key.
        outbox.enqueue(entry);
        buckets.remove(due);
        return List.of(claimAndSend(entry, now));
    }

    private DeliveryReport enqueueAndSend(OutboxEntry entry, Instant now) {
        outbox.enqueue(entry);
        return claimAndSend(entry, now);
    }

    private DeliveryReport claimAndSend(OutboxEntry entry, Instant now) {
        Optional<OutboxEntry> claimed = outbox.claim(entry.key(), now, now.plus(lease));
        if (claimed.isPresent()) {
            return attempt(claimed.get(), now);
        }
        // Already saved before (re-created after a restart) and sent, given up, or in another worker's hands.
        OutboxEntry saved = outbox.find(entry.key()).orElse(entry);
        DeliveryStatus status = switch (saved.state()) {
            case SENT -> DeliveryStatus.SENT;
            case FAILED -> DeliveryStatus.FAILED;
            case PENDING, SENDING -> saved.lastFailure() == null ? DeliveryStatus.PENDING : DeliveryStatus.RETRY_SCHEDULED;
        };
        FailureCode code = status == DeliveryStatus.FAILED || status == DeliveryStatus.RETRY_SCHEDULED
                ? saved.lastFailure() : null;
        return new DeliveryReport(saved.tenant(), saved.eventTypes(), saved.destination(), saved.message().kind(),
                status, code);
    }

    /** One attempt at a claimed message, and its outcome recorded in the outbox. */
    private DeliveryReport attempt(OutboxEntry entry, Instant now) {
        int attempts = entry.attempts() + 1;
        SendResult result = send(entry);
        MessageKind kind = entry.message().kind();
        if (result.sent()) {
            outbox.markSent(entry.key(), attempts, now);
            LOG.log(Level.DEBUG, "Notification {0} sent to destination {1} of tenant {2}, attempt {3}",
                    entry.key(), entry.destination(), entry.tenant(), attempts);
            return new DeliveryReport(entry.tenant(), entry.eventTypes(), entry.destination(), kind,
                    DeliveryStatus.SENT, null);
        }
        FailureCode code = result.failure();
        Optional<Instant> next = code.retryable()
                ? retry.next(entry.createdAt(), attempts, result.retryAfter(), now) : Optional.empty();
        if (next.isPresent()) {
            outbox.markRetry(entry.key(), attempts, next.get(), code);
            LOG.log(Level.INFO, "Notification {0} to destination {1} of tenant {2} not delivered: {3} ({4}); "
                            + "attempt {5}, next attempt at {6}", entry.key(), entry.destination(), entry.tenant(),
                    code.describe(entry.channel()), code.name(), attempts, next.get());
            return new DeliveryReport(entry.tenant(), entry.eventTypes(), entry.destination(), kind,
                    DeliveryStatus.RETRY_SCHEDULED, code);
        }
        outbox.markFailed(entry.key(), attempts, code, now);
        return failedReport(entry.tenant(), entry.eventTypes(), entry.destination(), entry.channel(), kind, code,
                "notification " + entry.key()
                        + (code.retryable() ? ", retries ran out after attempt " : ", stopped at attempt ") + attempts);
    }

    /** @param channel the destination's channel, or null when the destination is gone */
    private static DeliveryReport failedReport(TenantId tenant, Set<EventTypeId> types, DestinationId destination,
                                               ChannelKind channel, MessageKind kind, FailureCode code, String what) {
        LOG.log(Level.WARNING, "Notification not delivered to destination {0} of tenant {1}: {2} ({3}); {4}",
                destination, tenant, channel == null ? code.name() : code.describe(channel), code.name(), what);
        return new DeliveryReport(tenant, types, destination, kind, DeliveryStatus.FAILED, code);
    }

    private SendResult send(OutboxEntry entry) {
        Optional<TenantSettings> tenant = settings.tenant(entry.tenant());
        if (tenant.isEmpty() || tenant.get().kindOf(entry.destination()) != entry.channel()) {
            return SendResult.failed(FailureCode.DESTINATION_NOT_CONNECTED);
        }
        Channel channel = channels.get(entry.channel());
        if (channel == null) {
            return SendResult.failed(FailureCode.CHANNEL_NOT_AVAILABLE);
        }
        Set<Recipient> people = new LinkedHashSet<>();
        if (entry.channel() == ChannelKind.EMAIL) {
            try {
                // The IT Admin is a tenant-level role: asked without the entity, even for a per-entity event.
                entry.recipientRoles().stream().sorted((a, b) -> a.value().compareTo(b.value()))
                        .forEach(role -> people.addAll(recipients.resolve(entry.tenant(),
                                role.equals(catalogue.itAdminRole()) ? null : entry.entity(), role)));
            } catch (RuntimeException lookupFailed) {
                return SendResult.failed(FailureCode.RECIPIENTS_UNAVAILABLE);
            }
            if (people.isEmpty()) {
                return SendResult.failed(FailureCode.NO_RECIPIENTS);
            }
        }
        try {
            return channel.send(new OutgoingMessage(entry.tenant(), entry.destination(), entry.channel(), people,
                    entry.message(), entry.key()));
        } catch (RuntimeException senderFault) {
            // The exception text may hold a server reply or an address: it is not kept.
            return SendResult.failed(FailureCode.SENDER_FAILED);
        }
    }

    /** Wires a notifier. Settings, recipients and the three stores are required. */
    public static final class Builder {
        private final NotificationSettings settings;
        private final RecipientResolver recipients;
        private final List<Channel> channels = new ArrayList<>();
        private Clock clock = Clock.systemUTC();
        private OutboxStore outbox;
        private SummaryStore summaries;
        private ConditionStore conditions;
        private RetryPolicy retry = RetryPolicy.DEFAULT;
        private Duration lease = Duration.ofMinutes(5);
        private Duration keepFinishedFor = Duration.ofDays(30);
        private int batchSize = 100;

        private Builder(NotificationSettings settings, RecipientResolver recipients) {
            this.settings = settings;
            this.recipients = recipients;
        }

        public Builder channels(Collection<? extends Channel> channels) {
            this.channels.addAll(channels);
            return this;
        }

        public Builder channel(Channel channel) {
            this.channels.add(Objects.requireNonNull(channel, "channel"));
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder outbox(OutboxStore outbox) {
            this.outbox = outbox;
            return this;
        }

        public Builder summaries(SummaryStore summaries) {
            this.summaries = summaries;
            return this;
        }

        public Builder conditions(ConditionStore conditions) {
            this.conditions = conditions;
            return this;
        }

        public Builder retryPolicy(RetryPolicy retry) {
            this.retry = retry;
            return this;
        }

        /**
         * How long a worker holds a message it is sending (default 5 minutes). Must be longer than a channel's
         * timeouts: a lease that runs out means the worker stopped, and the message is sent again.
         */
        public Builder lease(Duration lease) {
            if (lease.compareTo(Duration.ofSeconds(1)) < 0) {
                throw new IllegalArgumentException("The lease must be at least a second");
            }
            this.lease = lease;
            return this;
        }

        /** How long sent and failed messages stay in the outbox (default 30 days). */
        public Builder keepFinishedFor(Duration keepFinishedFor) {
            if (keepFinishedFor.isNegative()) {
                throw new IllegalArgumentException("Retention cannot be negative");
            }
            this.keepFinishedFor = keepFinishedFor;
            return this;
        }

        /** Messages retried per tick (default 100). */
        public Builder batchSize(int batchSize) {
            if (batchSize < 1) {
                throw new IllegalArgumentException("Batch size must be positive");
            }
            this.batchSize = batchSize;
            return this;
        }

        public Notifier build() {
            return new Notifier(this);
        }
    }
}
