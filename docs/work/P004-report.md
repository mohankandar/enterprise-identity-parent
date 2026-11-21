# P004 report — notifications: real channels, retries, storage

Branch `feature/notifications-core`. Not merged.

## A. Owner answers applied

| # | Answer | What changed |
|---|---|---|
| 1 | Licensing is per entity | No code change: business events already need an entity. No tenant-level business settings were added. |
| 2 | Critical always on, timing limits, one summary per entity | Kept as built. |
| 3 | Fakes out of the main jar | New module `platform-notifications-testkit`, added to the root `<modules>` and to `platform-bom`. The fakes keep their package `com.documentplatform.notifications.inmemory`. The main jar contains no in-memory class (checked in the built jar). |
| 4 | Identifiers may contain single spaces | `RecordIdentifier` now accepts `LN 12345` and refuses leading, trailing and double spaces and tabs. Tests are updated. |

The answers and P003 decisions 1–12 are now section **5. Decisions** of `docs/design/notifications.md`, updated where the answers changed them (decisions 8, 9 and 10).

## What was built

All new code is in `platform-notifications`. It is plain Java with no Spring.

| Package | Main types | What it does |
|---|---|---|
| `…notifications` (core) | `OutboxStore`, `OutboxEntry`, `SummaryStore`, `ConditionStore`, `RetryPolicy`, `Notifier.Builder` | Every outgoing message is saved in the outbox before it is sent. Summary buckets and condition state are stored too. Temporary failures are retried with backoff. Permanent failures stop at once and are reported. |
| `…notifications.secret` | `SecretResolver`, `SecretRef`, `Secret` | Config holds only a reference. The secret is read at send time. `Secret.toString()` never shows it. A pasted web address is refused as a reference. |
| `…notifications.email` | `EmailChannel`, `MailServer`, `MailSecurity`, `MailServerDirectory` | Sends SMTP through the customer's mail server (Jakarta Mail / Eclipse Angus). Security is STARTTLS (required), TLS or none, and server names are always checked. Messages are plain text. The `Message-ID` is made from the idempotency key, and an `Auto-Submitted` header is added. |
| `…notifications.teams` | `TeamsChannel` | Posts to a Teams Workflows webhook: a message carrying one Adaptive Card (v1.4). Retired Office 365 connector addresses are refused before anything is sent. |
| `…notifications.slack` | `SlackChannel` | Posts to a Slack incoming webhook as Block Kit: a header, plain-text sections (never mrkdwn) and a button. It keeps within Slack's size limits. |
| `…notifications.webhook` | `WebhookClient`, `WebhookDirectory`, `WebhookOptions`, `Json` | Shared HTTP code (JDK `HttpClient`): turns each HTTP status into a code, reads `Retry-After`, accepts https only, does not follow redirects, and uses the JVM proxy settings. |
| `…notifications.jdbc` | `PostgresSettingsStore`, `PostgresOutboxStore`, `PostgresSummaryStore`, `PostgresConditionStore`, `NotificationSchema` | Plain JDBC on a `DataSource`. Workers claim messages with `FOR UPDATE SKIP LOCKED`, so several app instances can share the outbox safely. |
| resources | `db/platform/notifications/V1__notifications.sql` | Creates six tables, all prefixed `notif_`: tenant, destination, event settings, outbox, summary items, conditions. The SQL is plain, so it works with Flyway or run by hand. |

`platform-notifications-testkit` holds in-memory versions of everything, for the apps' tests: the channel, recipient resolver, settings store, outbox, summary and condition stores, and the secret resolver.

**How a message travels.** `publish` routes the event as before. For each destination it either adds an item to a stored summary bucket, or saves an outbox entry and tries to send it at once. `tick` raises due condition alerts, sends due summaries, retries due messages (including any that a stopped process left behind) and deletes finished messages after 30 days.

**Why nothing is sent twice.** Every message key is derived from what the message is:

- an event and its destination;
- the n-th alert of a condition that started at a given time;
- one summary bucket and its due time.

After a crash, the notifier re-creates the same key, and the database keeps the message that is already saved instead of adding a second.

**Changes to the core API.** No app uses this module yet.

- `Notifier` is now built with `Notifier.builder(...)`, and the three stores are required.
- `DeliveryStatus` has two new values, `RETRY_SCHEDULED` and `PENDING`.
- `OutgoingMessage` has a new `idempotencyKey` field.
- `SendResult` has a new `retryAfter` field.
- `FailureCode` has 17 new codes and a `retryable()` flag.

