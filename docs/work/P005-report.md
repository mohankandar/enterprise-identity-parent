# P005 report — notifications: Spring wiring and install rules

Branch `feature/notifications-core`. Not merged.

## A. Owner answers applied

| # | Answer | What changed |
|---|---|---|
| 1 | At-least-once window accepted | Recorded. No code change. |
| 3 | `publish` only saves; a background worker sends | `publish`, `conditionFailing` and `conditionCleared` now only write to the outbox and return `PENDING`. New `Notifier.sendDue(now)` sends due messages without holding the notifier's lock. New `queueDue(now)` raises alerts and queues summaries without sending. `Builder.whenQueued(Runnable)` wakes the sender. `tick(now)` still does both, for plain-Java callers. |
| 4 | Retry defaults accepted, as config | `platform.notifications.retry.*`, `lease`, `retention`, `sender.batch-size`. |
| 5 | No authenticating proxy | Recorded; the install guide says so. |

The answers, the database rule (B) and P004 decisions 1–13 are now in section 5 of `docs/design/notifications.md`.
P003 decision 10 is rewritten to match answer 3. P004 question 2 (OAuth for mail) was not answered and is still open
(question 1 below).

## What was built

### Database rule (B), in `platform-notifications`

- `V1__notifications.sql` now also creates `notif_schema_version`, with one row: version 1.
- `NotificationSchema.verify(DataSource)` only reads. It checks that the six tables and the version table are there at
  `VERSION` = 1, and that the connection's user holds SELECT, INSERT, UPDATE and DELETE on each. Otherwise it throws
  `NotificationSchemaException` with a stable `Problem` code and a specific message:
  - `TABLES_MISSING`: "Notifications tables missing (expected version 1): none of the notif_ tables exist in the
    database schema the app connects to; run the schema step (db/platform/notifications) before starting the app."
  - `VERSION_OLDER`: "Notifications tables at version 0, expected version 1: run the schema step …"
  - `VERSION_NEWER`, `VERSION_UNKNOWN`, `TABLES_INCOMPLETE` (names the missing tables), `PRIVILEGES_MISSING` (names the
    right and the table), `CHECK_FAILED` (SQL state only).
- Nothing at startup creates or changes a table. A test runs startup as the schema owner on an empty schema and
  checks that it is still empty afterwards.
- `notif_outbox` gets a `seq` identity column, so messages due at the same moment are sent first in, first out.
  Before this, the order was undefined.

### `platform-notifications-spring` (new; in the root `<modules>` and in `platform-bom`)

Same layout as `platform-identity-spring`: package `com.documentplatform.notifications.spring`, version from the
parent.

| Type | What it does |
|---|---|
| `NotificationsAutoConfiguration` | Builds the `Notifier` from the app's `EventCatalogue`, `RecipientResolver`, `SecretResolver` and `DataSource`. It also creates the four Postgres stores, `NotificationSettings`, the channels, the workers and the health indicator. Each bean backs off if the app defines its own. Registered in `META-INF/spring/…AutoConfiguration.imports`. `platform.notifications.enabled=false` switches it off. |
| `NotificationsProperties` | Prefix `platform.notifications`: `mail.*` (host, port, security, username, password-secret, from-address, from-name, timeouts), `teams.<id>` and `slack.<id>` (id → secret reference), `webhook.*` timeouts, `retry.*`, `lease`, `retention`, `tick-interval`, `sender.poll-interval`, `sender.batch-size`. |
| `NotificationsConfig` | Checks the properties at startup and builds the module's own types. It reports every wrong value at once, each with its full key and what to do. A value that might be a secret is never repeated; if it looks like a pasted address, the message says so. |
| `NotificationWorkers` | Two daemon threads per app instance. The tick thread runs every `tick-interval` (default 1 minute); it only queues, so it stays on time while a channel is slow. The sender thread is woken by every publish and also polls every `sender.poll-interval` (default 10 s); it sends batch after batch while messages are due. Failures are logged once per run of failures, with neutral text. |
| `NotificationChannels`, `ObservedChannel` | E-mail when `mail.host` is set, Teams and Slack when they have destinations. Each send outcome is recorded for health. |
| `DestinationHealth`, `NotificationsHealthIndicator` | `/actuator/health/notifications`: each destination with channel, state (Working / Having problems / Down / Not used yet), code, fixed reason, failing since and last attempt. Actuator is an optional dependency. |
| `NotificationsFailureAnalyzer` | Turns a config or table problem into Spring Boot's "APPLICATION FAILED TO START" report, with what to do. |

### Install guide

`docs/install/notifications.md` covers the customer-IT steps for e-mail, Teams and Slack (from the P004 report),
the database section rewritten for rule B, the startup messages and what to do about each, every config key with its
default, an example, and the health states.

