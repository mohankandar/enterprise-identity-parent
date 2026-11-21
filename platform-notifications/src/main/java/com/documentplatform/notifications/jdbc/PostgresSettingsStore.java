package com.documentplatform.notifications.jdbc;

import com.documentplatform.notifications.ChannelKind;
import com.documentplatform.notifications.ConditionTiming;
import com.documentplatform.notifications.DestinationId;
import com.documentplatform.notifications.EntityId;
import com.documentplatform.notifications.EventSettings;
import com.documentplatform.notifications.EventTypeId;
import com.documentplatform.notifications.Frequency;
import com.documentplatform.notifications.RoleId;
import com.documentplatform.notifications.Schedule;
import com.documentplatform.notifications.SettingsStore;
import com.documentplatform.notifications.TenantId;
import com.documentplatform.notifications.TenantSettings;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.documentplatform.notifications.jdbc.Sql.textArray;

/** Settings in Postgres (tables {@code notif_tenant}, {@code notif_destination}, {@code notif_event_settings}). */
public final class PostgresSettingsStore implements SettingsStore {

    private static final String TECHNICAL = "TECHNICAL";
    private static final String BUSINESS = "BUSINESS";

    private final DataSource ds;

    public PostgresSettingsStore(DataSource dataSource) {
        this.ds = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<TenantSettings> tenant(TenantId tenant) {
        return Sql.run(ds, "read tenant settings", c -> {
            URI base;
            ZoneId zone;
            try (PreparedStatement ps = c.prepareStatement(
                    "select app_base_url, zone_id from notif_tenant where tenant_id = ?")) {
                ps.setString(1, tenant.value());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    base = URI.create(rs.getString(1));
                    zone = ZoneId.of(rs.getString(2));
                }
            }
            Map<DestinationId, ChannelKind> destinations = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "select destination_id, channel from notif_destination where tenant_id = ?")) {
                ps.setString(1, tenant.value());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        destinations.put(DestinationId.of(rs.getString(1)), ChannelKind.valueOf(rs.getString(2)));
                    }
                }
            }
            return Optional.of(new TenantSettings(base, zone, destinations));
        });
    }

    @Override
    public void saveTenant(TenantId tenant, TenantSettings settings) {
        Sql.inTransaction(ds, "save tenant settings", c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into notif_tenant (tenant_id, app_base_url, zone_id, updated_at) values (?, ?, ?, now())
                    on conflict (tenant_id) do update
                        set app_base_url = excluded.app_base_url, zone_id = excluded.zone_id, updated_at = now()""")) {
                ps.setString(1, tenant.value());
                ps.setString(2, settings.appBaseUrl().toString());
                ps.setString(3, settings.zone().getId());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("delete from notif_destination where tenant_id = ?")) {
                ps.setString(1, tenant.value());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into notif_destination (tenant_id, destination_id, channel) values (?, ?, ?)")) {
                for (Map.Entry<DestinationId, ChannelKind> d : settings.destinations().entrySet()) {
                    ps.setString(1, tenant.value());
                    ps.setString(2, d.getKey().value());
                    ps.setString(3, d.getValue().name());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    @Override
    public Optional<EventSettings> technical(TenantId tenant, EventTypeId type) {
        return read(tenant, TECHNICAL, "", type);
    }

    @Override
    public void saveTechnical(TenantId tenant, EventTypeId type, EventSettings settings) {
        save(tenant, TECHNICAL, "", type, settings);
    }

    @Override
    public Optional<EventSettings> business(TenantId tenant, EntityId entity, EventTypeId type) {
        return read(tenant, BUSINESS, entity.value(), type);
    }

    @Override
    public void saveBusiness(TenantId tenant, EntityId entity, EventTypeId type, EventSettings settings) {
        save(tenant, BUSINESS, entity.value(), type, settings);
    }

    private Optional<EventSettings> read(TenantId tenant, String level, String entity, EventTypeId type) {
        return Sql.run(ds, "read event settings", c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    select enabled, frequency, daily_at, recipient_roles, destinations, alert_after_seconds,
                           repeat_every_seconds, thresholds
                    from notif_event_settings
                    where tenant_id = ? and level = ? and entity_id = ? and event_type = ?""")) {
                ps.setString(1, tenant.value());
                ps.setString(2, level);
                ps.setString(3, entity);
                ps.setString(4, type.value());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(settings(rs)) : Optional.<EventSettings>empty();
                }
            }
        });
    }

    private static EventSettings settings(ResultSet rs) throws SQLException {
        Frequency f = Frequency.valueOf(rs.getString("frequency"));
        LocalTime at = rs.getObject("daily_at", LocalTime.class);
        long alertAfter = rs.getLong("alert_after_seconds");
        ConditionTiming timing = rs.wasNull() ? null
                : new ConditionTiming(Duration.ofSeconds(alertAfter), Duration.ofSeconds(rs.getLong("repeat_every_seconds")));
        return new EventSettings(rs.getBoolean("enabled"), new Schedule(f, at),
                new HashSet<>(Sql.list(rs, "recipient_roles", RoleId::of)),
                new HashSet<>(Sql.list(rs, "destinations", DestinationId::of)), timing,
                Sql.readMap(rs.getString("thresholds"), Long::valueOf));
    }

    private void save(TenantId tenant, String level, String entity, EventTypeId type, EventSettings s) {
        Sql.run(ds, "save event settings", c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into notif_event_settings (tenant_id, level, entity_id, event_type, enabled, frequency,
                        daily_at, recipient_roles, destinations, alert_after_seconds, repeat_every_seconds, thresholds,
                        updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    on conflict (tenant_id, level, entity_id, event_type) do update set
                        enabled = excluded.enabled, frequency = excluded.frequency, daily_at = excluded.daily_at,
                        recipient_roles = excluded.recipient_roles, destinations = excluded.destinations,
                        alert_after_seconds = excluded.alert_after_seconds,
                        repeat_every_seconds = excluded.repeat_every_seconds, thresholds = excluded.thresholds,
                        updated_at = now()""")) {
                bind(c, ps, tenant, level, entity, type, s);
                return ps.executeUpdate();
            }
        });
    }

    private static void bind(Connection c, PreparedStatement ps, TenantId tenant, String level, String entity,
                             EventTypeId type, EventSettings s) throws SQLException {
        ps.setString(1, tenant.value());
        ps.setString(2, level);
        ps.setString(3, entity);
        ps.setString(4, type.value());
        ps.setBoolean(5, s.enabled());
        ps.setString(6, s.schedule().frequency().name());
        ps.setObject(7, s.schedule().dailyAt());
        ps.setArray(8, textArray(c, s.recipientRoles().stream().map(RoleId::value).toList()));
        ps.setArray(9, textArray(c, s.destinations().stream().map(DestinationId::value).toList()));
        ConditionTiming t = s.conditionTiming();
        ps.setObject(10, t == null ? null : t.alertAfter().toSeconds(), java.sql.Types.BIGINT);
        ps.setObject(11, t == null ? null : t.repeatEvery().toSeconds(), java.sql.Types.BIGINT);
        ps.setString(12, Sql.writeMap(s.thresholds()));
    }
}
