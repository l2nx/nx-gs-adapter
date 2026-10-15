# Character runtime activity (AI status + activity usage)

> Owner: @n1rmata

## Problem

The platform wants to show _"what is this character doing right now"_ on a live admin / dashboard view, and, since the
app can send a character fishing without a client ([`038`](038-app-offline-sessions.md)), also _"how much of each
activity has this character used and how much is left"_. Two distinct, **orthogonal** signals are wanted:

- **AI status** - the engine-native control intention the core puts a player character in (idle / moving / attack / cast /
  pick-up / interact / ...). Present in some form on essentially every L2 core.
- **Activity usage** - build-specific sustained activities and their consumption (fishing and its penalty clock,
  private store, auto-farm, ...) in one entity: what runs now, how much was used, how much is left, when it resets. The
  set varies per build; some servers do not ship fishing at all.

The design must stay **build-agnostic**: the wire contract and platform storage encode no core's activity vocabulary as a
first-class concept, and AI status tolerates cores whose intention set differs.

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
- [done] R3. Current activities MUST be a **list**: a character can be in several sustained activities at once (autofarming
  while fishing). Each entry is a thin agnostic envelope: required open `type` + optional open `Map<String,String>
metadata` (stringified values; durations ISO-8601). The list is independent of `aiStatus` (any combination legal, no
  precedence on the wire). Canonical `type` values live in `WellKnownActivities` (`fishing`, `reading`, `autofarming`,
  `automacro`, `trade`, `offline_trade`), canonical metadata keys in `WellKnownActivityMetadata`; both are open and a host
  may emit its own without an api release. This list is superseded by R11 (activity usage) on the wire; see R12 for the
  switch.
- [done] R4. The raw-seconds keys (`elapsed_seconds`, `seconds_remaining`, `seconds_to_next_tier`) were removed in
  `api/v0.93.1`; metadata durations are ISO-8601.
