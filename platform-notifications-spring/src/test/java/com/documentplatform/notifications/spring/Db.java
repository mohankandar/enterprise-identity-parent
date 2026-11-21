package com.documentplatform.notifications.spring;

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
 * One Postgres container for the test run. Each test gets its own schema. {@link #migrated()} runs the SQL files the
 * way the app's schema step does, as the schema owner; {@link #appUser} gives the runtime user the app connects with.
 */
final class Db {

    private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:16-alpine");
    private static final AtomicInteger N = new AtomicInteger();

    static {
        CONTAINER.start();
        Runtime.getRuntime().addShutdownHook(new Thread(CONTAINER::stop));
    }

    final String schema = "s" + N.incrementAndGet();
    /** The schema owner: what the customer's schema step runs as. */
    final PGSimpleDataSource owner;

    private Db() {
        exec(admin(null), "create schema " + schema);
        owner = admin(schema);
    }

    /** A schema with nothing in it: the schema step was never run. */
    static Db empty() {
        return new Db();
    }

    /** A schema after the schema step. */
    static Db migrated() {
        Db db = new Db();
        for (String script : NotificationSchema.scripts()) {
            db.exec(NotificationSchema.script(script));
        }
        return db;
    }

    void exec(String... sql) {
        exec(owner, sql);
    }

    /**
     * A login that holds only the given rights on the notif_ tables (and SELECT on the version table), as the install
     * guide tells the customer's DBA to grant.
     */
    DataSource appUser(String rights) {
        String user = schema + "_app";
        exec(admin(null), "create role " + user + " login password 'pw'",
                "grant usage on schema " + schema + " to " + user);
        List<String> grants = new ArrayList<>();
        for (String table : NotificationSchema.TABLES) {
            grants.add("grant " + rights + " on " + schema + "." + table + " to " + user);
        }
        grants.add("grant select on " + schema + "." + NotificationSchema.VERSION_TABLE + " to " + user);
        exec(admin(null), grants.toArray(String[]::new));
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(CONTAINER.getJdbcUrl());
        ds.setUser(user);
        ds.setPassword("pw");
        ds.setCurrentSchema(schema);
        return ds;
    }

    List<String> tables() {
        try (Connection c = owner.getConnection(); Statement s = c.createStatement();
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

    long count(String sql) {
        try (Connection c = owner.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void exec(DataSource ds, String... sql) {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            for (String q : sql) {
                s.execute(q);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PGSimpleDataSource admin(String schema) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(CONTAINER.getJdbcUrl());
        ds.setUser(CONTAINER.getUsername());
        ds.setPassword(CONTAINER.getPassword());
        if (schema != null) {
            ds.setCurrentSchema(schema);
        }
        return ds;
    }
}