## Failure codes

| Channel | Code | Retried? |
|---|---|---|
| Mail | `MAIL_SERVER_UNREACHABLE`, `MAIL_TIMEOUT`, `MAIL_TEMPORARILY_REFUSED` (4xx) | yes |
| Mail | `MAIL_LOGIN_REFUSED`, `MAIL_TLS_FAILED`, `MAIL_RECIPIENT_REFUSED`, `MAIL_MESSAGE_REJECTED` (5xx) | no |
| Webhook | `WEBHOOK_RATE_LIMITED` (429, honours `Retry-After`), `WEBHOOK_TIMEOUT` (timeout, 408, 504), `WEBHOOK_UNREACHABLE`, `WEBHOOK_SERVER_ERROR` (5xx) | yes |
| Webhook | `WEBHOOK_ADDRESS_INVALID` (404/410, bad address), `WEBHOOK_REJECTED` (401/403), `WEBHOOK_PAYLOAD_REJECTED` (400/413/415/422), `WEBHOOK_UNEXPECTED_REPLY` (3xx and others), `WEBHOOK_CONNECTOR_RETIRED`, `WEBHOOK_ADDRESS_NOT_SECURE` (http) | no |
| Any | `SECRET_UNAVAILABLE`, `RECIPIENTS_UNAVAILABLE`, `SENDER_FAILED` | yes |
| Any | `SECRET_NOT_FOUND`, `DESTINATION_NOT_CONFIGURED`, `DESTINATION_NOT_CONNECTED`, `CHANNEL_NOT_AVAILABLE`, `NO_RECIPIENTS` | no |

## Tests

`mvn verify`: **251 tests, all pass.** `mvn install` succeeded, and all six artifacts are in `~/.m2/repository/com/documentplatform/platform/`.

- Identity: 88, unchanged (73 core + 15 spring).
- The 85 tests from P003 all pass. They moved to the testkit module, because they use the fakes and Maven does not allow the main module's tests to depend on the testkit. Their assertions are unchanged except for the identifier cases. Four identifier cases were added, so they now count 89.
- New: 78.

| Test class (module) | Tests | Covers |
|---|---|---|
| `EmailChannelTest` (main, GreenMail + scripted SMTP) | 19 | Delivery with login, Message-ID and headers. Relay without login. Each mail failure code: unreachable, timeout, STARTTLS missing, TLS to a plain server, untrusted certificate, login refused (real and scripted), recipient refused (permanent, partial, temporary), message rejected (permanent, temporary), sender refused, 421 greeting. Secret missing, secret store down, no server configured. |
| `WebhookChannelsTest` (main, local HTTP server) | 24 | Exact Teams and Slack payloads. Each HTTP status mapped, on both channels. Retry-After in seconds and as a date. Timeout, unreachable. Retired connectors refused with no request sent. http refused. Bad addresses and secret problems. The token never in the result or the payload. Slack limits. JSON escaping. |
| `PostgresStoresTest` (main, Testcontainers) | 8 | Migration creates only `notif_` tables. Settings round trips. Each outbox key kept once. Lease, retry, sent, failed, purge. 4 concurrent workers × 200 messages, each claimed exactly once. Summary items and conditions. |
| `ReliabilityTest` (main, all real) | 10 | Crash after save and before send: sent once after restart. Crash after the summary was saved and before its bucket was emptied: one summary. Crash after an alert went out and before it was recorded: one alert, then the repeat and Recovered once. State survives a restart. Retries over HTTP. Permanent failure and refused login stop at once. Retry-After honoured. Two instances on one database: each message once. |
| `EndToEndContentRuleTest` (main, all real) | 2 | Every string in the e-mail, Teams and Slack payloads is either a fixed format word or part of the message. Summary lines match the allowed shapes. No secret, destination id, address or user id appears in any of them. |
| `SecretsNeverLeakTest` (main) | 2 | All java.util.logging output is captured at FINEST, Jakarta Mail's own logging included, across successes, refused logins, 404, 429, 401, a retired connector, a bad address and retries. No password or token appears in the logs, reports, message bodies or any table. Store errors name only the operation. |
| `OutboxRetryTest` (testkit) | 9 | Saved before sending. Backoff of 1, then 2 minutes. Permanent failures stop. Retry-After replaces the backoff. Giving up after the limit. Recipients looked up again on each attempt. A lapsed lease is taken over. Retention. Backoff maths. |
| `ContentRuleTest` (testkit) | 30 (was 26) | Identifiers with single spaces accepted. Double, leading and trailing spaces, tabs and 64+ characters refused. |

