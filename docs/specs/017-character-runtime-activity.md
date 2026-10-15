# Character runtime activity (AI status + activity usage)

> Owner: @n1rmata

## Problem

The platform wants to show _"what is this character doing right now"_ on a live admin / dashboard view, and, since the
app can send a character fishing without a client ([`038`](038-app-offline-sessions.md)), also _"how much of each
activity has this character used and how much is left"_. Two distinct, **orthogonal** signals are wanted:

- **AI status** - the engine-native control intention the core puts a player character in (idle / moving / attack / cast /
  pick-up / interact / ...). Present in some form on essentially every L2 core.
- **Activity usage** - sustained activities and their consumption (fishing and its penalty clock, private store,
  auto-farm, ...) in one entity: what runs now, how much was used, how much is left, when it resets. Some servers do not
  ship fishing at all.

The design must stay **build-agnostic**: the activity vocabulary is a closed enum of generic L2 concepts
(`ActivityType`), each host maps its engine onto it and omits what its build lacks; AI status stays an open string and
tolerates cores whose intention set differs.

Audience: platform-side dashboard and command-flow consumers; host-side authors hooking the runtime character mapping.

## Requirements

> Parent / sibling features:
>
> - [`runtime-sync`](006-runtime-sync.md) - the FNV-1a snapshot+diff engine and `RuntimeEntityMapping` SPI this rides.
> - [`db-sync`](003-db-sync/spec.md) - carries the activity usage of a character that is not running (spend of an
>   offline character).
> - [`character-core-extension`](013-character-core-extension.md) / [`character-online-hero`](016-character-online-hero.md) -
>   `CharacterRuntimeDto` / `CharacterDbDto` shape.
> - [`038-app-offline-sessions`](038-app-offline-sessions.md) - the sessions whose activities this entity reports.

**Must:**

- [done] R1. `CharacterRuntimeDto` MUST carry `@Nullable String aiStatus` - the engine AI control intention as an **open
  string**. Additive + nullable; hosts that do not report it leave it `null`. Open string (not an enum) so a core with a
  divergent intention set is not a breaking contract change.
- [done] R2. `nx-gs-adapter-api` MUST ship `WellKnownAiStatuses` documenting the canonical lower_snake_case values - `idle` /
  `active` / `rest` / `attack` / `cast` / `moving` / `follow` / `pick_up` / `interact` (the L2 `CtrlIntention` set, name
  lower-cased). Hosts MAY emit additional values; consumers treat unknowns as opaque.
- [done] R3. Current activities were first a **list**: a character can be in several sustained activities at once
  (autofarming while fishing), independent of `aiStatus` (any combination legal, no precedence on the wire). That list
  (`CharacterRuntimeDto.activities`, the string-typed `Activity` model class with its open `Map<String,String>`
  metadata, `WellKnownActivities`) is replaced by R11 and removed in the same api release (R12).
- [done] R4. The raw-seconds keys (`elapsed_seconds`, `seconds_remaining`, `seconds_to_next_tier`) were removed in
  `api/v0.93.1`; metadata durations are ISO-8601.
- [done] R5. The runtime mapping of the host MUST populate `aiStatus` and the current activities (from the switch on, the usage rows of R11) from live state, and the
  FNV-1a hash of the DTO MUST mix both so a change triggers a diff/publish. A fishing character republishes every
  runtime tick because its elapsed time advances; accepted for the character population in scope. Offline tombstones carry
  neither.
- [done] R6. `nx-gameservers` MUST persist both on `gs_characters` (`ai_status VARCHAR(32)`, activities as opaque `JSONB`),
  written by the runtime-sync upsert (not timestamp-gated, newest tick wins); the read API forwards the JSON raw. The
  platform never models per-activity keys.