- [done] R5. The runtime mapping of the host MUST populate `aiStatus` and the current activities from live state, and the
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
  | `type`          | `String`                    | Open token; canonical values in `WellKnownActivities` plus whatever a host invents (open - see Open questions on casing) |
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

  Consumption and limits are named `usage`, not `quota`. A fishing row (live or offline) is also the source of the
  "fishing ended" notification: when its `activeSince` clears the platform notifies the player (for an offline session the
  end event's report is added, see [`038`](038-app-offline-sessions.md)). After a host restart (the fishing thresholds,
  daily ceiling and reset change only with the host's own configuration) the host republishes every usage row.

  The fishing penalty is one such row (`used` is the accumulated active fishing time, `steps` its tiers, `limit` the
  daily ceiling, `resetSchedule` the daily reset); a running fishing session fills `activeSince`.

- [todo] R12. `characteractivityusage` replaces `CharacterRuntimeDto.activities`. The **host switches in one release**: it
  sends only the new entity and no longer sends `activities`. The **platform straddles**: it reads both forms until every
  game server of the tenant is on the new host, then deletes the old branch (see [`038`](038-app-offline-sessions.md)
  Rollout). On the adapter side the api keeps `activities` `@Deprecated` with a Javadoc naming the replacement and the
  removal gate, and removes it in a later release, per the two-release rule of the root `CLAUDE.md`.
- [todo] R13. `resetSchedule` is an RRULE (RFC 5545), the platform canon for recurring schedules; one-off instants stay
  ISO-8601. Platform time is always UTC: the rule is written `DTSTART:<UTC>Z` + `RRULE:...` with no `TZID`. The host
  converts its own local server time to UTC (for example a daily reset at 06:00 on a UTC+3 server is
  `DTSTART:...T030000Z` + `RRULE:FREQ=DAILY`); if the host's clock offset changes, recomputing and republishing the
  rule is the host's job. Converting the older `RecurringSchedule` shapes to RRULE is deferred, see
  [`TODO §7`](../TODO.md#7-recurringschedule-to-rrule).
- [todo] R14. Platform storage MUST key the entity by `(server, character, type, key)` with `NULL`-safe uniqueness, and
  merge two sources by freshness: the runtime channel says what is running now (`activeSince`); a runtime tombstone
  clears only `activeSince`; db-sync supplies the spend of a character that is not in the world.
- [todo] R15. `WellKnownActivities` MUST gain `offline_fishing` (activity of an app-session fisher,
  [`038-app-offline-sessions`](038-app-offline-sessions.md)). The existing `offline_trade` is not touched and is also the
  activity token of an app private store: the session type tells them apart, and the platform's offline-store place check
  works unchanged.

**Should:**

- [done] R7. `GET /v1/gameservers/characters` exposes `aiStatus` and the activities (raw JSON passthrough); display only.
- [done] R8. The platform chat bot shows fishing info in the character view only while the character is fishing.

**Could:**

- [todo] R9. Typed metadata (promoting well-known keys to typed fields) - NOT shipped; the fully open `Map<String,String>`
  is the deliberate choice.
- [todo] R16. Tracking a character's presence inside instances as usage of type `INSTANCE` (`key` = instance template id),
  see [`TODO §9`](../TODO.md#9-instance-type-activity-usage).

**Non-goals:**

- **An `activity`/`fishing` enum on the wire or a CHECK-constrained column** - it would bake one core's vocabulary into a
  build-agnostic contract. Open string + `WellKnown*` constants is the choice.
- **A `FISHING` (or any activity) value on `aiStatus`** - fishing is not an engine intention; the fields are independent.
- **Coupling / precedence between `aiStatus` and the activities** on the wire or in the DB.
- **Filtering / sorting the character list by activity**, and **history / analytics of activity over time** (live-state
  overlay only).

### Edge cases

- **Core without fishing** - no `fishing` row until that core's mapping emits its own activity; no contract change.
- **Unknown `aiStatus`, activity `type` or metadata key at the consumer** - stored/displayed raw, no behavior gated on it.
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
- [resolved: stored opaque; the platform never models per-activity keys.]
- [resolved: two independent fields, not one unified "state".]
- [resolved: activity and usage are one entity, `characteractivityusage`, not a second parallel list.]
- [ ] [NEEDS CLARIFICATION: Java type and package of the usage entity and whether it is one DTO for runtime and db-sync
      or two]
- [ ] [NEEDS CLARIFICATION: `type` token casing - existing `WellKnownActivities` values are lower_snake_case
      (`offline_trade`), the repo naming rule for enum-like vocabularies is `UPPER_SNAKE_CASE`, and the backlog item names
      the type `INSTANCE`]
- [resolved: `steps[].after` is measured in the row's `unit`.]
- [resolved: `remaining` is `limit - used`, floored at zero, when `limit` is set; `null` when `limit` is `null`.]

## Versioning

- `nx-gs-adapter-api` - **minor** bump, additive: the usage entity and `WellKnownActivities.offline_fishing`;
  `CharacterRuntimeDto.activities` marked `@Deprecated` (R12), removed in a later breaking release once the gate has
  fired.
- `nx-gs-adapter-core` / `nx-gs-db-sync-core` / `nx-gs-runtime-sync-core` - the usage entity rides the existing engines
  (the runtime engine hashes whatever the mapping mixes).
- The host: sends the usage entity only, from the switch release on.
- `nx-gameservers` - `gs_character_activity_usages`, a reader for both forms until the gate, then drops the old column.
- `nx-social` - character detail renders the fishing block from the usage entity.

## Links

- Rides: [`runtime-sync`](006-runtime-sync.md); sibling: [`character-online-hero`](016-character-online-hero.md),
  [`character-core-extension`](013-character-core-extension.md)
- Sessions: [`038-app-offline-sessions`](038-app-offline-sessions.md)
- Pattern precedent: [`events-raid`](014-events-raid.md) (open string + `WellKnown*`)
- Platform: `nx-gameservers/docs/specs/086-offline-fishing.md`, `nx-gameservers/docs/specs/009-characters.md`
- Host source: not linked here, this repository does not name tenant hosts.
