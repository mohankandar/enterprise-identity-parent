package com.documentplatform.notifications.jdbc;

import com.documentplatform.notifications.AppLink;
import com.documentplatform.notifications.EntityId;
import com.documentplatform.notifications.EventTypeId;
import com.documentplatform.notifications.NotificationEvent;
import com.documentplatform.notifications.RecordIdentifier;
import com.documentplatform.notifications.TenantId;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** JDBC helpers shared by the Postgres stores. */
final class Sql {

    interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    private Sql() {
    }

    static <T> T run(DataSource ds, String operation, Work<T> work) {
        try (Connection c = ds.getConnection()) {
            return work.run(c);
        } catch (SQLException e) {
            throw new NotificationStoreException(operation, e);
        }
    }

    static <T> T inTransaction(DataSource ds, String operation, Work<T> work) {
        try (Connection c = ds.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            throw new NotificationStoreException(operation, e);
        }
    }

    static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime t = rs.getObject(column, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }

    static Array textArray(Connection c, Collection<String> values) throws SQLException {
        return c.createArrayOf("text", values.stream().sorted().toArray(String[]::new));
    }

    static <T> List<T> list(ResultSet rs, String column, Function<String, T> parse) throws SQLException {
        Array a = rs.getArray(column);
        return a == null ? List.of() : Arrays.stream((String[]) a.getArray()).map(parse).toList();
    }

    /** {@code name=value,...}. Names are field names ([a-z][A-Za-z0-9]*), so ',' and '=' cannot occur in them. */
    static String writeMap(Map<String, ?> map) {
        return map.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","));
    }

    static <V> Map<String, V> readMap(String text, Function<String, V> value) {
        Map<String, V> map = new LinkedHashMap<>();
        if (text != null && !text.isEmpty()) {
            for (String pair : text.split(",")) {
                int eq = pair.indexOf('=');
                map.put(pair.substring(0, eq), value.apply(pair.substring(eq + 1)));
            }
        }
        return map;
    }

    /** '' for none, in key columns that cannot be null. */
    static String orEmpty(Object id) {
        return id == null ? "" : id.toString();
    }

    static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /** Sets the event columns shared by the summary and condition tables, from {@code first} on. */
    static int bindEventFields(PreparedStatement ps, int first, NotificationEvent e) throws SQLException {
        int i = first;
        ps.setString(i++, writeMap(e.counts()));
        ps.setString(i++, writeMap(e.times()));
        ps.setString(i++, e.link() == null ? null : e.link().path());
        ps.setObject(i++, ts(e.occurredAt()));
        return i;
    }

    /** Reads an event from columns tenant_id, event_type, entity_id, identifier, counts, times, link, occurred_at. */
    static NotificationEvent event(ResultSet rs) throws SQLException {
        String entity = nullIfEmpty(rs.getString("entity_id"));
        String identifier = nullIfEmpty(rs.getString("identifier"));
        String link = rs.getString("link");
        return new NotificationEvent(EventTypeId.of(rs.getString("event_type")), TenantId.of(rs.getString("tenant_id")),
                entity == null ? null : EntityId.of(entity),
                identifier == null ? null : RecordIdentifier.of(identifier),
                readMap(rs.getString("counts"), Long::valueOf), readMap(rs.getString("times"), Instant::parse),
                link == null ? null : AppLink.of(link), instant(rs, "occurred_at"));
    }
}
