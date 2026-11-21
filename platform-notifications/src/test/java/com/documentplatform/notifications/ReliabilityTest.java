package com.documentplatform.notifications;

import com.documentplatform.notifications.support.Stack;
import com.documentplatform.notifications.support.WebhookServer.Reply;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.documentplatform.notifications.support.TestCatalogue.APPROVAL;
import static com.documentplatform.notifications.support.TestCatalogue.E1;
import static com.documentplatform.notifications.support.TestCatalogue.REJECTED;
import static com.documentplatform.notifications.support.TestCatalogue.STORE_DOWN;
import static com.documentplatform.notifications.support.TestCatalogue.TEAMS;
import static com.documentplatform.notifications.support.TestCatalogue.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Restarts and failures against the real stack (Postgres outbox, HTTP webhooks, SMTP). A "crash" is an Error thrown
 * at a chosen point, which the notifier does not catch: the process stops there, the database keeps what was
 * committed, and a new notifier on the same database is the restart.
 */
class ReliabilityTest {

    private Stack s;

    @BeforeEach
    void start() throws Exception {
        s = new Stack();
    }

    @AfterEach
    void stop() {
        s.close();
    }

    private static NotificationEvent approval(String id) {
        return NotificationEvent.builder(APPROVAL, TENANT).entity(E1).identifier(RecordIdentifier.of(id)).build();
    }

    private static NotificationEvent rejected(String id, int files) {
        return NotificationEvent.builder(REJECTED, TENANT).identifier(RecordIdentifier.of(id)).count("files", files)
                .build();
    }

    @Test
    void aRestartAfterSavingAndBeforeSendingLosesNothing() {
        s.routeBusiness(E1, APPROVAL, TEAMS);
        Channel diesBeforeSending = new CrashingChannel(s.teams, true, false);
        Notifier before = s.notifier(s.summaries, s.conditions, List.of(diesBeforeSending));
        before.publish(approval("LN 1")); // saved; the sender dies while sending
        assertThrows(ProcessStopped.class, () -> before.sendDue(s.clock.instant()));
        assertTrue(s.hooks.requests().isEmpty());

        Notifier after = s.notifier();
        assertTrue(s.advance(after, Duration.ofMinutes(4)).isEmpty(), "the stopped worker's lease still holds");
        assertEquals(DeliveryStatus.SENT, s.advance(after, Duration.ofMinutes(1)).get(0).status());
        assertTrue(s.advance(after, Duration.ofHours(1)).isEmpty());
        assertEquals(1, s.hooks.requests().size(), "sent exactly once");
        assertTrue(s.hooks.requests().get(0).body().contains("Approval waiting: LN 1"));
    }

    @Test
    void aCrashAfterSavingASummaryAndBeforeEmptyingItsBucketSendsItOnce() {
        s.routeTechnical(REJECTED, Schedule.everyHour(), TEAMS);
        Notifier first = s.notifier();
        first.publish(rejected("batch-1", 2));
        first.publish(rejected("batch-2", 5));

        SummaryStore crashOnRemove = new CrashingSummaryStore(s.summaries);
        Notifier crashing = s.notifier(crashOnRemove, s.conditions, List.of(s.teams));
        s.clock.advance(Duration.ofMinutes(55)); // 11:00, the hourly summary is due
        assertThrows(ProcessStopped.class, () -> crashing.tick(s.clock.instant()));
        assertTrue(s.hooks.requests().isEmpty());
        assertEquals(2, s.summaries.pending(), "the bucket was not emptied");

        Notifier restarted = s.notifier();
        List<DeliveryReport> reports = restarted.tick(s.clock.instant());
        assertEquals(List.of(DeliveryStatus.SENT), reports.stream().map(DeliveryReport::status).toList());
        assertEquals(0, s.summaries.pending());
        assertTrue(s.advance(restarted, Duration.ofHours(3)).isEmpty());
        assertEquals(1, s.hooks.requests().size(), "one summary, not two");
        String body = s.hooks.requests().get(0).body();
        assertTrue(body.contains("batch-1") && body.contains("batch-2") && body.contains("Files (total): 7"), body);
    }

