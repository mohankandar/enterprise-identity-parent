package com.documentplatform.notifications;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * A message ready for a channel. Built only by this module, from catalogue labels and the event's structured fields,
 * so it carries the event's name, the record's identifier, counts and times, and a link into the app — nothing else.
 * Channels format it (P004); they add no content.
 *
 * @param link the main link into the app; null if the event had none
 */
public record Message(MessageKind kind, String subject, List<String> lines, URI link) {

    public Message {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subject, "subject");
        lines = List.copyOf(lines);
    }

    /** Plain-text form: subject, lines, link. */
    public String text() {
        StringBuilder b = new StringBuilder(subject);
        lines.forEach(line -> b.append('\n').append(line));
        if (link != null) {
            b.append('\n').append(link);
        }
        return b.toString();
    }
}
