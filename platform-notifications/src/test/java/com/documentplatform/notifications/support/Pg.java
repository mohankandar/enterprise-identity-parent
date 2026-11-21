package com.documentplatform.notifications.support;

import com.documentplatform.notifications.jdbc.NotificationSchema;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One Postgres container for the whole test run (Testcontainers). Each caller gets its own schema with the module's
 * migration files applied, the way an app runs them in its own schema step.
 */
public final class Pg {

    private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:16-alpine");
    private static final AtomicInteger SCHEMAS = new AtomicInteger();

    static {
        CONTAINER.start();
        Runtime.getRuntime().addShutdownHook(new Thread(CONTAINER::stop));
    }

    private Pg() {
    }

    /** A data source on a new schema holding the notification tables. */
    public static DataSource freshSchema() {
        String schema = "app" + SCHEMAS.incrementAndGet();
        try (Connection c = admin().getConnection(); Statement s = c.createStatement()) {
            s.execute("create schema " + schema);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        PGSimpleDataSource ds = admin();
        ds.setCurrentSchema(schema);
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            for (String script : NotificationSchema.scripts()) {
                s.execute(NotificationSchema.script(script));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return ds;
    }

    /** Every row of every notif_ table in the schema, as JSON text: for "nothing secret is stored" checks. */
    public static String dumpAllTables(DataSource ds) {
        StringBuilder all = new StringBuilder();
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = s.executeQuery(
                    "select table_name from information_schema.tables where table_schema = current_schema()")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            for (String table : tables) {
                if (!table.startsWith("notif_")) {
                    throw new IllegalStateException("Table without the notif_ prefix: " + table);
                }
                try (ResultSet rs = s.executeQuery("select row_to_json(t)::text from " + table + " t")) {
                    while (rs.next()) {
                        all.append(table).append(' ').append(rs.getString(1)).append('\n');
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return all.toString();
    }

    public static List<String> tables(DataSource ds) {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("select table_name from information_schema.tables "
                     + "where table_schema = current_schema() order by 1")) {
            List<String> out = new ArrayList<>();
            while (rs.next()) {
                out.add(rs.getString(1));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PGSimpleDataSource admin() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(CONTAINER.getJdbcUrl());
        ds.setUser(CONTAINER.getUsername());
        ds.setPassword(CONTAINER.getPassword());
        return ds;
    }
}
