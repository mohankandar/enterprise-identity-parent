# P003 report — notifications core logic

Branch `feature/notifications-core` (from `main`). Not merged.

## Housekeeping

- a. `main` did not exist locally. I created it from `origin/main` and fast-forwarded it to `feature/platform-core-setup` (`0c5c0a7..7e4d370`). Pushed `main`, then created `feature/notifications-core` from it.
- b. Deleted `platform-core`, `platform-bom`, `platform-identity-core`, `platform-identity-spring` and `platform-notifications` from `~/.m2/repository/com/documentplatform/identity/`. Left: `enterprise-identity-core`, `-parent`, `-spring`.
- c. Ran `git branch -d fix/null-audience-validator` in `C:\dev\shared\enterprise-identity-parent`. It deleted cleanly because the branch was merged (it was at `85135a7`). Nothing else changed there.
- The design doc `docs/design/notifications.md` was untracked. I committed it unchanged on the new branch.

## What was built

Module `platform-notifications`, package `com.documentplatform.notifications`. It is plain Java with no dependencies apart from JUnit for tests. It does no logging and no I/O.

| Area | Types | How it fits |
|---|---|---|
| Event types | `EventType` (+ `Builder`), `EventKind` (BUSINESS / TECHNICAL), `EventDefaults`, `FieldSpec` (declared counts and times), `NumericThreshold`, `ConditionTiming` | The app describes each event once: its name, kind, whether it is critical or continuous, its defaults, and the fields it may carry. |
| Catalogue | `EventCatalogue` (+ `Builder`) | The app registers its types at start-up, together with its IT Admin role. Registration applies the rules: a critical event must default to Immediately or Every hour, be on, and go to the IT Admin. Every type needs a recipient role. Condition timing must be in range. |
| Schedule | `Frequency`, `Schedule` | Immediately, Every hour, Every 4 hours (00/04/08/… in the tenant's zone), or Once a day at a chosen time in the tenant's zone. `nextDue()` gives the time the next summary goes. |
| Settings | `TenantSettings`, `EventSettings`, `NotificationSettings`, `SettingsStore` | `TenantSettings` (set by the IT Admin): the app address, the time zone, and connected destinations by id. There are no secrets in it. `NotificationSettings` saves technical events per tenant and business events per entity. It refuses the wrong level, and on every save it checks the same rules as the catalogue plus threshold ranges and connected destinations. `effective()` returns the saved settings or, if there are none, the defaults. |
| Publish API | `NotificationEvent` (+ `Builder`), `RecordIdentifier`, `AppLink`, ids (`TenantId`, `EntityId`, `EventTypeId`, `RoleId`, `DestinationId`) | Structured fields only: type, tenant, entity, identifier, declared counts (numbers) and times (instants), an app path, and the time it happened. There is no text field. |
| Routing | `Notifier`, `RecipientResolver`, `Recipient` | Each event goes through: is it enabled? → which connected destinations? → for e-mail, who holds the roles (looked up at send time). Immediately events are sent at once. All other frequencies wait in a summary bucket. |
| Summaries | `SummaryBuckets` (internal) | Items are grouped by destination, schedule, entity and recipient roles. Each bucket is sent once when it is due and then emptied. |
| Conditions | `ConditionTracker` (internal), `ConditionKey` | `conditionFailing(event)` is called on every failure and `conditionCleared(key)` when the problem ends. A condition alerts after its threshold, repeats on its interval, and sends Recovered once, but only if it had alerted. |
| Messages | `Message`, `MessageKind`, `MessageBuilder` (internal) | Messages are built only from catalogue labels and the event's structured fields: name, identifier, counts and times, and a link. |
| Channels | `Channel`, `OutgoingMessage`, `SendResult`, `FailureCode`, `DeliveryReport`, `DeliveryStatus` | A sender returns a specific `FailureCode`, for example "Teams webhook address no longer valid". The `Notifier` returns a `DeliveryReport` per destination, holding ids and codes only. |
| Fakes | `inmemory.InMemoryChannel`, `InMemoryRecipientResolver`, `InMemorySettingsStore` | These ship in the main jar so the apps can use them in their own tests. |
| Time | `java.time.Clock` + `Notifier.tick(Instant now)` | The app's scheduler calls `tick` (every minute is enough). Tests move the clock themselves. |

Apps use it like this: build an `EventCatalogue`, create `NotificationSettings(catalogue, store)` and then `Notifier(settings, resolver, channels, clock)`. After that, call `publish`, `conditionFailing` and `conditionCleared` as things happen, and `tick` on a schedule.

## Tests

`mvn verify`: **173 tests, all pass.** That is identity 88 (73 core + 15 spring, unchanged) plus **85 new**. `mvn install` succeeded, and the new jar is in `~/.m2/repository/com/documentplatform/platform/platform-notifications/0.1.0-SNAPSHOT/`.

| Test class | Tests | What it covers |
|---|---|---|
| `EventCatalogueTest` | 14 | registration rules, defaults (15 min / 4 h), duplicates, labels |
| `ScheduleTest` | 6 | hourly / 4-hourly / daily due times, tenant time zone |
| `NotificationSettingsTest` | 12 | **critical set slower than hourly is refused**, critical can't be switched off or lose the IT Admin, the two settings levels, per-entity settings, threshold and timing ranges, destinations, falling back to defaults |
| `ContentRuleTest` | 26 | **sensitive values can't reach a message**: the API has no text field (checked by reflection), identifiers and links refuse sentences, key=value, e-mail addresses and URLs, refusal messages don't repeat the value, a whole message is checked word for word, no destination or address appears in a message |
| `RoutingTest` | 10 | enabled, destinations, roles per entity, webhooks, specific failure codes, misuse |
| `SummaryTest` | 7 | hourly, 4-hourly per entity, daily in tenant zone, no re-send, 10-item cap |
| `ConditionTrackerTest` | 10 | threshold, **no duplicate alerts during a continuous failure**, repeat, **Recovered sent once**, clear before the threshold, adjusted timing, critical hourly conditions going into the summary |

The existing `NoProductNamesGuardTest` scans the whole repo, so it covers the new module too.

## Decisions I had to make (design unclear or silent)

1. **Critical events cannot be switched off.** Rule 8 says they "go Immediately to the IT Admin", which I read as mandatory. Their recipient roles must always include the IT Admin role, and the app names that role when it registers its catalogue.
2. **The IT Admin role is always looked up at tenant level**, even for a per-entity event such as "licence expired" (B, C). Without this, the IT Admin was not found for per-entity critical events.
3. **Limits on condition timing.** Threshold 0–24 h, and at most 1 h for critical events, so they keep within "Immediately or Every hour". Repeat 15 min–24 h. These limits are my choice; the design gives none.
4. **Recovered** follows the condition's own event type and settings, not a separate "recovered" event type. It is sent only if an alert went out. If the condition clears before its threshold, nothing is sent.
5. **Where events go when the admin chose nothing:** the tenant's e-mail destination. Teams and Slack only receive events the admin assigned to them, and receive no role-based recipients.
6. **One summary per destination, schedule, entity and set of recipient roles.** It lists up to 10 items per event type, then "… and N more", with totals for counts. A summary covers what arrived since the last one was sent.
7. **Saved settings that break a rule after a catalogue change** (for example, a type became critical in a later release) fall back to the event type's defaults instead of being used.
8. **Business events must carry an entity.** Technical events may carry one, but their settings are per tenant.
9. **Which characters an identifier and a link may contain.** Identifier: at most 64 characters, from `A–Z a–z 0–9 . _ : / # -`, with no spaces and no `//`. Link: a path only, with no query string or fragment; the host comes from the tenant's app address. Checking characters cannot recognise a short secret that looks like an identifier. The real protection is that the API has no free-text field.
10. **"Immediately" is sent inside `publish`.** A failed send is reported, not retried. Retries and the outbox come in P004. Summary buckets and condition state are in memory until then.
11. **Own `TenantId` and `EntityId` types**, so the module does not depend on identity-core.
12. **Numeric thresholds** (such as "disk almost full at 90 %") are declared per event type with a default and a range. The admin may adjust them within the range, and the app reads them with `NotificationSettings.threshold(...)`. This module does not compare them itself.

## Questions

1. Business events that concern the whole tenant, such as licence expiry: must the app publish one per entity, or should there be tenant-level business settings? **Recommendation:** add tenant-level business settings, used when the event has no entity. It is a small change in P004.
2. Can a critical event really never be switched off (decision 1)? **Recommendation:** keep it always on. The IT Admin can still change Immediately to Every hour and add Teams or Slack.
3. Are the timing limits in decision 3 acceptable? **Recommendation:** yes, as they are: critical threshold at most 1 h, repeat between 15 min and 24 h.
4. Should a summary sent to a Teams or Slack webhook combine all entities instead of one message per entity? **Recommendation:** keep one per entity for now. It is simpler and matches the per-entity business settings. Revisit if channels get noisy.
5. Should the in-memory fakes stay in the main jar (`inmemory` package) or move to a separate test-support artifact? **Recommendation:** keep them in the main jar until a second capability needs fakes too.