- [todo] R11. A new entity **`characteractivityusage`** MUST ship on the runtime channel and on db-sync. One row per
  `(charId, type, key)`:

  | Field           | Type                        | Notes                                                                                                                    |
  | --------------- | --------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
  | `charId`        | `int`                       | Character                                                                                                                |
  | `type`          | `ActivityType`              | Closed enum, constant name on the wire (`UPPER_SNAKE_CASE`); see below                                                   |
  | `key`           | `String?`                   | Refinement inside the type, e.g. an instance template id; `null` when the type has no sub-identity                       |
  | `subtype`       | `String?`                   | Further sub-kind of the activity                                                                                         |
  | `activeSince`   | `Instant?`                  | `null` = the activity is not running now; otherwise the moment the current run started                                   |
  | `remaining`     | `long`                      | What is left in `unit`s until the limit                                                                                  |
  | `metadata`      | `Map<String,String>?`       | Open map                                                                                                                 |
  | `unit`          | `MS` / `COUNT`              | What `used`, `limit` and `remaining` are measured in                                                                     |
  | `used`          | `long`                      | Consumed so far in the current period                                                                                    |
  | `limit`         | `Long?`                     | Ceiling per period; `null` = unlimited                                                                                   |
  | `resetSchedule` | `String?`                   | RRULE (RFC 5545) in UTC: `DTSTART:<UTC>Z` + `RRULE:...`, no `TZID`; `null` = never resets (see R13)                     |
  | `steps`         | `List<{after, multiplier}>` | Tiers: after `after` of use the effect (e.g. catch chance) is scaled by `multiplier`; empty = no tiers                   |
  | `updatedAt`     | `Instant`                   | When the host last updated the row                                                                                       |

  `ActivityType` (api, closed): `FISHING`, `READING`, `AUTOFARMING`, `AUTOMACRO`, `TRADE`, `OFFLINE_TRADE`,
  `OFFLINE_FISHING`. Hosts cannot invent tokens; a new activity is an api release first, then the platform enum and
  CHECK, then the host. The same enum types the presence `activity` field ([`013`](013-character-core-extension.md)).
  An app private store and a `.offline` store both use `OFFLINE_TRADE`; the presence `source` tells them apart.

  Fishing rows: the `FISHING` row carries `used` / `limit` / `steps` / `resetSchedule` shared by live and offline
  fishing, and `activeSince` while any fishing runs. The `OFFLINE_FISHING` row carries only `activeSince` of an app
  fishing session (no usage numbers).

  Consumption and limits are named `usage`, not `quota`. Fishing notifications are a deferred, separate task; the platform
  can later derive "fishing ended" from a fishing row (live or offline) whose `activeSince` clears (for an offline session
  the session itself is described by [`038`](038-app-offline-sessions.md)). After a host restart (the fishing thresholds,
  daily ceiling and reset change only with the host's own configuration) the host republishes every usage row.

  The fishing penalty is one such row (`used` is the accumulated active fishing time, `steps` its tiers, `limit` the
  daily ceiling, `resetSchedule` the daily reset); a running fishing session fills `activeSince`.

- [todo] R12. `characteractivityusage` replaces `CharacterRuntimeDto.activities`. The **host switches in one release**: it
  sends only the new entity and no longer sends `activities`. In the SAME api release the adapter removes
  `CharacterRuntimeDto.activities`, the string-typed `Activity` model class and `WellKnownActivities` (no
  `@Deprecated` copy, see the root `CLAUDE.md` › breaking wire changes). The **platform straddles**: it reads the old
  `activities` JSON of a not-yet-restarted host with its own private legacy mapping, not with adapter classes, until
  every game server of the tenant restarted on the new host, then deletes that branch (see
  [`038`](038-app-offline-sessions.md) Rollout).

- [todo] R13. `resetSchedule` is an RRULE (RFC 5545), the platform canon for recurring schedules; one-off instants stay
  ISO-8601. Platform time is always UTC: the rule is written `DTSTART:<UTC>Z` + `RRULE:...` with no `TZID`. The host
  converts its own local server time to UTC (for example a daily reset at 06:00 on a UTC+3 server is
  `DTSTART:...T030000Z` + `RRULE:FREQ=DAILY`); if the host's clock offset changes, recomputing and republishing the
  rule is the host's job. Converting the older `RecurringSchedule` shapes to RRULE is deferred, see
  [`TODO §7`](../TODO.md#7-recurringschedule-to-rrule).
- [todo] R14. Platform storage MUST key the entity by `(server, character, type, key)` with `NULL`-safe uniqueness, and
  merge two sources by freshness: the runtime channel says what is running now (`activeSince`); a runtime tombstone
  clears only `activeSince`; db-sync supplies the spend of a character that is not in the world.
- [todo] R15. `ActivityType` MUST contain `OFFLINE_FISHING` (activity of an app-session fisher,
  [`038-app-offline-sessions`](038-app-offline-sessions.md)) next to `OFFLINE_TRADE`. `OFFLINE_TRADE` is also the
  activity of an app private store: the presence `source` tells it apart from a `.offline` store, and the platform's
  offline-store place check works unchanged.

**Should:**

- [done] R7. `GET /v1/gameservers/characters` exposes `aiStatus` and the activities (raw JSON passthrough); display only.
- [done] R8. The platform chat bot shows fishing info in the character view only while the character is fishing.

**Could:**

- [todo] R9. Typed metadata (promoting well-known keys to typed fields) - NOT shipped; the fully open `Map<String,String>`
  is the deliberate choice.
- [todo] R16. Tracking a character's presence inside instances as usage of an `INSTANCE` activity (`key` = instance
  template id; needs the new `ActivityType` value first), see
  [`TODO §9`](../TODO.md#9-instance-type-activity-usage).

**Non-goals:**

- **An open activity vocabulary.** The activity type is a closed enum of generic L2 concepts; hosts map onto it and
  never invent tokens. (`aiStatus` stays an open string with `WellKnownAiStatuses`.)
- **A `FISHING` (or any activity) value on `aiStatus`** - fishing is not an engine intention; the fields are independent.
- **Coupling / precedence between `aiStatus` and the activities** on the wire or in the DB.
- **Filtering / sorting the character list by activity**, and **history / analytics of activity over time** (live-state
  overlay only).

### Edge cases

- **Core without fishing** - no `FISHING` row until that core's mapping emits it; no contract change.
- **Unknown `aiStatus` at the consumer** - stored/displayed raw, no behavior gated on it. An unknown activity `type`
  arriving at the platform is a WARN plus a metric and the row is skipped, never the partition.
- **`getAI()` null mid-transition** - `aiStatus = null`, the row is still emitted.
- **Activity churn** - `aiStatus` flips with combat and movement; a running fishing row republishes every tick. Accepted.
  A churn-free `startedAt`-only model does not fit the fishing penalty clock, which is a paused accumulator of active
  time, not wall-clock since start (hence `used` plus `activeSince`).
- **Runtime arrives before db-sync** - skeleton row; db columns stay `null` until db-sync catches up.
- **Usage row of an offline character** - arrives through db-sync with `activeSince = null`.
- **Daily reset** - the period boundary comes from `resetSchedule`; consumers do not assume a fixed hour.

## Open questions

- [resolved: `aiStatus` is an open string with `WellKnown*` constants, not an enum.]
- [resolved: the activities are a list of `{type, metadata}`; current state rides the runtime channel, not CDC and not a
  discrete event.]
- [resolved: activities were stored opaque as JSON; superseded by `characteractivityusage` rows with a closed `type`.]
- [resolved: two independent fields, not one unified "state".]
- [resolved: activity and usage are one entity, `characteractivityusage`, not a second parallel list.]
- [resolved: `type` is the closed `UPPER_SNAKE_CASE` enum `ActivityType`; no lower_snake on the wire.]
- [ ] [NEEDS CLARIFICATION: Java package of the usage entity and whether it is one DTO for runtime and db-sync or two]
- [resolved: `steps[].after` is measured in the row's `unit`.]
- [resolved: `remaining` is `limit - used`, floored at zero, when `limit` is set; `null` when `limit` is `null`.]

## Versioning

- `nx-gs-adapter-api` - one release: the usage entity and the closed `ActivityType`; the same release removes
  `CharacterRuntimeDto.activities`, the string `Activity` class and `WellKnownActivities` (R12). Breaking for source,
  so a version bump accordingly; no `@Deprecated` window.
- `nx-gs-adapter-core` / `nx-gs-db-sync-core` / `nx-gs-runtime-sync-core` - the usage entity rides the existing engines
  (the runtime engine hashes whatever the mapping mixes).
- The host: sends the usage entity only, from the switch release on.
- `nx-gameservers` - `gs_character_activity_usages` (no FK to `gs_characters`; orphans are removed by a sweep, like the
  character effects), closed CHECK on `type`, the platform `CharacterActivity` enum with exactly the `ActivityType`
  values, and a private legacy reader for the old `activities` column until the gate, then drops it.
- `nx-social` - character detail renders the fishing block from the usage entity.

## Links

- Rides: [`runtime-sync`](006-runtime-sync.md); sibling: [`character-online-hero`](016-character-online-hero.md),
  [`character-core-extension`](013-character-core-extension.md)
- Sessions: [`038-app-offline-sessions`](038-app-offline-sessions.md)
- Platform: `nx-gameservers/docs/specs/086-offline-fishing.md`, `nx-gameservers/docs/specs/009-characters.md`
- Host source: not linked here, this repository does not name tenant hosts.
