package com.documentplatform.notifications.jdbc;

import com.documentplatform.notifications.DestinationId;
import com.documentplatform.notifications.EntityId;
import com.documentplatform.notifications.Frequency;
import com.documentplatform.notifications.MessageKind;
import com.documentplatform.notifications.NotificationEvent;
import com.documentplatform.notifications.RoleId;
import com.documentplatform.notifications.Schedule;
import com.documentplatform.notifications.SummaryBucket;
import com.documentplatform.notifications.SummaryItem;
import com.documentplatform.notifications.SummaryStore;
import com.documentplatform.notifications.TenantId;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static com.documentplatform.notifications.jdbc.Sql.instant;
import static com.documentplatform.notifications.jdbc.Sql.textArray;
import static com.documentplatform.notifications.jdbc.Sql.ts;

/** Summary buckets in Postgres (table {@code notif_summary_item}). */
public final class PostgresSummaryStore implements SummaryStore {

    private final DataSource ds;

    public PostgresSummaryStore(DataSource dataSource) {
        this.ds = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void add(SummaryItem item) {
        Sql.run(ds, "save a summary item", c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into notif_summary_item (item_key, tenant_id, destination_id, frequency, daily_at,
                        entity_id, recipient_roles, due_at, event_type, message_kind, identifier, counts, times, link,
                        occurred_at, raised_at, since, failures)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (item_key) do nothing""")) {
                SummaryBucket b = item.bucket();
                NotificationEvent e = item.event();
                ps.setString(1, item.key());
                ps.setString(2, b.tenant().value());
                ps.setString(3, b.destination().value());
                ps.setString(4, b.schedule().frequency().name());
                ps.setObject(5, b.schedule().dailyAt());
                ps.setString(6, b.entity() == null ? null : b.entity().value());
                ps.setArray(7, textArray(c, b.roles().stream().map(RoleId::value).toList()));
                ps.setObject(8, ts(b.dueAt()));
                ps.setString(9, e.type().value());
                ps.setString(10, item.kind().name());
                ps.setString(11, e.identifier() == null ? null : e.identifier().value());
                int i = Sql.bindEventFields(ps, 12, e);
                ps.setObject(i++, ts(item.at()));
                ps.setObject(i++, ts(item.since()));
                ps.setLong(i, item.failures());
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public List<SummaryItem> due(Instant now) {
        return Sql.run(ds, "read due summary items", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "select * from notif_summary_item where due_at <= ? order by due_at, seq")) {
                ps.setObject(1, ts(now));
                List<SummaryItem> items = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(item(rs));
                    }
                }
                return items;
            }
        });
    }

    @Override
    public void remove(Collection<String> itemKeys) {
        if (itemKeys.isEmpty()) {
            return;
        }
        Sql.run(ds, "remove summary items", c -> {
            try (PreparedStatement ps = c.prepareStatement("delete from notif_summary_item where item_key = any (?)")) {
                ps.setArray(1, c.createArrayOf("text", itemKeys.toArray(String[]::new)));
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public int pending() {
        return Sql.run(ds, "count summary items", c -> {
            try (PreparedStatement ps = c.prepareStatement("select count(*) from notif_summary_item");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        });
    }

    private static SummaryItem item(ResultSet rs) throws SQLException {
        String entity = rs.getString("entity_id");
        SummaryBucket bucket = new SummaryBucket(TenantId.of(rs.getString("tenant_id")),
                DestinationId.of(rs.getString("destination_id")),
                new Schedule(Frequency.valueOf(rs.getString("frequency")), rs.getObject("daily_at", LocalTime.class)),
                entity == null ? null : EntityId.of(entity), new HashSet<>(Sql.list(rs, "recipient_roles", RoleId::of)),
                instant(rs, "due_at"));
        return new SummaryItem(rs.getString("item_key"), bucket, Sql.event(rs),
                MessageKind.valueOf(rs.getString("message_kind")), instant(rs, "raised_at"), instant(rs, "since"),
                rs.getLong("failures"));
    }
}
