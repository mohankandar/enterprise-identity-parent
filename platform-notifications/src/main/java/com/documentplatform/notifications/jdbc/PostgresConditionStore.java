package com.documentplatform.notifications.jdbc;

import com.documentplatform.notifications.ConditionKey;
import com.documentplatform.notifications.ConditionState;
import com.documentplatform.notifications.ConditionStore;
import com.documentplatform.notifications.NotificationEvent;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.documentplatform.notifications.jdbc.Sql.instant;
import static com.documentplatform.notifications.jdbc.Sql.orEmpty;
import static com.documentplatform.notifications.jdbc.Sql.ts;

/** Continuous conditions in Postgres (table {@code notif_condition}). */
public final class PostgresConditionStore implements ConditionStore {

    private static final String KEY = "tenant_id = ? and event_type = ? and entity_id = ? and identifier = ?";

    private final DataSource ds;

    public PostgresConditionStore(DataSource dataSource) {
        this.ds = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public ConditionState recordFailure(ConditionKey key, NotificationEvent latest, Instant now) {
        return Sql.run(ds, "record a failing condition", c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into notif_condition (tenant_id, event_type, entity_id, identifier, since, failures, alerts,
                        last_alert_at, counts, times, link, occurred_at, updated_at)
                    values (?, ?, ?, ?, ?, 1, 0, null, ?, ?, ?, ?, ?)
                    on conflict (tenant_id, event_type, entity_id, identifier) do update set
                        failures = notif_condition.failures + 1, counts = excluded.counts, times = excluded.times,
                        link = excluded.link, occurred_at = excluded.occurred_at, updated_at = excluded.updated_at
                    returning *""")) {
                int i = bindKey(ps, 1, key);
                ps.setObject(i++, ts(now));
                i = Sql.bindEventFields(ps, i, latest);
                ps.setObject(i, ts(now));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return state(rs);
                }
            }
        });
    }

    @Override
    public List<ConditionState> active() {
        return Sql.run(ds, "read active conditions", c -> {
            try (PreparedStatement ps = c.prepareStatement("select * from notif_condition order by since");
                 ResultSet rs = ps.executeQuery()) {
                List<ConditionState> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(state(rs));
                }
                return out;
            }
        });
    }

    @Override
    public Optional<ConditionState> find(ConditionKey key) {
        return Sql.run(ds, "read a condition", c -> {
            try (PreparedStatement ps = c.prepareStatement("select * from notif_condition where " + KEY)) {
                bindKey(ps, 1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(state(rs)) : Optional.<ConditionState>empty();
                }
            }
        });
    }

    @Override
    public void markAlerted(ConditionKey key, int alerts, Instant at) {
        Sql.run(ds, "record a condition alert", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "update notif_condition set alerts = ?, last_alert_at = ?, updated_at = ? where " + KEY)) {
                ps.setInt(1, alerts);
                ps.setObject(2, ts(at));
                ps.setObject(3, ts(at));
                bindKey(ps, 4, key);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public void remove(ConditionKey key) {
        Sql.run(ds, "remove a condition", c -> {
            try (PreparedStatement ps = c.prepareStatement("delete from notif_condition where " + KEY)) {
                bindKey(ps, 1, key);
                return ps.executeUpdate();
            }
        });
    }

    private static int bindKey(PreparedStatement ps, int first, ConditionKey key) throws SQLException {
        ps.setString(first, key.tenant().value());
        ps.setString(first + 1, key.type().value());
        ps.setString(first + 2, orEmpty(key.entity()));
        ps.setString(first + 3, orEmpty(key.identifier()));
        return first + 4;
    }

    private static ConditionState state(ResultSet rs) throws SQLException {
        NotificationEvent latest = Sql.event(rs);
        int alerts = rs.getInt("alerts");
        return new ConditionState(ConditionKey.of(latest), latest, instant(rs, "since"), rs.getLong("failures"), alerts,
                alerts == 0 ? null : instant(rs, "last_alert_at"));
    }
}
