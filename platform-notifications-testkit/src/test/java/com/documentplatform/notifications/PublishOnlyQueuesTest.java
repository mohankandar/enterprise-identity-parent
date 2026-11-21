package com.documentplatform.notifications;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.documentplatform.notifications.Fixture.APPROVAL_WAITING;
import static com.documentplatform.notifications.Fixture.E1;
import static com.documentplatform.notifications.Fixture.STORE_DOWN;
import static com.documentplatform.notifications.Fixture.TENANT;
import static com.documentplatform.notifications.Fixture.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner answer to P004 question 3: publish only saves to the outbox; a background sender sends. */
class PublishOnlyQueuesTest {

    private final Fixture f = new Fixture();

    private static NotificationEvent approval(String id) {
        return event(APPROVAL_WAITING).entity(E1).identifier(RecordIdentifier.of(id)).build();
    }

    @Test
    void publishSavesWakesTheSenderAndNeverCallsAChannel() {
        AtomicInteger wakes = new AtomicInteger();
        Notifier n = Notifier.builder(f.settings, f.resolver).channels(List.of(f.email)).clock(f.clock)
                .outbox(f.outbox).summaries(f.summaries).conditions(f.conditions).whenQueued(wakes::incrementAndGet)
                .build();
        List<DeliveryReport> reports = n.publish(approval("C-1"));
        assertEquals(DeliveryStatus.PENDING, reports.get(0).status());
        assertTrue(f.email.sent().isEmpty() && f.email.attempts() == 0, "no channel call in publish");
        assertEquals(OutboxState.PENDING, f.outbox.all().get(0).state());
        assertEquals(1, wakes.get());

        assertEquals(DeliveryStatus.SENT, n.sendDue(f.clock.instant()).get(0).status());
        assertEquals(1, f.email.sent().size());
        assertTrue(n.sendDue(f.clock.instant()).isEmpty());
    }

    @Test
    void queueDueRaisesAlertsWithoutSending() {
        AtomicInteger wakes = new AtomicInteger();
        Notifier n = Notifier.builder(f.settings, f.resolver).channels(List.of(f.email)).clock(f.clock)
                .outbox(f.outbox).summaries(f.summaries).conditions(f.conditions).whenQueued(wakes::incrementAndGet)
                .build();
        n.conditionFailing(event(STORE_DOWN).build());
        assertEquals(0, wakes.get(), "nothing due yet");
        f.clock.advance(Duration.ofMinutes(15));
        assertEquals(List.of(DeliveryStatus.PENDING),
                n.queueDue(f.clock.instant()).stream().map(DeliveryReport::status).toList());
        assertEquals(0, f.email.attempts());
        assertEquals(1, wakes.get());
        assertEquals(DeliveryStatus.SENT, n.sendDue(f.clock.instant()).get(0).status());
    }

    @Test
    void publishGoesOnWhileTheSenderIsStuckInAChannel() throws Exception {
        CountDownLatch inSend = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Channel stuck = new Channel() {
            public ChannelKind kind() {
                return ChannelKind.EMAIL;
            }

            public SendResult send(OutgoingMessage m) {
                inSend.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return SendResult.ok();
            }
        };
        Notifier n = f.notifier(f.settings, f.resolver, List.of(stuck));
        n.publish(approval("C-1"));
        CompletableFuture<List<DeliveryReport>> sender = CompletableFuture.supplyAsync(() -> n.sendDue(f.clock.instant()));
        assertTrue(inSend.await(5, TimeUnit.SECONDS));

        CompletableFuture<List<DeliveryReport>> second = CompletableFuture.supplyAsync(() -> n.publish(approval("C-2")));
        assertEquals(DeliveryStatus.PENDING, second.get(2, TimeUnit.SECONDS).get(0).status(),
                "publish did not wait for the stuck send");
        n.conditionFailing(event(STORE_DOWN).build());
        assertFalse(sender.isDone());

        release.countDown();
        assertEquals(DeliveryStatus.SENT, sender.get(5, TimeUnit.SECONDS).get(0).status());
        assertEquals(TENANT, sender.get().get(0).tenant());
    }
}
