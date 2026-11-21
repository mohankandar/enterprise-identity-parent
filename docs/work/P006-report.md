# P006 report — close notifications

## Design doc

Section 5 of `docs/design/notifications.md` now has:

- **Owner answers to the P005 questions (B1–B5).** B1: OAuth 2.0 for mail is still open. B2: "Not used yet" and
  `DEGRADED` are accepted. B3: health stays per instance. B4: each app runs `db/platform/notifications` as a separate
  Flyway run with its own history table `notif_schema_history`, in its schema step, never at app startup. B5: one
  mail server for V1.
- **P005 decisions 1–16**, marked where the answers above confirm them.

The note saying that the P005 decisions were still waiting for review is removed.

## Tests

`mvn -o clean verify`: **283 tests, 0 failures, 0 errors** (identity-core 73, identity-spring 15, notifications 65,
notifications-testkit 101, notifications-spring 29). It ran offline, as in P005.

The first clean run failed one test: `WiringTest.anImmediateEventIsSentByTheBackgroundSender` (expected 1 SENT row,
found 0). The cause is a race in the test, not in the code. The mail server has the message a moment before the
sender marks the outbox row as SENT, and the test checked the row straight away. The test now waits up to 10 s for
the row, as it already does for the e-mail. After that, two clean runs in a row passed all 283 tests. No production
code changed.

## Merge

- The design doc and the test fix were committed on `feature/notifications-core` (`377656c`) and pushed.
- `main` was fast-forwarded from `7e4d370` to `377656c`: no merge commit, no conflicts. It now has P003–P005 and the
  design doc. `main` was pushed to origin.
- `feature/notifications-core` was deleted locally and on origin.
- This task and this report are committed on `main` and pushed.

## Final module list

Root `<modules>` and `platform-bom`, all at the root version:

| Module | Purpose |
|---|---|
| `platform-bom` | Bill of materials |
| `platform-identity-core` | Identity: claims, OIDC bindings, admission, role mapping |
| `platform-identity-spring` | Spring Security adapters for identity |
| `platform-notifications` | Notifications core, senders, outbox, Postgres stores, SQL (`db/platform/notifications`) |
| `platform-notifications-testkit` | In-memory test support (test scope only) |
| `platform-notifications-spring` | Spring Boot wiring, properties, startup table check, workers, health |

## Still open

- OAuth 2.0 (XOAUTH2) for Microsoft 365 and Google mail (P004 A2 / P005 B1). Recommendation: its own task.
- Each app's switch-over task must add the separate Flyway run for `db/platform/notifications` (B4).
