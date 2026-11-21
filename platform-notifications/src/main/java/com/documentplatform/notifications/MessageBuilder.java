package com.documentplatform.notifications;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds messages under the content rule. Inputs are the catalogue's labels and the event's structured fields; no
 * other text is read, so nothing else can appear: the event's name, the record's identifier, counts and times, and
 * a link into the app.
 */
final class MessageBuilder {

    /** Items listed per event type in a summary; the rest are counted. */
    static final int SUMMARY_ITEMS_PER_TYPE = 10;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    private MessageBuilder() {
    }

    static Message single(Occurrence o, TenantSettings tenant) {
        ZoneId zone = tenant.zone();
        String head = headline(o);
        String subject = switch (o.kind()) {
            case CONDITION_ONGOING -> "Still ongoing: " + head;
            case RECOVERED -> "Recovered: " + head;
            default -> head;
        };
        List<String> lines = new ArrayList<>();
        switch (o.kind()) {
            case EVENT -> lines.add("Time: " + time(o.at(), zone));
            case CONDITION_ALERT -> {
                lines.add("Since: " + time(o.since(), zone));
                lines.add("Failures: " + o.failures());
            }
            case CONDITION_ONGOING -> {
                lines.add("Since: " + time(o.since(), zone));
                lines.add("Duration: " + duration(o.since(), o.at()));
                lines.add("Failures: " + o.failures());
            }
            case RECOVERED -> {
                lines.add("Since: " + time(o.since(), zone));
                lines.add("Recovered at: " + time(o.at(), zone));
                lines.add("Duration: " + duration(o.since(), o.at()));
            }
            case SUMMARY -> throw new IllegalArgumentException("Summaries are built from several occurrences");
        }
        if (o.kind() != MessageKind.RECOVERED) {
            fieldLines(o, zone, lines);
        }
        return new Message(o.kind(), subject, lines, link(o, tenant));
    }

    static Message summary(List<Occurrence> items, TenantSettings tenant) {
        ZoneId zone = tenant.zone();
        Map<EventTypeId, List<Occurrence>> byType = new LinkedHashMap<>();
        items.forEach(o -> byType.computeIfAbsent(o.type().id(), k -> new ArrayList<>()).add(o));
        Instant first = items.stream().map(Occurrence::at).min(Instant::compareTo).orElseThrow();
        Instant last = items.stream().map(Occurrence::at).max(Instant::compareTo).orElseThrow();

        List<String> lines = new ArrayList<>();
        lines.add("Period: " + time(first, zone) + " to " + time(last, zone));
        for (List<Occurrence> group : byType.values()) {
            EventType type = group.get(0).type();
            lines.add(type.name() + ": " + group.size());
            for (FieldSpec f : type.fields()) {
                if (f.type() == FieldSpec.Type.COUNT && group.stream().anyMatch(o -> o.event().counts().containsKey(f.name()))) {
                    long total = group.stream().mapToLong(o -> o.event().counts().getOrDefault(f.name(), 0L)).sum();
                    lines.add("  " + f.label() + " (total): " + total);
                }
            }
            group.stream().limit(SUMMARY_ITEMS_PER_TYPE).forEach(o -> lines.add("  - " + item(o, tenant)));
            if (group.size() > SUMMARY_ITEMS_PER_TYPE) {
                lines.add("  ... and " + (group.size() - SUMMARY_ITEMS_PER_TYPE) + " more");
            }
        }
        String subject = "Summary: " + items.size() + (items.size() == 1 ? " notification" : " notifications");
        return new Message(MessageKind.SUMMARY, subject, lines, tenant.appBaseUrl());
    }

    private static String headline(Occurrence o) {
        RecordIdentifier id = o.event().identifier();
        return id == null ? o.type().name() : o.type().name() + ": " + id.value();
    }

    private static String item(Occurrence o, TenantSettings tenant) {
        String prefix = switch (o.kind()) {
            case CONDITION_ALERT -> "Alert: ";
            case CONDITION_ONGOING -> "Still ongoing: ";
            case RECOVERED -> "Recovered: ";
            default -> "";
        };
        RecordIdentifier id = o.event().identifier();
        URI link = link(o, tenant);
        return prefix + (id == null ? o.type().name() : id.value()) + ", " + time(o.at(), tenant.zone())
                + (link == null ? "" : " " + link);
    }

    private static void fieldLines(Occurrence o, ZoneId zone, List<String> lines) {
        for (FieldSpec f : o.type().fields()) {
            if (f.type() == FieldSpec.Type.COUNT && o.event().counts().containsKey(f.name())) {
                lines.add(f.label() + ": " + o.event().counts().get(f.name()));
            } else if (f.type() == FieldSpec.Type.TIME && o.event().times().containsKey(f.name())) {
                lines.add(f.label() + ": " + time(o.event().times().get(f.name()), zone));
            }
        }
    }

    private static URI link(Occurrence o, TenantSettings tenant) {
        AppLink link = o.event().link();
        return link == null ? null : link.resolve(tenant.appBaseUrl());
    }

    static String time(Instant instant, ZoneId zone) {
        return TIME.format(instant.atZone(zone));
    }

    static String duration(Instant from, Instant to) {
        Duration d = Duration.between(from, to);
        long hours = d.toHours();
        int minutes = d.toMinutesPart();
        return hours == 0 ? minutes + " min" : hours + " h " + minutes + " min";
    }
}