## Tests

`mvn verify` and `mvn install`: **283 tests, all pass** (251 before). All seven artifacts are in
`~/.m2/repository/com/documentplatform/platform/`.

- Identity: 88, unchanged.
- `platform-notifications`: 65. `platform-notifications-testkit`: 101 (98 + 3 new).
- `platform-notifications-spring`: 29, new.
- The new module's suite also passes against Spring Boot 3.5.16 (`-Dspring.boot.version=3.5.16`).

**Existing tests and answer 3.** Tests that expected `publish` to send now run the sender once straight after
publishing, through a helper (`Fixture.via(n)`, `Stack.publish(n, …)`). Their assertions are unchanged, with three
exceptions:

- The two crash-while-sending tests now crash in `sendDue`, because `publish` no longer sends.
- One condition test shows a stronger result: the alert is saved before the crash and sent once after the restart.
- The table list in `PostgresStoresTest` includes `notif_schema_version`.

| New test class | Tests | Covers |
|---|---|---|
| `PublishOnlyQueuesTest` (testkit) | 3 | `publish` never calls a channel and wakes the sender. `queueDue` raises alerts without sending. `publish` and `conditionFailing` go on while `sendDue` is stuck inside a channel. |
| `WiringTest` | 8 | The Notifier is built from the app's beans with the Postgres stores. An Immediately event is sent by the background sender. The scheduled tick raises a condition alert with nobody calling `tick`. Two app instances on one database send 20 messages once each. Switched off. The app's own stores replace the Postgres ones (no DataSource needed). A missing app bean is named. |
| `PropertyValidationTest` | 10 | Defaults, and port defaults by security. Each mail mistake. A webhook address or password put where its reference goes: refused and never repeated, also in the startup report. Duplicate ids. Retry, lease, interval, retention and batch-size limits. All problems listed at once. Ids with dots and capitals. Startup refused. |
| `StartupSchemaCheckTest` | 8 | Startup refused for missing tables (and nothing created), older and newer versions, no version table, incomplete tables, and a user without DELETE. **A user with only SELECT/INSERT/UPDATE/DELETE runs everything**: publish, the sender, a summary item (identity column), a condition, the alert, the summary and the purge. The startup report says what to do. |
| `SlowChannelTest` | 1 | A mail server that accepts the connection and never answers. Each `publish`, `conditionFailing` and `queueDue` returns in under 500 ms while the sender waits. Health then shows "Having problems / MAIL_TIMEOUT". |
| `HealthTest` | 2 | Teams and Slack start as Not used yet. After a send: mail Working, a retired Teams connector Down (`WEBHOOK_CONNECTOR_RETIRED`), an http Slack address Down (`WEBHOOK_ADDRESS_NOT_SECURE`), component `DEGRADED`. A changed password makes mail Down (`MAIL_LOGIN_REFUSED`). No secret, address, host, recipient or secret path appears in the health output. The state rules over time, including Down after 15 minutes of temporary failures. |

## How an app turns it on

1. Add the dependency. The version comes from `platform-bom`.

   ```xml
   <dependency>
       <groupId>com.documentplatform.platform</groupId>
       <artifactId>platform-notifications-spring</artifactId>
   </dependency>
   ```

2. Define three beans. The app's `DataSource` is already there.

   ```java
   @Configuration
   class NotificationsSetup {
       @Bean EventCatalogue notificationCatalogue() {
           return EventCatalogue.builder(RoleId.of("it-admin"))
                   .register(EventType.builder("work.approval-waiting", "Approval waiting", EventKind.BUSINESS)
                           .schedule(Schedule.immediately()).recipients(RoleId.of("approver")).build())
                   .build();
       }
       @Bean RecipientResolver notificationRecipients(UserDirectory users) {   // role → people, the app's own lookup
           return (tenant, entity, role) -> users.holders(tenant, entity, role);
       }
       @Bean SecretResolver notificationSecrets(SecretStore store) {           // the app's secret store
           return ref -> store.read(ref.value()).map(Secret::of);
       }
   }
   ```

3. Add the schema step. With Flyway, run the location `classpath:db/platform/notifications` in the app's schema
   step, not at app startup (see question 4).
4. Configure (for example `application.yml`):

   ```yaml
   platform:
     notifications:
       mail:
         host: smtp.customer.example
         username: notifications@customer.example
         password-secret: notifications/mail-password
         from-address: notifications@customer.example
       teams:
         ops-teams: notifications/teams-ops
   ```

5. Use the beans: `Notifier.publish(...)` and `conditionFailing / conditionCleared(...)` from the app's code, and
   `NotificationSettings` from the admin screens. Scheduling, sending and health need nothing more.

