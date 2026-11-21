package com.documentplatform.notifications.spring;

import com.documentplatform.notifications.ChannelKind;
import com.documentplatform.notifications.ConditionTiming;
import com.documentplatform.notifications.DestinationId;
import com.documentplatform.notifications.EntityId;
import com.documentplatform.notifications.EventCatalogue;
import com.documentplatform.notifications.EventKind;
import com.documentplatform.notifications.EventType;
import com.documentplatform.notifications.EventTypeId;
import com.documentplatform.notifications.NotificationEvent;
import com.documentplatform.notifications.Recipient;
import com.documentplatform.notifications.RecipientResolver;
import com.documentplatform.notifications.RecordIdentifier;
import com.documentplatform.notifications.RoleId;
import com.documentplatform.notifications.Schedule;
import com.documentplatform.notifications.TenantId;
import com.documentplatform.notifications.TenantSettings;
import com.documentplatform.notifications.secret.Secret;
import com.documentplatform.notifications.secret.SecretRef;
import com.documentplatform.notifications.secret.SecretResolver;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.fail;

/** What an app supplies (catalogue, recipient resolver, secret resolver), made up for the tests, and helpers. */
final class TestApp {

    static final RoleId IT_ADMIN = RoleId.of("it-admin");
    static final RoleId APPROVER = RoleId.of("approver");
    static final EventTypeId APPROVAL = EventTypeId.of("work.approval-waiting");
    static final EventTypeId STORE_DOWN = EventTypeId.of("service.store-down");
    static final EventTypeId REJECTED = EventTypeId.of("intake.files-rejected");
    static final TenantId TENANT = TenantId.of("t1");
    static final EntityId E1 = EntityId.of("e1");
    static final DestinationId MAIL = DestinationId.of("mail");
    static final DestinationId TEAMS = DestinationId.of("ops-teams");
    static final DestinationId SLACK = DestinationId.of("ops-slack");
    static final Recipient ADMIN = new Recipient("u-admin", "admin@customer.example");

    static final String MAIL_PASSWORD = "Mail-Pw-SECRET-123";
    static final String MAIL_REF = "notifications/mail-password";
    static final String TEAMS_REF = "notifications/ops-teams";
    static final String SLACK_REF = "notifications/ops-slack";

    /** The secret store: references to secrets. Tests change it. */
    static final Map<SecretRef, String> SECRETS = new ConcurrentHashMap<>();

    private TestApp() {
    }

    /** The beans an app defines. */
    @Configuration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        EventCatalogue catalogue() {
            return EventCatalogue.builder(IT_ADMIN)
                    .register(EventType.builder(APPROVAL.value(), "Approval waiting", EventKind.BUSINESS)
                            .schedule(Schedule.immediately()).recipients(APPROVER).build())
                    .register(EventType.builder(STORE_DOWN.value(), "Store down", EventKind.TECHNICAL).critical()
                            .continuous(new ConditionTiming(Duration.ofSeconds(1), Duration.ofMinutes(15)))
                            .recipients(IT_ADMIN).build())
                    .register(EventType.builder(REJECTED.value(), "Files rejected", EventKind.TECHNICAL)
                            .schedule(Schedule.everyHour()).recipients(IT_ADMIN).count("files", "Files").build())
                    .build();
        }

        @Bean
        RecipientResolver recipients() {
            return (tenant, entity, role) -> Set.of(ADMIN);
        }

        @Bean
        SecretResolver secrets() {
            return ref -> Optional.ofNullable(SECRETS.get(ref)).map(Secret::of);
        }
    }

    /** The app beans without a catalogue: a wiring mistake the startup must name. */
    @Configuration(proxyBeanMethods = false)
    static class NoCatalogue {
        @Bean
        RecipientResolver recipients() {
            return (tenant, entity, role) -> Set.of(ADMIN);
        }

        @Bean
        SecretResolver secrets() {
            return ref -> Optional.empty();
        }
    }

    static GreenMail startMail() {
        GreenMail mail = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP).dynamicPort())
                .withConfiguration(GreenMailConfiguration.aConfig()
                        .withUser("notifications@customer.example", "notify", MAIL_PASSWORD));
        mail.start();
        SECRETS.put(SecretRef.of(MAIL_REF), MAIL_PASSWORD);
        return mail;
    }

    /** Properties for the mail server, plus the given ones. */
    static String[] mailProperties(int port, String... more) {
        List<String> p = new ArrayList<>(List.of(
                "platform.notifications.mail.host=127.0.0.1",
                "platform.notifications.mail.port=" + port,
                "platform.notifications.mail.security=none",
                "platform.notifications.mail.username=notify",
                "platform.notifications.mail.password-secret=" + MAIL_REF,
                "platform.notifications.mail.from-address=notifications@customer.example",
                "platform.notifications.mail.from-name=Notifications",
                "platform.notifications.mail.connect-timeout=2s",
                "platform.notifications.mail.read-timeout=2s",
                "platform.notifications.sender.poll-interval=1s"));
        p.addAll(List.of(more));
        return p.toArray(String[]::new);
    }

    static TenantSettings tenant() {
        return new TenantSettings(URI.create("https://app.customer.example/"), ZoneOffset.UTC,
                Map.of(MAIL, ChannelKind.EMAIL, TEAMS, ChannelKind.TEAMS, SLACK, ChannelKind.SLACK));
    }

    static NotificationEvent approval(String id) {
        return NotificationEvent.builder(APPROVAL, TENANT).entity(E1).identifier(RecordIdentifier.of(id)).build();
    }

    /** Waits up to {@code timeout} for the condition. */
    static void await(Duration timeout, String what, BooleanSupplier condition) {
        Instant end = Instant.now().plus(timeout);
        while (Instant.now().isBefore(end)) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted");
            }
        }
        fail("timed out waiting for: " + what);
    }
}
