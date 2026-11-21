package com.documentplatform.notifications.spring;

import com.documentplatform.notifications.DestinationId;
import com.documentplatform.notifications.RetryPolicy;
import com.documentplatform.notifications.email.MailSecurity;
import com.documentplatform.notifications.email.MailServer;
import com.documentplatform.notifications.secret.SecretRef;
import com.documentplatform.notifications.webhook.WebhookOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.documentplatform.notifications.spring.NotificationsProperties.PREFIX;

/**
 * The checked install config: {@link NotificationsProperties} turned into the module's own types, or a
 * {@link NotificationsConfigException} listing every wrong value with its key. Values that may be secrets (anything
 * given as a secret reference, which someone may have filled with the secret itself) are never repeated in a message.
 */
public record NotificationsConfig(Optional<MailServer> mail, Map<DestinationId, SecretRef> teams,
                                  Map<DestinationId, SecretRef> slack, WebhookOptions webhookOptions,
                                  RetryPolicy retry, Duration lease, Duration retention, Duration tickInterval,
                                  Duration pollInterval, int batchSize) {

    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.:\\[\\]_-]{1,253}");
    private static final Pattern ADDRESS = Pattern.compile("[^@\\s]+@[^@\\s]+");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Duration SECOND = Duration.ofSeconds(1);
    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);
    private static final String REF_SHAPE = "not a valid secret reference (letters, digits and . _ : / -, no \"//\")";

    public NotificationsConfig {
        teams = Map.copyOf(teams);
        slack = Map.copyOf(slack);
    }

    /** Every destination id the config names, with its channel's name. */
    public Map<DestinationId, String> webhookDestinations() {
        Map<DestinationId, String> all = new LinkedHashMap<>();
        teams.keySet().stream().sorted((a, b) -> a.value().compareTo(b.value())).forEach(id -> all.put(id, "Teams"));
        slack.keySet().stream().sorted((a, b) -> a.value().compareTo(b.value())).forEach(id -> all.put(id, "Slack"));
        return all;
    }

    /** Checks the properties and builds the config. */
    public static NotificationsConfig from(NotificationsProperties p) {
        List<String> problems = new ArrayList<>();
        Optional<MailServer> mail = mail(p.getMail(), problems);
        Map<DestinationId, SecretRef> teams = webhooks("teams", p.getTeams(), problems);
        Map<DestinationId, SecretRef> slack = webhooks("slack", p.getSlack(), problems);
        teams.keySet().stream().filter(slack::containsKey).forEach(id -> problems.add(PREFIX + ".slack." + id
                + ": the id is also used under " + PREFIX + ".teams; each destination id names one destination"));

        NotificationsProperties.Webhook w = p.getWebhook();
        boolean webhookOk = positive("webhook.connect-timeout", w.getConnectTimeout(), problems)
                & positive("webhook.request-timeout", w.getRequestTimeout(), problems);
        WebhookOptions webhookOptions = webhookOk
                ? WebhookOptions.DEFAULT.withTimeouts(w.getConnectTimeout(), w.getRequestTimeout()) : null;

        RetryPolicy retry = retry(p.getRetry(), problems);

        Duration lease = p.getLease();
        if (atLeast("lease", lease, SECOND, problems)) {
            Duration longest = Duration.ZERO;
            String which = null;
            if (mail.isPresent()) {
                longest = mail.get().connectTimeout().plus(mail.get().readTimeout());
                which = "mail.connect-timeout + mail.read-timeout";
            }
            if (webhookOptions != null && !(teams.isEmpty() && slack.isEmpty())) {
                Duration hook = webhookOptions.connectTimeout().plus(webhookOptions.requestTimeout());
                if (hook.compareTo(longest) > 0) {
                    longest = hook;
                    which = "webhook.connect-timeout + webhook.request-timeout";
                }
            }
            if (which != null && lease.compareTo(longest) <= 0) {
                problems.add(PREFIX + ".lease: must be longer than the longest send (" + which + " = "
                        + longest.toSeconds() + "s), was " + lease.toSeconds() + "s; otherwise a slow send is taken "
                        + "for a stopped one and sent twice");
            }
        }
        Duration retention = p.getRetention();
        if (retention == null) {
            problems.add(PREFIX + ".retention: missing");
        } else if (retention.isNegative()) {
            problems.add(PREFIX + ".retention: must not be negative");
        }
        between("tick-interval", p.getTickInterval(), SECOND, FIVE_MINUTES, problems);
        between("sender.poll-interval", p.getSender().getPollInterval(), SECOND, FIVE_MINUTES, problems);
        int batch = p.getSender().getBatchSize();
        if (batch < 1 || batch > 1000) {
            problems.add(PREFIX + ".sender.batch-size: must be between 1 and 1000, was " + batch);
        }

        if (!problems.isEmpty()) {
            throw new NotificationsConfigException(problems);
        }
        return new NotificationsConfig(mail, teams, slack, webhookOptions, retry, lease, retention,
                p.getTickInterval(), p.getSender().getPollInterval(), batch);
    }

    private static Optional<MailServer> mail(NotificationsProperties.Mail m, List<String> problems) {
        String key = PREFIX + ".mail.";
        boolean anySet = m.getHost() != null || m.getPort() != null || m.getUsername() != null
                || m.getPasswordSecret() != null || m.getFromAddress() != null || m.getFromName() != null;
        if (!anySet) {
            return Optional.empty();
        }
        int before = problems.size();
        if (blank(m.getHost())) {
            problems.add(key + "host: missing; give the mail server's host name, or remove the other "
                    + key + "* settings to switch e-mail off");
        } else if (!HOST.matcher(m.getHost()).matches()) {
            problems.add(key + "host: not a valid host name or address");
        }
        MailSecurity security = null;
        try {
            security = MailSecurity.valueOf(m.getSecurity() == null ? "" : m.getSecurity().trim()
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add(key + "security: must be STARTTLS, TLS or NONE");
        }
        int port = m.getPort() != null ? m.getPort()
                : security == null ? 0 : switch (security) {
                    case STARTTLS -> 587;
                    case TLS -> 465;
                    case NONE -> 25;
                };
        if (m.getPort() != null && (port < 1 || port > 65535)) {
            problems.add(key + "port: must be between 1 and 65535, was " + port);
        }
        boolean user = !blank(m.getUsername());
        boolean pw = !blank(m.getPasswordSecret());
        if (m.getUsername() != null && !user) {
            problems.add(key + "username: empty; leave it out for a relay that takes mail without a login");
        }
        if (user && !pw) {
            problems.add(key + "password-secret: missing; a login (" + key + "username) needs the secret reference "
                    + "of its password");
        }
        if (pw && !user) {
            problems.add(key + "username: missing; " + key + "password-secret is set, so the login needs its user name");
        }
        SecretRef password = null;
        if (pw) {
            password = ref(key + "password-secret", m.getPasswordSecret(), "the mail password", problems);
        }
        if (blank(m.getFromAddress())) {
            problems.add(key + "from-address: missing; give the sender address, for example "
                    + "notifications@yourcompany.com");
        } else if (!ADDRESS.matcher(m.getFromAddress().trim()).matches()) {
            problems.add(key + "from-address: not a valid e-mail address");
        }
        if (m.getFromName() != null && (m.getFromName().isBlank()
                || m.getFromName().chars().anyMatch(Character::isISOControl))) {
            problems.add(key + "from-name: must be a non-empty display name without control characters");
        }
        boolean timeouts = positive("mail.connect-timeout", m.getConnectTimeout(), problems)
                & positive("mail.read-timeout", m.getReadTimeout(), problems);
        if (problems.size() > before || !timeouts) {
            return Optional.empty();
        }
        MailServer server = new MailServer(m.getHost().trim(), port, security, user ? m.getUsername().trim() : null,
                password, m.getFromAddress().trim(), m.getFromName(), m.getConnectTimeout(), m.getReadTimeout());
        return Optional.of(server);
    }

    private static Map<DestinationId, SecretRef> webhooks(String channel, Map<String, String> config,
                                                         List<String> problems) {
        Map<DestinationId, SecretRef> out = new LinkedHashMap<>();
        if (config == null) {
            return out;
        }
        int n = 0;
        for (Map.Entry<String, String> e : config.entrySet()) {
            n++;
            String id = e.getKey();
            if (id == null || !ID.matcher(id).matches()) {
                problems.add(PREFIX + "." + channel + ": destination id number " + n + " is not valid (letters, "
                        + "digits, '.', '_' and '-', at most 128 characters)");
                continue;
            }
            String key = PREFIX + "." + channel + "." + id;
            if (blank(e.getValue())) {
                problems.add(key + ": missing the secret reference of the webhook address");
                continue;
            }
            SecretRef ref = ref(key, e.getValue(), "the webhook address", problems);
            if (ref != null) {
                out.put(DestinationId.of(id), ref);
            }
        }
        return out;
    }

    private static RetryPolicy retry(NotificationsProperties.Retry r, List<String> problems) {
        boolean ok = atLeast("retry.first-delay", r.getFirstDelay(), SECOND, problems)
                & atLeast("retry.max-delay", r.getMaxDelay(), SECOND, problems)
                & positive("retry.give-up-after", r.getGiveUpAfter(), problems);
        if (ok && r.getMaxDelay().compareTo(r.getFirstDelay()) < 0) {
            problems.add(PREFIX + ".retry.max-delay: must not be shorter than retry.first-delay ("
                    + r.getFirstDelay().toSeconds() + "s), was " + r.getMaxDelay().toSeconds() + "s");
            ok = false;
        }
        return ok ? new RetryPolicy(r.getFirstDelay(), r.getMaxDelay(), r.getGiveUpAfter()) : null;
    }

    /** The value is not repeated: someone may have put the secret itself here. */
    private static SecretRef ref(String key, String value, String what, List<String> problems) {
        String v = value.trim();
        try {
            return SecretRef.of(v);
        } catch (IllegalArgumentException e) {
            problems.add(key + ": " + REF_SHAPE + "; give the path of the secret that holds " + what
                    + ", never " + what + " itself" + (v.contains("//") || v.contains("@")
                    ? " (this value looks like an address: store it in the secret store and give its path here)"
                    : ""));
            return null;
        }
    }

    private static boolean positive(String name, Duration d, List<String> problems) {
        if (d == null) {
            problems.add(PREFIX + "." + name + ": missing");
            return false;
        }
        if (d.isNegative() || d.isZero()) {
            problems.add(PREFIX + "." + name + ": must be positive");
            return false;
        }
        return true;
    }

    private static boolean atLeast(String name, Duration d, Duration min, List<String> problems) {
        if (d == null) {
            problems.add(PREFIX + "." + name + ": missing");
            return false;
        }
        if (d.compareTo(min) < 0) {
            problems.add(PREFIX + "." + name + ": must be at least " + min.toSeconds() + "s, was " + d);
            return false;
        }
        return true;
    }

    private static void between(String name, Duration d, Duration min, Duration max, List<String> problems) {
        if (atLeast(name, d, min, problems) && d.compareTo(max) > 0) {
            problems.add(PREFIX + "." + name + ": must be at most " + max.toMinutes() + " minutes, was " + d);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