    @Test
    void aCrashAfterAnAlertWasSavedAndBeforeItWasRecordedAlertsOnce() {
        s.routeTechnical(STORE_DOWN, Schedule.immediately(), TEAMS);
        NotificationEvent down = NotificationEvent.builder(STORE_DOWN, TENANT).identifier(RecordIdentifier.of("store-1"))
                .build();
        ConditionStore crashOnMark = new CrashingConditionStore(s.conditions);
        Notifier crashing = s.notifier(s.summaries, crashOnMark, List.of(s.teams));
        crashing.conditionFailing(down);
        s.clock.advance(Duration.ofMinutes(15));
        assertThrows(ProcessStopped.class, () -> crashing.tick(s.clock.instant()));
        assertTrue(s.hooks.requests().isEmpty(), "the alert was saved in the outbox before the crash");

        Notifier restarted = s.notifier();
        List<DeliveryReport> again = restarted.tick(s.clock.instant());
        assertEquals(List.of(DeliveryStatus.SENT), again.stream().map(DeliveryReport::status).toList(),
                "re-raised under the same key, found already saved, and sent");
        assertEquals(1, s.hooks.requests().size(), "no duplicate alert");

        assertTrue(s.advance(restarted, Duration.ofHours(3)).isEmpty());
        s.advance(restarted, Duration.ofHours(1));
        assertEquals(2, s.hooks.requests().size(), "the 4-hour repeat");
        assertTrue(s.hooks.requests().get(1).body().contains("Still ongoing: Store down: store-1"));
        s.delivered(restarted, restarted.conditionCleared(ConditionKey.of(down)));
        s.delivered(restarted, restarted.conditionCleared(ConditionKey.of(down)));
        assertEquals(3, s.hooks.requests().size(), "Recovered once");
        assertTrue(s.hooks.requests().get(2).body().contains("Recovered: Store down: store-1"));
    }

    @Test
    void summaryItemsAndConditionStateSurviveARestart() {
        s.routeTechnical(REJECTED, Schedule.everyHour(), TEAMS);
        s.routeTechnical(STORE_DOWN, Schedule.immediately(), TEAMS);
        Notifier first = s.notifier();
        first.publish(rejected("batch-1", 1));
        first.conditionFailing(NotificationEvent.builder(STORE_DOWN, TENANT).build());

        Notifier second = s.notifier();
        assertTrue(second.isConditionActive(new ConditionKey(STORE_DOWN, TENANT, null, null)));
        assertEquals(1, second.pendingSummaryItems());
        s.clock.advance(Duration.ofMinutes(15)); // 10:20: the condition's threshold
        assertEquals(1, second.tick(s.clock.instant()).size());
        s.clock.advance(Duration.ofMinutes(40)); // 11:00: the summary
        assertEquals(1, second.tick(s.clock.instant()).size());
        assertEquals(2, s.hooks.requests().size());
    }

    @Test
    void aCrashBetweenDeliveryAndRecordingItResendsUnderTheSameMessageId() throws Exception {
        // The one window an outbox cannot close: the mail server took the message, then the process stopped before
        // the outbox recorded it. The message goes again after the lease, with the same Message-ID.
        Channel diesAfterSending = new CrashingChannel(s.email, false, true);
        Notifier crashing = s.notifier(s.summaries, s.conditions, List.of(diesAfterSending));
        crashing.publish(approval("LN 2"));
        assertThrows(ProcessStopped.class, () -> crashing.sendDue(s.clock.instant()));
        assertEquals(1, s.mail.getReceivedMessages().length);

        Notifier restarted = s.notifier();
        s.advance(restarted, Duration.ofMinutes(5));
        MimeMessage[] received = s.mail.getReceivedMessages();
        assertEquals(2, received.length);
        assertEquals(received[0].getMessageID(), received[1].getMessageID());
    }

    @Test
    void temporaryFailuresAreRetriedOverHttpUntilDelivered() {
        s.routeBusiness(E1, APPROVAL, TEAMS);
        s.hooks.then(Reply.of(503)).then(Reply.of(502));
        Notifier n = s.notifier();
        DeliveryReport first = s.publish(n, approval("LN 3")).get(0);
        assertEquals(DeliveryStatus.RETRY_SCHEDULED, first.status());
        assertEquals(FailureCode.WEBHOOK_SERVER_ERROR, first.failure());
        assertEquals(DeliveryStatus.RETRY_SCHEDULED, s.advance(n, Duration.ofMinutes(1)).get(0).status());
        assertTrue(s.advance(n, Duration.ofMinutes(1)).isEmpty(), "second delay is 2 minutes");
        assertEquals(DeliveryStatus.SENT, s.advance(n, Duration.ofMinutes(1)).get(0).status());
        assertEquals(3, s.hooks.requests().size());
    }

    @Test
    void permanentFailuresStopAtOnce() {
        s.routeBusiness(E1, APPROVAL, TEAMS);
        s.hooks.always(Reply.of(410));
        Notifier n = s.notifier();
        DeliveryReport r = s.publish(n, approval("LN 4")).get(0);
        assertEquals(DeliveryStatus.FAILED, r.status());
        assertEquals(FailureCode.WEBHOOK_ADDRESS_INVALID, r.failure());
        for (int i = 0; i < 24; i++) {
            assertTrue(s.advance(n, Duration.ofHours(1)).isEmpty());
        }
        assertEquals(1, s.hooks.requests().size());
    }

