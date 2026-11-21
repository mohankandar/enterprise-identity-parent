# platform-notifications — design (locked 2026-11-20)

Source of truth for the functional rules: the owner's Shared capabilities thread (project doc `Shared-Capabilities.md`, section 7). This file is the copy the coding sessions read. Do not change the rules here without the owner.

## 1. What the module is

Shared library code that LexaVault and Vero include. Not a server. It knows nothing about either app: each app registers its own event catalogue and supplies recipients. No app names, no app-specific events in this module.

## 2. Functional rules (owner decisions)

1. **Event catalogue.** Each app registers a list of eligible event types. The customer switches on the ones they want. Events are added or removed in later releases.
2. **Recipients are roles**, not named people (e.g. "Reviewers of entity X"). Email goes to the people holding the role; Teams/Slack go to the channel webhook the admin assigned.
3. **Who configures.** The customer's IT Admin connects email, Teams and Slack once (tenant level). Technical events (system health, intake, delivery) are chosen and routed by the IT Admin. Business events (work, approvals, licence/usage, security) are chosen per entity by the Business Admin.
4. **Channels:** email (customer's mail server), Microsoft Teams and Slack by **incoming webhook link**. No bot apps.
5. **Content rule.** A message carries only: the event's name, the record's primary identifier (e.g. contract number), counts/times where relevant, and a link into the app. **Never** document content, a redacted value, a case field marked "sensitive", passwords, keys, tokens or webhook URLs. Enforced by the API shape and by tests.
6. **No personal opt-out in V1.** Only admins choose.
7. **Frequency per event:** Immediately · Every hour · Every 4 hours · Once a day (at an admin-chosen time). Anything except Immediately is a **summary** of everything since the last one.
8. **Critical events** (something is down: delivery failing, database / AI model / OCR / key service / storage down, licence expired) go **Immediately to the IT Admin** and may only be set to Immediately or Every hour.
9. **Defaults:** critical Immediately; work events every 4 hours; licence and usage once a day. Each event type carries its default on/off state, frequency, recipient roles and thresholds.
10. **Continuous problems alert once, not per failure:** an alert fires when a condition has lasted past a threshold (default 15 minutes), repeats while it continues (default every 4 hours), and a **Recovered** message follows when it clears.
11. **Thresholds are defaults the admin can adjust**, within the critical-event limit above.
12. **No generic errors:** when a channel fails, the cause is specific ("Mail server refused the login", "Teams webhook address no longer valid"), with a stable code, and nothing secret in the message or log.

## 3. Event catalogues (registered by each app, not built into this module)

Listed so the model covers them. Kind: B = business, T = technical, C = critical.

**LexaVault:** cases waiting for review (summary, B) · case past review target (B) · approval waiting (B) · intake source unreachable (T) · files rejected at intake (summary, T) · repeated processing failures (T) · backlog growing / over time target (T) · delivery to Vero failing (T, C) · delivery recovered (T) · database / AI model / OCR / key service down (T, C) · disk almost full (T) · recovered (T) · page allowance 80% / 100% (B) · licence expiring 30/7/1 days (B) · grace period (B) · licence expired (B, C) · redaction policy changed (B) · emergency admin used (B).

**Vero:** print/download request waiting (B, to Business Admins) · request approved / denied (B, to the requester) · source system unreachable or failing (T) · documents rejected (summary, T) · database / storage / key service down (T, C) · recovered (T) · licence expiring (B) · storage or seats near limit (B) · licence expired (B, C) · sensitivity level lowered (B, **off by default**).

## 4. Technical shape (built in steps)

- **P003 — core logic, no I/O:** event-type model and catalogue registration with validation (critical frequency rule, defaults); settings model (tenant-level channels; per-entity business settings; tenant-level technical settings; thresholds); the publish API (structured fields only, no free text); routing (event → enabled? → which channels → which roles); summary buckets for hourly / 4-hourly / daily; continuous-condition tracker (start, threshold, repeat, recovered); message building that obeys the content rule; a `Clock` and a `tick(now)` entry point so apps schedule it and tests control time; channel and recipient-resolver **interfaces** with in-memory fakes. Full unit tests.
- **P004 — real channels and storage:** SMTP email, Teams and Slack webhook senders with retries and specific failure reasons; an outbox so a crash or restart loses nothing and nothing is sent twice; storage behind interfaces with a Postgres implementation and SQL migration files the apps run in their own schema step; webhook URLs and mail passwords read from the secret store, never logged.
- **P005 — Spring wiring:** auto-configuration, scheduling of `tick`, configuration properties (mail server and webhook addresses come from the install config file).
- **Later, in each app:** the admin settings screens, the app's catalogue registration and the recipient resolver (role → people), when that app switches to platform-core.
