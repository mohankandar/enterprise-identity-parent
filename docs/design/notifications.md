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

## 5. Decisions

### Owner answers to the P003 questions (2026-11-20)

- **O1. Licensing is per entity.** Licence and page-allowance events are published once per entity. There are no
  tenant-level business settings: every business event carries an entity.
- **O2. Kept as built:** critical events are always on; the condition timing limits of D3 stand; one summary per
  entity. Entity separation is a legal requirement: a message never mixes entities.
- **O3. Fakes out of production code.** The in-memory fakes live in the test-support module
  `platform-notifications-testkit` (in the build and the BOM). The main jar does not contain them.
- **O4. Record identifiers may contain single spaces** (for example `LN 12345`), with no leading, trailing or double
  spaces. This replaces the "no spaces" part of D9.

### Decisions made in P003, as confirmed or changed by the answers above

1. **Critical events cannot be switched off.** Their recipient roles always include the IT Admin role, which the app
   names when it registers its catalogue. Confirmed (O2).
2. **The IT Admin role is looked up at tenant level**, even for a per-entity event such as "licence expired".
3. **Condition timing limits:** threshold 0–24 h, at most 1 h for critical events; repeat 15 min–24 h. Confirmed (O2).
4. **Recovered** uses the condition's own event type and settings. It is sent only if an alert went out; a condition
   that clears before its threshold sends nothing.
5. **When the admin chose no destination**, events go to the tenant's e-mail destination. Teams and Slack receive
   only the events assigned to them, without role-based recipients.
6. **One summary per destination, schedule, entity and set of recipient roles.** Up to 10 items per event type, then
   "… and N more", with totals for counts. Confirmed (O2: a message never mixes entities).
7. **Saved settings that break a rule** after a catalogue change fall back to the event type's defaults.
8. **Business events must carry an entity**; technical events may carry one but are set per tenant. Confirmed and
   extended (O1): there are no tenant-level business settings.
9. **Identifier and link shapes.** Identifier: at most 64 characters of `A–Z a–z 0–9 . _ : / # -`, with single spaces
   inside (O4), no `//`. Link: a path only, no query string or fragment; the host comes from the tenant's app
   address. The real protection is that the publish API has no free-text field.
10. **"Immediately" is sent inside `publish`.** Since P004 every message goes through the outbox first and temporary
    failures are retried; the attempt is still made inside `publish`.
11. **Own `TenantId` and `EntityId` types**, so the module does not depend on identity-core.
12. **Numeric thresholds** are declared per event type with a default and a range, adjustable by the admin within the
    range, and read by the app with `NotificationSettings.threshold(...)`. The module does not compare them itself.

Decisions made in P004 (channels, outbox, storage) are listed in `docs/work/P004-report.md` and await the owner's
review before they are copied here.