`SecretsNeverLeakTest` found one real leak while I wrote it: the test's own HTTP server (the JDK server standing in for Teams) logs the request line it receives. That is the receiving side, not our sender, so the test excludes that one logger.

## New dependencies and their licences

| Dependency | Scope | Licence |
|---|---|---|
| `org.eclipse.angus:angus-mail` 2.0.5 | compile (ships in the app) | EPL-2.0 or GPL-2.0 with Classpath Exception or EDL-1.0 (BSD-3-Clause) |
| ↳ `jakarta.mail:jakarta.mail-api` 2.1.5 | compile, transitive | EPL-2.0 or GPL-2.0 with Classpath Exception or EDL-1.0 |
| ↳ `jakarta.activation:jakarta.activation-api` 2.1.4 | compile, transitive | EDL-1.0 (BSD-3-Clause) |
| ↳ `org.eclipse.angus:angus-activation` 2.0.3 | runtime, transitive | EPL-2.0 or GPL-2.0 with Classpath Exception or EDL-1.0 |
| `com.icegreen:greenmail` 2.1.15 | test | Apache-2.0 (brings JUnit 4: EPL-1.0; slf4j-api: MIT) |
| `org.testcontainers:testcontainers-postgresql` 2.0.5 | test | MIT (brings docker-java: Apache-2.0; JNA: Apache-2.0 / LGPL-2.1) |
| `org.postgresql:postgresql` 42.7.14 | test | BSD-2-Clause |

These are the same Jakarta Mail and Angus lines that Spring Boot 3 manages. The JDBC driver is not a runtime dependency, because each app brings its own. For the webhook tests I used the JDK's built-in HTTP server instead of WireMock, so that dependency was not needed.

## What the customer's IT must set up (for the install guide)

### E-mail

1. Choose the mail server the app sends through, either the company mail server or an internal relay. Note its host name and port. Choose the security:
   - **STARTTLS on port 587** (recommended);
   - **TLS on port 465**;
   - **none on port 25**, only for a relay inside your own network.
2. Create a sender address for the app, for example `notifications@yourcompany.com`. Either give it a login that may send mail (SMTP AUTH), or allow the app server's address on the relay without a login.
   - **Microsoft 365:** SMTP AUTH is off by default for each mailbox and must be switched on for this mailbox. Microsoft is also retiring password login for SMTP AUTH (see question 2). The safer path is an Exchange Online relay connector, or your own relay.
3. Store the sender's password in the secret store. Give the installer the **path** of the secret, never the password itself.
4. Give the installer the host, port, security, user name (if any), the secret's path, and the sender address and name.
5. If the mail server's certificate is signed by your own certificate authority, add that authority to the app's Java trust store.
6. Open the firewall from the app server to the mail server on that port.
7. Make sure the sender may send to the people who will receive notifications. The messages carry `Auto-Submitted: auto-generated`, so out-of-office replies are suppressed.

### Microsoft Teams

1. In Teams, open the channel that should receive notifications, choose **⋯ → Workflows**, and pick the template **"Post to a channel when a webhook request is received"**. In Power Automate, this is a flow with the trigger "When a Teams webhook request is received" and the action "Post card in a chat or channel".
2. Choose the team and channel. Set **who can trigger** to **Anyone**: the app does not sign in, and the secret part of the address is what protects it. Save, then copy the address the flow shows.
3. Create the flow with a **service account**, not a personal one. If the owner leaves or the flow is switched off, the address stops working, and the admin screen shows "Teams webhook address no longer valid".
4. Store the address in the secret store, because it works like a password. Give the installer only the secret's path.
5. Allow outbound HTTPS (port 443) from the app server to the Power Platform / Logic Apps hosts (`*.logic.azure.com`, `*.api.powerplatform.com`). If you use a proxy, set the Java proxy options (`https.proxyHost`, `https.proxyPort`).
6. Old "Incoming Webhook" connector addresses (`…webhook.office.com…`) are retired by Microsoft and will be refused. Create a Workflows address instead.

### Slack

