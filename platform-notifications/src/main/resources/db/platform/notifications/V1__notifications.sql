-- platform-notifications schema, version 1.
--
-- Each app runs these files in its own schema step (for example Flyway with the location
-- classpath:db/platform/notifications and its own history table). Table names are unqualified: they are created in
-- the schema the app's connection uses.
--
-- What is stored: ids, the admins' settings, and the text of outgoing messages, which obeys the content rule (event
-- name, record identifier, counts, times, link into the app). What is never stored: document content, redacted values,
-- e-mail addresses (recipients are kept as roles and looked up at send time) and secrets (mail passwords and webhook
-- addresses stay in the secret store; destinations are referenced by id).

-- Channels the IT Admin connected for a tenant (TenantSettings).
create table notif_tenant (
    tenant_id    text        primary key,
    app_base_url text        not null,
    zone_id      text        not null,
    updated_at   timestamptz not null default now()
);

create table notif_destination (
    tenant_id      text not null references notif_tenant (tenant_id) on delete cascade,
    destination_id text not null,
    channel        text not null check (channel in ('EMAIL', 'TEAMS', 'SLACK')),
    primary key (tenant_id, destination_id)
);

-- Admins' choices per event type: technical events per tenant (entity_id = ''), business events per entity.
create table notif_event_settings (
    tenant_id            text        not null,
    level                text        not null check (level in ('TECHNICAL', 'BUSINESS')),
    entity_id            text        not null,
    event_type           text        not null,
    enabled              boolean     not null,
    frequency            text        not null
        check (frequency in ('IMMEDIATELY', 'EVERY_HOUR', 'EVERY_4_HOURS', 'ONCE_A_DAY')),
    daily_at             time,
    recipient_roles      text[]      not null,
    destinations         text[]      not null,
    alert_after_seconds  bigint,
    repeat_every_seconds bigint,
    thresholds           text        not null, -- name=value,name=value
    updated_at           timestamptz not null default now(),
    primary key (tenant_id, level, entity_id, event_type),
    check ((level = 'TECHNICAL') = (entity_id = '')),
    check ((frequency = 'ONCE_A_DAY') = (daily_at is not null)),
    check ((alert_after_seconds is null) = (repeat_every_seconds is null))
);

-- Outbox: every outgoing message, saved before it is sent. message_key is the idempotency key.
create table notif_outbox (
    message_key     text        primary key,
    tenant_id       text        not null,
    destination_id  text        not null,
    channel         text        not null check (channel in ('EMAIL', 'TEAMS', 'SLACK')),
    entity_id       text,
    recipient_roles text[]      not null,
    event_types     text[]      not null,
    message_kind    text        not null,
    subject         text        not null,
    body_lines      text[]      not null,
    link            text,
    state           text        not null check (state in ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    attempts        integer     not null default 0,
    created_at      timestamptz not null,
    next_attempt_at timestamptz not null,
    claimed_until   timestamptz,
    last_failure    text,
    finished_at     timestamptz
);

create index notif_outbox_due_idx on notif_outbox (next_attempt_at) where state in ('PENDING', 'SENDING');
create index notif_outbox_finished_idx on notif_outbox (finished_at) where state in ('SENT', 'FAILED');

-- Occurrences waiting for their summary. A bucket is (tenant, destination, schedule, entity, roles, due_at).
create table notif_summary_item (
    item_key        text        primary key,
    seq             bigint      generated always as identity,
    tenant_id       text        not null,
    destination_id  text        not null,
    frequency       text        not null,
    daily_at        time,
    entity_id       text,
    recipient_roles text[]      not null,
    due_at          timestamptz not null,
    event_type      text        not null,
    message_kind    text        not null,
    identifier      text,
    counts          text        not null, -- name=value,name=value
    times           text        not null, -- name=instant,name=instant
    link            text,
    occurred_at     timestamptz,
    raised_at       timestamptz not null,
    since           timestamptz,
    failures        bigint      not null
);

create index notif_summary_item_due_idx on notif_summary_item (due_at, seq);

-- Continuous conditions that are going on. entity_id / identifier are '' when the condition has none.
create table notif_condition (
    tenant_id     text        not null,
    event_type    text        not null,
    entity_id     text        not null,
    identifier    text        not null,
    since         timestamptz not null,
    failures      bigint      not null,
    alerts        integer     not null default 0,
    last_alert_at timestamptz,
    counts        text        not null,
    times         text        not null,
    link          text,
    occurred_at   timestamptz,
    updated_at    timestamptz not null,
    primary key (tenant_id, event_type, entity_id, identifier)
);
