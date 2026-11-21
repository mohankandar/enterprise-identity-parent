package com.documentplatform.notifications.spring;

import com.documentplatform.notifications.DeliveryReport;
import com.documentplatform.notifications.Notifier;
import com.documentplatform.notifications.jdbc.NotificationStoreException;
import org.springframework.context.SmartLifecycle;

import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The two background threads of each app instance:
 *
 * <ul>
 *   <li><b>tick</b>, every {@code platform.notifications.tick-interval} (default 1 minute): raises due condition
 *       alerts, puts due summaries in the outbox and clears out old messages ({@link Notifier#queueDue}). It never
 *       sends, so it runs on time even while a channel is slow.</li>
 *   <li><b>sender</b>: woken by every publish, so Immediately messages go out at once, and otherwise every
 *       {@code platform.notifications.sender.poll-interval} (default 10 seconds) for retries that came due and for
 *       messages other instances saved. It sends batch after batch while messages are due
 *       ({@link Notifier#sendDue}).</li>
 * </ul>
 *
 * <p>Safe with several app instances on one database: every message is claimed by one sender
 * ({@code FOR UPDATE SKIP LOCKED}), and alerts and summaries raised by two instances at once get the same key, so the
 * outbox keeps one. No cluster lock is needed.
 *
 * <p>A failure (database unreachable) is logged once with its neutral reason, then again when it clears; the threads
 * keep running.
 */
final class NotificationWorkers implements SmartLifecycle {

    private static final System.Logger LOG = System.getLogger(NotificationWorkers.class.getName());
    private static final Duration STOP_WAIT = Duration.ofSeconds(10);

    private final Notifier notifier;
    private final SenderSignal signal;
    private final Clock clock;
    private final Duration tickInterval;
    private final Duration pollInterval;

    private volatile boolean running;
    private ScheduledExecutorService ticker;
    private Thread sender;
    private final Failures tickFailures = new Failures("tick");
    private final Failures sendFailures = new Failures("sender");

    NotificationWorkers(Notifier notifier, SenderSignal signal, Clock clock, Duration tickInterval,
                        Duration pollInterval) {
        this.notifier = notifier;
        this.signal = signal;
        this.clock = clock;
        this.tickInterval = tickInterval;
        this.pollInterval = pollInterval;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        ticker = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "platform-notifications-tick"));
        ticker.scheduleWithFixedDelay(this::tickOnce, tickInterval.toMillis(), tickInterval.toMillis(),
                TimeUnit.MILLISECONDS);
        sender = daemon(this::sendLoop, "platform-notifications-sender");
        sender.start();
        LOG.log(Level.INFO, "Notifications started: tick every {0}s, sender polling every {1}s",
                tickInterval.toSeconds(), pollInterval.toSeconds());
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        ticker.shutdown();
        signal.raise();
        try {
            ticker.awaitTermination(STOP_WAIT.toMillis(), TimeUnit.MILLISECONDS);
            // A send in progress is allowed to finish; one cut off is sent again after its lease, under its key.
            sender.join(STOP_WAIT.toMillis());
            if (sender.isAlive()) {
                sender.interrupt();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void tickOnce() {
        try {
            notifier.queueDue(clock.instant());
            tickFailures.cleared();
        } catch (RuntimeException e) {
            tickFailures.failed(e);
        }
    }

    private void sendLoop() {
        while (running) {
            try {
                List<DeliveryReport> sent;
                do {
                    sent = notifier.sendDue(clock.instant());
                } while (running && sent.size() >= notifier.batchSize());
                sendFailures.cleared();
            } catch (RuntimeException e) {
                sendFailures.failed(e);
            }
            try {
                signal.await(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    /** Logs the first failure of a run of failures and the recovery, not every failed round. */
    private static final class Failures {
        private final String worker;
        private volatile boolean failing;

        Failures(String worker) {
            this.worker = worker;
        }

        void failed(RuntimeException e) {
            if (!failing) {
                failing = true;
                // Our store exceptions name the operation and SQL state only; any other text is not logged, as it
                // may carry connection details.
                String reason = e instanceof NotificationStoreException ? e.getMessage() : e.getClass().getName();
                LOG.log(Level.WARNING, "Notifications {0} failed and keeps trying: {1}", worker, reason);
            }
        }

        void cleared() {
            if (failing) {
                failing = false;
                LOG.log(Level.INFO, "Notifications {0} works again", worker);
            }
        }
    }
}
