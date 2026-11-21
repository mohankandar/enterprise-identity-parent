# Notifications — install guide for IT

This page tells your IT team what to set up so the app can send notifications by e-mail, Microsoft Teams and Slack.
It also covers the database. The app's own install guide says where its install config file is. The settings below
all start with `platform.notifications`.

Two rules hold throughout:

- **No secrets in the config file.** The mail password and the Teams and Slack webhook addresses go into your secret
  store. The config file holds only the **path** of each secret (a "secret reference"), for example
  `notifications/mail-password`.
- **The app never changes the database by itself.** You create the tables with the schema step (section 4). At startup
  the app only checks them.

You only need the channels you want to use. With none configured, the app still starts and records notifications, but
sends nothing.

---

## 1. E-mail

1. **Choose the mail server** the app sends through: your company mail server or an internal relay. Note its host name
   and port, and choose the security:
   - **STARTTLS on port 587**: recommended.
   - **TLS on port 465.**
   - **None on port 25**: only for a relay inside your own network.
2. **Create a sender address** for the app, for example `notifications@yourcompany.com`. Then either:
   - give it a login that may send mail (SMTP AUTH), or
   - allow the app server's address on the relay, with no login.

   **Microsoft 365:** SMTP AUTH is off by default for each mailbox, so switch it on for this one. Microsoft is also
   retiring password login for SMTP AUTH. The safer path is an Exchange Online relay connector, or your own relay.
3. **If there is a login,** store the sender's password in the secret store. Note the secret's path, not the password.
4. **Give the installer:** host, port, security, user name (if any), the secret's path, and the sender address and
   display name.
5. **If your own certificate authority signs the mail server's certificate,** add that authority to the app's Java
   trust store.
6. **Open the firewall** from the app server to the mail server on that port.
7. **Make sure the sender may send to everyone who will get notifications.** Messages carry
   `Auto-Submitted: auto-generated`, so out-of-office replies are held back.

One mail server serves all e-mail notifications.

## 2. Microsoft Teams

1. In Teams, open the channel that should receive notifications. Choose **⋯ → Workflows** and pick the template
   **"Post to a channel when a webhook request is received"**. In Power Automate this is a flow with the trigger "When a
   Teams webhook request is received" and the action "Post card in a chat or channel".
2. Choose the team and channel. Set **who can trigger** to **Anyone**: the app does not sign in, and the secret part of
   the address protects it. Save, then copy the address the flow shows.
3. **Create the flow with a service account,** not a personal one. If the owner leaves or the flow is switched off, the
   address stops working, and the app reports "Teams webhook address no longer valid".
4. **Store the address in the secret store.** It works like a password. Give the installer only the secret's path,
   and a short id for this destination, for example `ops-teams`.
5. **Allow outbound HTTPS (port 443)** from the app server to the Power Platform / Logic Apps hosts
   (`*.logic.azure.com`, `*.api.powerplatform.com`). If you use a proxy, set the Java proxy options (`https.proxyHost`,
   `https.proxyPort`). Proxies that ask for a login are not supported.
6. Old "Incoming Webhook" connector addresses (`…webhook.office.com…`) were retired by Microsoft, and the app refuses
   them. Create a Workflows address instead.

Repeat for each channel that should receive notifications. Each one gets its own id and secret.

## 3. Slack

1. At `api.slack.com/apps`, create an app ("From scratch") in your workspace. Open **Incoming Webhooks**, switch it on,
   choose **Add New Webhook to Workspace** and pick the channel. A workspace admin may need to approve the app.
2. Copy the address (`https://hooks.slack.com/services/…`) and **store it in the secret store**. It works like a
   password. Give the installer only the secret's path and a short id, for example `ops-slack`.
3. **Allow outbound HTTPS** from the app server to `hooks.slack.com`, through the proxy if you use one.
4. If the channel is archived or the Slack app is removed, the app reports "Slack webhook address no longer valid". Add
   a new webhook and update the secret. The app reads secrets each time it sends, so no restart is needed.

## 4. Database

The notifications tables all start with `notif_`. There are seven: tenant, destination, event settings, outbox,
summary items, conditions and the schema version.

**Creating and upgrading the tables is a separate schema step that you run.** It is part of the app's own schema step.
The SQL files ship inside the app under `db/platform/notifications` (`V1__notifications.sql`, …).

1. **Before the first start, and after every upgrade,** run the app's schema step. Run it as a database user that may
   create tables in the app's schema, for example the schema owner.
2. **The app's runtime user needs no rights to create or change tables.** Grant it only:

   ```sql
   grant usage on schema <app_schema> to <app_user>;
   grant select, insert, update, delete on
       notif_tenant, notif_destination, notif_event_settings,
       notif_outbox, notif_summary_item, notif_condition
     to <app_user>;
   grant select on notif_schema_version to <app_user>;
   ```

   No sequence rights are needed.
