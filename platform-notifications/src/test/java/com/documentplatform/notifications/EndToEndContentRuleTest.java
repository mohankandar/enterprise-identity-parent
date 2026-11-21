package com.documentplatform.notifications;

import com.documentplatform.notifications.support.Stack;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.documentplatform.notifications.support.TestCatalogue.APPROVAL;
import static com.documentplatform.notifications.support.TestCatalogue.CY;
import static com.documentplatform.notifications.support.TestCatalogue.E1;
import static com.documentplatform.notifications.support.TestCatalogue.MAIL;
import static com.documentplatform.notifications.support.TestCatalogue.REJECTED;
import static com.documentplatform.notifications.support.TestCatalogue.SLACK;
import static com.documentplatform.notifications.support.TestCatalogue.TEAMS;
import static com.documentplatform.notifications.support.TestCatalogue.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The content rule, end to end: what reaches the mail server, Teams and Slack is the event's name, the record's
 * identifier, counts, times and a link into the app — plus the fixed words of each channel's format, and nothing else.
 */
class EndToEndContentRuleTest {

    /** Keys and fixed values of the Teams and Slack formats. Every other string must come from the message. */
    private static final Set<String> FORMAT = Set.of(
            "type", "attachments", "contentType", "contentUrl", "content", "$schema", "version", "body", "text",
            "weight", "size", "wrap", "spacing", "actions", "title", "url", "blocks", "elements", "emoji",
            "message", "application/vnd.microsoft.card.adaptive", "http://adaptivecards.io/schemas/adaptive-card.json",
            "AdaptiveCard", "1.4", "TextBlock", "Bolder", "Medium", "None", "Action.OpenUrl", "Open in the app",
            "header", "plain_text", "section", "button");

    private static final List<String> NEVER = List.of(Stack.MAIL_PASSWORD, Stack.TEAMS_SIG, Stack.SLACK_TOKEN,
            "notifications/t1", TEAMS.value(), SLACK.value(), "127.0.0.1", "@customer.example", "u-cy", "u-ann");

    private Stack s;

    @BeforeEach
    void start() throws Exception {
        s = new Stack();
    }

    @AfterEach
    void stop() {
        s.close();
    }

    @Test
    void anEventReachesAllThreeChannelsWithOnlyItsAllowedContent() throws Exception {
        s.routeBusiness(E1, APPROVAL, MAIL, TEAMS, SLACK);
        Notifier n = s.notifier();
        List<DeliveryReport> reports = s.publish(n, NotificationEvent.builder(APPROVAL, TENANT).entity(E1)
                .identifier(RecordIdentifier.of("LN 12345")).time("due", Instant.parse("2026-11-21T09:00:00Z"))
                .link(AppLink.of("/approvals/LN-12345")).build());
        assertEquals(List.of(DeliveryStatus.SENT, DeliveryStatus.SENT, DeliveryStatus.SENT),
                reports.stream().map(DeliveryReport::status).toList());

        List<String> parts = List.of("Approval waiting: LN 12345", "Time: 2026-11-20 10:05 Z", "Due: 2026-11-21 09:00 Z",
                "https://app.customer.example/approvals/LN-12345");

        MimeMessage mail = s.mail.getReceivedMessages()[0];
        assertEquals(parts.get(0), mail.getSubject());
        assertEquals(String.join("\n", parts), ((String) mail.getContent()).replace("\r\n", "\n").stripTrailing());
        assertEquals(1, mail.getAllRecipients().length);
        assertEquals(CY.emailAddress(), mail.getAllRecipients()[0].toString());

        for (var req : s.hooks.requests()) {
            Set<String> allowed = new HashSet<>(FORMAT);
            allowed.addAll(parts);
            allowed.add(String.join("\n", parts.subList(1, 3))); // Slack section: the lines together
            for (String value : jsonStrings(req.body())) {
                assertTrue(allowed.contains(value), "not allowed in a message: " + value);
            }
            assertNever(req.body());
        }
        assertNever((String) mail.getContent());
        assertNever(mail.getSubject());
    }

    @Test
    void summariesHoldOnlyLabelsIdentifiersCountsTimesAndLinks() throws Exception {
        s.routeTechnical(REJECTED, Schedule.everyHour(), MAIL, TEAMS, SLACK);
        Notifier n = s.notifier();
        for (int i = 1; i <= 12; i++) {
            n.publish(NotificationEvent.builder(REJECTED, TENANT).identifier(RecordIdentifier.of("batch " + i))
                    .count("files", i).link(AppLink.of("/intake/batch-" + i)).build());
            s.clock.advance(Duration.ofMinutes(1));
        }
        s.clock.advance(Duration.ofMinutes(43)); // 11:00
        assertEquals(3, n.tick(s.clock.instant()).stream().filter(r -> r.status() == DeliveryStatus.SENT).count());

        List<String> lines = summaryLines();
        Pattern line = Pattern.compile(String.join("|",
                "Period: \\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d Z to \\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d Z",
                "Files rejected: \\d+",
                "  Files \\(total\\): \\d+",
                "  - batch \\d+, \\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d Z https://app\\.customer\\.example/intake/batch-\\d+",
                "  \\.\\.\\. and \\d+ more"));
        for (String l : lines) {
            assertTrue(line.matcher(l).matches(), "summary line outside the content rule: " + l);
        }
        assertTrue(lines.contains("  Files (total): 78"));
        assertTrue(lines.contains("  ... and 2 more"));

        for (var req : s.hooks.requests()) {
            for (String value : jsonStrings(req.body())) {
                boolean fromMessage = value.equals("Summary: 12 notifications")
                        || value.equals("https://app.customer.example/")
                        || Arrays.stream(value.split("\n")).allMatch(lines::contains);
                assertTrue(FORMAT.contains(value) || fromMessage, "not allowed in a summary: " + value);
            }
            assertNever(req.body());
        }
        assertNever((String) s.mail.getReceivedMessages()[0].getContent());
    }

    private List<String> summaryLines() throws Exception {
        try (Connection c = s.ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select body_lines from notif_outbox where message_kind = 'SUMMARY' limit 1")) {
            assertTrue(rs.next());
            return List.of((String[]) rs.getArray(1).getArray());
        }
    }

    /** Every string (keys and values) in a JSON document, unescaped. */
    static List<String> jsonStrings(String json) {
        Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add(unescape(m.group(1)));
        }
        return out;
    }

    private static String unescape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') {
                b.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> b.append('\n');
                case 'r' -> b.append('\r');
                case 't' -> b.append('\t');
                case 'u' -> {
                    b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> b.append(n);
            }
        }
        return b.toString();
    }

    private static void assertNever(String text) {
        for (String never : NEVER) {
            assertFalse(text.contains(never), "must not appear in a message: " + never);
        }
    }
}
