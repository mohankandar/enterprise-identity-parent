package com.documentplatform.notifications.spring;

import com.documentplatform.notifications.DeliveryStatus;
import com.documentplatform.notifications.NotificationEvent;
import com.documentplatform.notifications.NotificationSettings;
import com.documentplatform.notifications.Notifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static com.documentplatform.notifications.spring.TestApp.TENANT;
import static com.documentplatform.notifications.spring.TestApp.approval;
import static com.documentplatform.notifications.spring.TestApp.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner rule: publish only saves to the outbox and never waits on a channel. */
class SlowChannelTest {

    /** A "mail server" that takes the connection and never says a word. */
    private final ServerSocket silent;
    private final List<Socket> held = new CopyOnWriteArrayList<>();
    private final AtomicInteger connections = new AtomicInteger();

    SlowChannelTest() throws IOException {
        silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread t = new Thread(() -> {
            while (!silent.isClosed()) {
                try {
                    held.add(silent.accept());
                    connections.incrementAndGet();
                } catch (IOException closed) {
                    return;
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @AfterEach
    void close() throws IOException {
        silent.close();
        for (Socket s : held) {
            s.close();
        }
    }

    @Test
    void publishReturnsAtOnceWhileTheSenderWaitsOnASlowMailServer() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(NotificationsAutoConfiguration.class))
                .withUserConfiguration(TestApp.Beans.class)
                .withBean(DataSource.class, () -> Db.migrated().owner)
                .withPropertyValues(
                        "platform.notifications.mail.host=127.0.0.1",
                        "platform.notifications.mail.port=" + silent.getLocalPort(),
                        "platform.notifications.mail.security=none",
                        "platform.notifications.mail.from-address=notifications@customer.example",
                        "platform.notifications.mail.read-timeout=4s",
                        "platform.notifications.sender.poll-interval=1s")
                .run(ctx -> {
                    ctx.getBean(NotificationSettings.class).connectChannels(TENANT, TestApp.tenant());
                    Notifier n = ctx.getBean(Notifier.class);

                    assertEquals(DeliveryStatus.PENDING, timed(() -> n.publish(approval("LN 1")).get(0).status()));
                    await(Duration.ofSeconds(5), "the sender connected and waits",
                            () -> connections.get() == 1);

                    // The sender is now stuck in the mail server's silence for up to 4 s, holding a message.
                    for (int i = 2; i <= 5; i++) {
                        String id = "LN " + i;
                        assertEquals(DeliveryStatus.PENDING, timed(() -> n.publish(approval(id)).get(0).status()));
                    }
                    timed(() -> n.conditionFailing(NotificationEvent.builder(TestApp.STORE_DOWN, TENANT).build()));
                    timed(() -> n.queueDue(java.time.Instant.now()));
                    assertEquals(1, connections.get(), "still in the first send");

                    // The read timeout ends the attempt; the destination shows the specific problem.
                    await(Duration.ofSeconds(10), "the timeout is recorded", () -> mailState(ctx.getBean(
                            NotificationsHealthIndicator.class)).equals("Having problems/MAIL_TIMEOUT"));
                });
    }

    /** Runs the call and fails if it took more than half a second. */
    private static <T> T timed(java.util.function.Supplier<T> call) {
        long start = System.nanoTime();
        T result = call.get();
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ms < 500, "took " + ms + " ms");
        return result;
    }

    @SuppressWarnings("unchecked")
    static String mailState(NotificationsHealthIndicator indicator) {
        Map<String, Object> destinations = (Map<String, Object>) indicator.health().getDetails().get("destinations");
        Map<String, Object> mail = (Map<String, Object>) destinations.get("mail");
        return mail == null ? "unknown" : mail.get("state") + "/" + mail.get("code");
    }
}