## Decisions I had to make

1. **The core API changed for answer 3.** Added: `sendDue`, `queueDue`, `batchSize()` and `Builder.whenQueued`.
   `publish` now reports `PENDING` where it used to report `SENT`. `tick` keeps its old meaning (queue, then send),
   so plain-Java callers and the existing tests still work.
2. **I edited V1 instead of adding V2**, for the version table and the outbox `seq` column. V1 was never released: the
   branch is not merged and no app runs it. Once a release ships, its files are never edited again.
3. **The version comes from `notif_schema_version`**, which the SQL files keep up to date. It does not come from
   Flyway's history: each app uses its own tool and history table.
4. **Startup also checks the user's rights**, with `has_table_privilege`, so a missing grant is named at startup and
   not at the first send. The DML-only test confirms that no sequence rights are needed.
5. **One mail server serves every e-mail destination.** The task lists one mail server. E-mail destination ids live in
   the tenant settings (database), not in the config.
6. **Teams and Slack ids are config map keys**, unique across both channels. Ports default from the security
   setting. Security defaults to STARTTLS.
7. **Extra limits:** the lease must be longer than the longest send (connect + read timeout); otherwise a slow send
   looks like a stopped one and is sent twice. `tick-interval` and `sender.poll-interval` must be 1 s – 5 min.
   `sender.batch-size` must be 1 – 1000.
8. **Validation is my own code, not Bean Validation**, to give one specific message per key and never echo a
   possible secret.
9. **The module runs its own daemon threads** and does not use `@EnableScheduling`, so it imposes nothing on the
   app's scheduler. One sender thread per instance.
10. **Several instances need no cluster lock.** Claims use `SKIP LOCKED`, and alerts and summaries raised twice get
    the same key. A test covers two instances on one database.
11. **Shutdown waits up to 10 s for a send in progress.** A send cut off there is sent again after its lease. This is
    the same accepted window as A1.
12. **Health is per app instance and in memory**, from the last outcome per destination.
    - A fourth state, **Not used yet**, covers the time before the first send.
    - Temporary failures count as **Down** after 15 minutes without a success.
    - Refusals that concern one message (recipient refused, message rejected, no recipients) count as **Having
      problems**, because the destination itself answers.
    - Configured Teams and Slack destinations are listed from the start. E-mail destinations appear once used.
13. **The component status is `DEGRADED`, not `DOWN`**, when a destination has a problem. A broken webhook must not
    get the app restarted or taken out of a load balancer. Spring leaves custom statuses out of the overall status and
    returns HTTP 200 for them.
14. **Missing app beans give a specific startup message.** `SecretResolver` is required only when a channel is
    configured. The table check runs only when the module's Postgres stores are used.
15. **Built against Spring Boot 3.3.2**, the lowest line the products run. The full suite also passes on 3.5.16.
    `spring-boot-autoconfigure` is compile scope and `spring-boot-actuator` is optional. The app's own Boot version
    wins.
16. **New dependencies, all Apache-2.0:** `spring-boot-autoconfigure` (compile), `spring-boot-actuator` (optional),
    and `spring-boot-test` and `assertj-core` (test). AssertJ's `byte-buddy` is excluded: these tests don't need it,
    and Maven Central could not be reached from this machine (TLS interception), so the build runs offline.

## Not covered

- A successful SMTP send over TLS (unchanged from P004: there is no trusted test certificate).
- Health across instances: each instance reports what it sent.

## Questions

1. P004 question 2 is still open: should OAuth 2.0 (XOAUTH2) for Microsoft 365 and Google mail be added?
   **Recommendation:** yes, as its own task. The install guide points to a relay connector until then.
2. Are "Not used yet" as a fourth health state, and `DEGRADED` instead of `DOWN`, acceptable?
   **Recommendation:** keep both. Reporting "Working" before any send would be untrue, and `DOWN` would take the
   whole app out.
3. Should health later be cluster-wide, read from the outbox, instead of per instance? **Recommendation:** not now.
   Per instance is accurate for what that instance sent; revisit if a customer's monitoring needs one view.
4. When an app uses Flyway, the platform files must not share the app's history table, because both start at V1.
   **Recommendation:** each app runs `db/platform/notifications` as a separate Flyway run in its schema step, with
   its own history table named `notif_schema_history`. Write this into each app's switch-over task.
5. Is one mail server for all e-mail destinations enough for V1? **Recommendation:** yes. Per-destination servers can
   be added later without breaking the config (`mail.*` would stay the default).
6. Should the P005 decisions above be copied into section 5 of the design doc? **Recommendation:** yes, once you have
   reviewed them.