1. At `api.slack.com/apps`, create an app ("From scratch") in your workspace. Open **Incoming Webhooks**, switch it on, choose **Add New Webhook to Workspace** and pick the channel. A workspace admin may need to approve the app.
2. Copy the address (`https://hooks.slack.com/services/…`) and store it in the secret store, because it works like a password. Give the installer only the secret's path.
3. Allow outbound HTTPS from the app server to `hooks.slack.com`, through the proxy if you use one.
4. If the channel is archived or the app is removed, the admin screen shows "Slack webhook address no longer valid". Add a new webhook and update the secret.

### Database

The app creates the six `notif_` tables in its own schema when it starts, through its migration step. The app's database user needs rights to create tables during that step, then read and write on those tables. The tables hold ids, settings and message text that follows the content rule. They hold no passwords, webhook addresses or e-mail addresses.

## Decisions I had to make

1. **The P003 tests moved with the fakes**, because the main module's tests cannot depend on the testkit (that would be a Maven cycle). The main module now has the I/O tests; the testkit has the core-logic tests. The fakes keep their package name (CLAUDE.md: package names stay stable).
2. **Idempotency keys name what the message is**, not when it was made: an event id and destination, a condition's start and alert number, a summary bucket and its due time. A summary bucket takes no new items once it is due, so rebuilding it gives the same message. The notifier works in whole milliseconds, so keys survive the database round trip (Postgres stores microseconds).
3. **One window is "at least once", not "exactly once"**: the receiver took the message, then the process stopped before the outbox recorded it. After the lease (5 minutes) the message goes again under the same key. E-mail keeps the same `Message-ID`; Teams and Slack have no way to deduplicate. Every other crash point sends exactly once, and the tests cover each one. See question 1.
4. **Retry policy:** first retry after 1 minute, doubling up to 30 minutes, giving up after 24 hours. A receiver's `Retry-After` replaces the backoff for that attempt. Defaults: a 5-minute lease, up to 100 retries per tick, and finished messages kept for 30 days. Builder options are there for P005 configuration.
5. **Which codes are retried** (table above). A TLS failure counts as permanent, because a certificate or configuration problem does not fix itself. `SENDER_FAILED` (an unexpected fault) and `RECIPIENTS_UNAVAILABLE` are retried within the 24 hours.
6. **One refused recipient:** the others still get the message. The refusal is reported and not retried, so no one gets it twice.
7. **Recipients are looked up again on every attempt.** The outbox stores roles, not e-mail addresses, so no address is stored and role changes are picked up.
8. **Mail server and webhook settings come from config** (`MailServerDirectory`, `WebhookDirectory`, wired in P005), not from the database. The database keeps only destination ids and their channel, as before.
9. **https only for webhooks.** `allowingPlainHttpForTests()` exists for tests only. Redirects are not followed, and the JVM proxy settings are used.
10. **Logging uses `System.Logger`** (part of the JDK, no new dependency; it routes to the app's logging). It writes ids, codes and fixed reasons only. Jakarta Mail's debug output is off and its login trace is never enabled.
11. **Plain-text e-mail only.** Slack uses only plain-text blocks, so nothing in a message is read as formatting or a link.
12. **A summary whose event types were all removed** from the catalogue in a later release is dropped without a message.
13. **The text of `WEBHOOK_REJECTED` changed** to "… refused the webhook request (not authorised)", to match 401/403. The code itself is unchanged.

## Not covered

- A successful SMTP send over TLS. Only the TLS failures are tested, because there is no trusted test certificate.

## Questions

1. Is the at-least-once window in decision 3 acceptable? The alternative is to mark such messages "outcome unknown" and never resend them, which can lose an alert. **Recommendation:** accept it. A rare duplicate is less harmful than a lost critical alert.
2. Microsoft is retiring password login for SMTP AUTH in Exchange Online. Should we add OAuth 2.0 (XOAUTH2) for Microsoft 365 and Google mail? **Recommendation:** yes, as its own task (Angus supports it; the token would come through the secret resolver). Until then, the install guide points to a relay connector.
3. `publish` still sends inside the call and can wait up to the channel timeouts (30 seconds), holding the notifier's lock. **Recommendation:** in P005, `publish` only saves to the outbox, and a background worker sends at once.
4. Are the retry defaults right (24 hours to give up, 30 days kept)? **Recommendation:** yes, and expose them as configuration properties in P005.
5. Is an authenticating proxy needed for outbound webhooks? **Recommendation:** not now. Plain JVM proxy settings are supported; ask the first customers.
6. Should the P004 decisions above be copied into section 5 of the design doc? **Recommendation:** yes, once you have reviewed them.