3. **At startup the app checks the tables and stops if something is wrong.** The message says exactly what is wrong:

   | Message starts with | What to do |
   |---|---|
   | `Notifications tables missing (expected version 1)` | The schema step was not run on this database schema. Run it. |
   | `Notifications tables at version N, expected version V` | The schema step for this release was not run. Run it. |
   | `Notifications tables at version N, newer than this app expects` | An older app release is running against an upgraded database. Install the matching release. |
   | `Notifications tables at an unknown version` / `Notifications tables incomplete` | Tables were changed or dropped by hand. Run the schema step, or restore the database. |
   | `The app's database user lacks …` | Grant the rights in step 2. |

**What the tables hold:** ids, the admins' settings, and the text of outgoing messages. That text contains only the
event name, a record number, counts, times and a link into the app. The tables hold no passwords, no webhook
addresses, no e-mail addresses and no document content. Sent and failed messages are deleted after 30 days.

## 5. Settings

| Key (prefix `platform.notifications.`) | Default | Meaning |
|---|---|---|
| `enabled` | `true` | `false` switches notifications off completely. |
| `mail.host` | — | Mail server host. Leave out all `mail.*` keys for no e-mail. |
| `mail.port` | 587 / 465 / 25 | Defaults to the usual port for the chosen security. |
| `mail.security` | `STARTTLS` | `STARTTLS`, `TLS` or `NONE`. |
| `mail.username` | — | Login user. Leave out for a relay without a login. |
| `mail.password-secret` | — | **Path** of the password in the secret store. Required with `mail.username`. |
| `mail.from-address` | — | Sender address. Required with e-mail. |
| `mail.from-name` | — | Sender display name. |
| `mail.connect-timeout` / `mail.read-timeout` | `10s` / `30s` | How long to wait for the mail server. |
| `teams.<id>` | — | One line per Teams destination: id = **path** of the webhook address secret. |
| `slack.<id>` | — | One line per Slack destination: id = **path** of the webhook address secret. |
| `webhook.connect-timeout` / `webhook.request-timeout` | `10s` / `30s` | How long to wait for Teams and Slack. |
| `retry.first-delay` | `1m` | First retry after a temporary failure. |
| `retry.max-delay` | `30m` | The wait doubles up to this. |
| `retry.give-up-after` | `24h` | Stop retrying a message this long after it was made, and report it. |
| `lease` | `5m` | How long one app instance holds a message it is sending. Must be longer than the timeouts added up. |
| `retention` | `30d` | How long sent and failed messages are kept. |
| `tick-interval` | `1m` | How often alerts for ongoing problems and due summaries are checked (1 s to 5 min). |
| `sender.poll-interval` | `10s` | How often the sender looks for retries and other instances' messages (1 s to 5 min). |
| `sender.batch-size` | `100` | Messages per send round (1 to 1000). |

Destination ids may use letters, digits, `.`, `_` and `-`. Lower-case ids such as `ops-teams` are simplest. To keep
capitals or `_` in an id, put it in brackets: `platform.notifications.teams[Ops_Teams]=…`.

**Example** (`application.yml`):

```yaml
platform:
  notifications:
    mail:
      host: smtp.yourcompany.com
      port: 587
      security: STARTTLS
      username: notifications@yourcompany.com
      password-secret: notifications/mail-password      # path in the secret store, not the password
      from-address: notifications@yourcompany.com
      from-name: Document notifications
    teams:
      ops-teams: notifications/teams-ops-channel        # path of the secret holding the Workflows address
    slack:
      ops-slack: notifications/slack-ops-channel        # path of the secret holding the hooks.slack.com address
```

All other settings can keep their defaults.

**At startup the app checks every setting.** If one is wrong, it stops and names each problem with its key, for
example:

```
Notifications configuration is not valid:
 - platform.notifications.mail.security: must be STARTTLS, TLS or NONE
 - platform.notifications.teams.ops-teams: not a valid secret reference (…); give the path of the secret that holds
   the webhook address, never the webhook address itself (this value looks like an address: store it in the secret
   store and give its path here)
```

The message never repeats a value that might be a secret.

## 6. Several app servers

Several instances of the app may share one database. Each message is sent by one instance only, and no extra setup
is needed.

## 7. Checking that it works

With Spring Boot Actuator switched on, `/actuator/health/notifications` lists each destination. A destination
appears once the app has sent to it; Teams and Slack destinations from the config are listed from the start.

| State | Meaning |
|---|---|
| **Working** | The last message went through. |
| **Having problems** | The last attempt failed for a temporary reason and is being retried, or one message was refused (for example one recipient address). The `code` and `reason` say which. |
| **Down** | It cannot work until someone fixes something, for example "Mail server refused the login" or "Teams webhook address no longer valid". Also shown when temporary failures have gone on for 15 minutes. |
| **Not used yet** | Nothing was sent to it since the app started. |

The health page shows ids, states, codes and reasons only, never an address, login or secret. A destination that is
down does not mark the whole app as unhealthy. The notifications entry shows `DEGRADED` instead.