    @Test
    void loginRefusedStopsAtOnce() {
        s.secrets.put(Stack.MAIL_REF, "Wrong-Pw-SECRET-77");
        Notifier n = s.notifier();
        DeliveryReport r = s.publish(n, approval("LN 5")).get(0);
        assertEquals(DeliveryStatus.FAILED, r.status());
        assertEquals(FailureCode.MAIL_LOGIN_REFUSED, r.failure());
        assertTrue(s.advance(n, Duration.ofHours(2)).isEmpty());
    }

    @Test
    void retryAfterIsHonoured() {
        s.routeBusiness(E1, APPROVAL, TEAMS);
        s.hooks.then(new Reply(429, Map.of("Retry-After", "300"), 0));
        Notifier n = s.notifier();
        Instant published = s.clock.instant();
        assertEquals(FailureCode.WEBHOOK_RATE_LIMITED, s.publish(n, approval("LN 6")).get(0).failure());
        assertTrue(s.advance(n, Duration.ofMinutes(4)).isEmpty(), "not after the usual 1 minute");
        assertEquals(DeliveryStatus.SENT, s.advance(n, Duration.ofMinutes(1)).get(0).status());
        assertEquals(2, s.hooks.requests().size());
        assertEquals(published.plusSeconds(300), s.clock.instant());
    }

    @Test
    void twoAppInstancesOnOneDatabaseSendEachMessageOnce() throws Exception {
        s.routeBusiness(E1, APPROVAL, TEAMS);
        s.hooks.always(Reply.of(503));
        Notifier one = s.notifier();
        for (int i = 0; i < 30; i++) {
            one.publish(approval("LN 1" + i));
        }
        s.hooks.reset();
        s.clock.advance(Duration.ofMinutes(1));
        Instant now = s.clock.instant();
        List<Notifier> instances = List.of(
                Notifier.builder(s.settings, (t, e, r) -> java.util.Set.of()).channels(List.of(s.teams))
                        .clock(s.clock).outbox(s.outbox).summaries(s.summaries).conditions(s.conditions).batchSize(3)
                        .build(),
                Notifier.builder(s.settings, (t, e, r) -> java.util.Set.of()).channels(List.of(s.teams))
                        .clock(s.clock).outbox(s.outbox).summaries(s.summaries).conditions(s.conditions).batchSize(3)
                        .build());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Collection<Callable<Integer>> work = new ArrayList<>();
            for (Notifier n : instances) {
                work.add(() -> {
                    int sent = 0;
                    List<DeliveryReport> r;
                    while (!(r = n.tick(now)).isEmpty()) {
                        sent += r.size();
                    }
                    return sent;
                });
            }
            int total = 0;
            for (Future<Integer> f : pool.invokeAll(work)) {
                total += f.get();
            }
            assertEquals(30, total);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(30, s.hooks.requests().size(), "each message once across both instances");
    }

    /** Stands in for the process stopping. */
    static final class ProcessStopped extends Error {
    }

    /** Stops the process before and/or after handing the message to the real channel, once. */
    private record CrashingChannel(Channel real, boolean before, boolean after, AtomicBoolean done) implements Channel {
        CrashingChannel(Channel real, boolean before, boolean after) {
            this(real, before, after, new AtomicBoolean());
        }

        public ChannelKind kind() {
            return real.kind();
        }

        public SendResult send(OutgoingMessage m) {
            if (before && done.compareAndSet(false, true)) {
                throw new ProcessStopped();
            }
            SendResult r = real.send(m);
            if (after && done.compareAndSet(false, true)) {
                throw new ProcessStopped();
            }
            return r;
        }
    }

    private record CrashingSummaryStore(SummaryStore real) implements SummaryStore {
        public void add(SummaryItem item) {
            real.add(item);
        }

        public List<SummaryItem> due(Instant now) {
            return real.due(now);
        }

        public void remove(Collection<String> itemKeys) {
            throw new ProcessStopped();
        }

        public int pending() {
            return real.pending();
        }
    }

    private record CrashingConditionStore(ConditionStore real) implements ConditionStore {
        public ConditionState recordFailure(ConditionKey key, NotificationEvent latest, Instant now) {
            return real.recordFailure(key, latest, now);
        }

        public List<ConditionState> active() {
            return real.active();
        }

        public java.util.Optional<ConditionState> find(ConditionKey key) {
            return real.find(key);
        }

        public void markAlerted(ConditionKey key, int alerts, Instant at) {
            throw new ProcessStopped();
        }

        public void remove(ConditionKey key) {
            real.remove(key);
        }
    }
}
