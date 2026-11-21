package com.documentplatform.notifications.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The SQL migration files of the notification tables (all prefixed {@code notif_}). Apps run them in their own schema
 * step: with Flyway, add the location {@link #LOCATION}; without, run {@link #scripts()} in order with
 * {@link #script(String)}.
 */
public final class NotificationSchema {

    /** Classpath location of the migration files. */
    public static final String LOCATION = "db/platform/notifications";

    private static final List<String> SCRIPTS = List.of("V1__notifications.sql");

    private NotificationSchema() {
    }

    /** File names, in the order they must run. */
    public static List<String> scripts() {
        return SCRIPTS;
    }

    /** The SQL of one migration file. */
    public static String script(String name) {
        if (!SCRIPTS.contains(name)) {
            throw new IllegalArgumentException("Unknown migration file");
        }
        try (InputStream in = NotificationSchema.class.getClassLoader().getResourceAsStream(LOCATION + "/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Migration file missing from the classpath: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
