# App offline sessions

> Owner: @n1rmata

## Problem

A player who is not in the game client wants their character to keep doing something in the world: stand in a private
store, fish. Today a private store opened from the app reuses the host's offline-trader mechanism, which is a
disconnected client with no notion of an app-driven lifecycle, a start/stop history, a report, or a second kind of
action. The platform needs one generic mechanism: a character is placed into the world without a client by an app
command, performs a typed action, and leaves when the action ends, with the platform seeing every start, end and
restart.

Offline fishing (see [`009-commands/offline-fishing.md`](009-commands/offline-fishing.md)) is the first new type and the
reason the mechanism is generalized; the private store from the app moves onto it.

## Requirements

> Siblings: [`037-schema-migrations`](037-schema-migrations.md) (owns the table), [`009-commands`](009-commands/spec.md)
> (rail, receipts), [`013-character-core-extension`](013-character-core-extension.md) (presence event),
> [`011-events-online-snapshot`](011-events-online-snapshot.md) (bucket `app`),
> [`017-character-runtime-activity`](017-character-runtime-activity.md) (activity usage, activity token).

**Must:**

- [todo] R1. The adapter MUST ship an app-session engine in `nx-gs-adapter-core`: a store (`OfflineSessionStore`, JDBC),
  `active_ms` accounting, the restore schedule after a restart, end-by-time, the events and the report. The host never
  writes SQL against the session table.
- [todo] R2. The adapter MUST define an SPI `OfflineSessionHost`, implemented by the host, with at least:
  `placeInWorld` (put the character on a spot of the session type), `removeFromWorld` (take it out and return it to
  its origin by the host's own rule), `startAction` / `stopAction` (run or stop the typed action, fishing for
  `FISHING`). The engine receives host callbacks: the action ended with a reason, and the character entered the game
  from a client.
- [todo] R3. Session types are an open vocabulary on the adapter side; this spec defines `FISHING` and `PRIVATE_STORE`.
  Further types (offline mail-style actions, attribute, enchant work) reuse the same engine.
- [todo] R4. A session MUST be a game session with `source = APP`: its `sessionId` is the session row's `id` (UUIDv7),
  carried by the presence events (R15) so the platform's session log needs no second identity.
- [todo] R5. Start MUST be idempotent by the command's `idempotencyKey` (stored as `idempotency_key`): a repeated command
  finds the session it already created and returns it instead of starting a second one.
- [todo] R6. At most one active session per character and at most one character per spot, enforced by the table (see
  Data model), not only by host checks.
- [todo] R7. `active_ms` MUST count time spent in the world without the restart downtime. It is flushed once a minute and
  on stop. End by time compares `active_ms` with `planned_ms`.
- [todo] R8. Business rules (who may start, the recently-left-the-game rule, cooldown, restart window, one-fishing limits,
  duration bounds, location checks, stock checks) live in the host and its configuration. The adapter does not know
  them; the host reports a refusal with an adapter reason code from the shared enum (see
  [`offline-fishing.md`](009-commands/offline-fishing.md)). The time a character left the game MUST be recorded on every
  disconnect of a live client, including the in-game command that leaves the character as an offline trader: that
  command only works in a trade zone, which is a peaceful town zone, so no special case or bypass exists.
- [todo] R9. A character logging in with a client MUST close its own session with reason `LOGIN`, under the character's
  mutation lock and before the live character is loaded, so the last action cycle finishes before the character leaves
  the world. Logging in with another character of the same account MUST NOT close the session. A login before the
  post-restart restore has reached the character cancels the session.
- [todo] R10. Login is never blocked by a session.
- [todo] R11. A session survives a restart as open: the engine restores it on the schedule below with the same `id`.
  Restore that cannot happen ends the session with `RESTORE_FAILED`. The platform's server-generation close does not
  touch `APP` sessions, and `SUPERSEDED` for an `APP` session has no generation boundary (a session lives across
  restarts). Reconciling `APP` sessions left open by mistake is a platform task (`nx-gameservers/docs/TODO.md` §72).
- [todo] R12. There are no dedicated session events. The existing `CharacterPresenceEvent` is the session event: the end
  of a session is `online = false` with `metadata.logout_reason` and an optional `report` block; a session restored
  after a restart is `online = true` with the same `sessionId` and `resumed = true` (R15).
- [todo] R13. Closed sessions older than the retention supplied by the host MUST be deleted at server start. The retention
  is a key of the host's app settings (R18), read by the engine through the `OfflineSessionHost` SPI.
- [todo] R18. A typed snapshot of the server's app settings MUST be published through a new sync family `settings`
  (see [App settings snapshot](#app-settings-snapshot)). The platform stores it per server.

**Non-goals:**

- Business rules, spot pools, titles, flags of the placed character: host and its configuration (R8).
- Frontend. The app screens consume the platform API.
- Trust levels for starting a session. [assumed: until a trust system exists, start is guarded by a permission on the
  platform side]. Trust is a separate item on the platform's backlog.
- Mail notifications for the session owner; today the platform shows "character not in game" as it does for offline
  stores.

### Edge cases

- **Start retried after a timeout:** same `idempotencyKey`, returns the existing session (R5).
- **Character already in a session or in the world with a client:** refused with the shared reason codes, not by a
  constraint violation reaching the caller.
- **Login mid-session:** R9; the closing event is still emitted (with the report, once the report design is approved).
- **Restart mid-session:** the session row stays open; downtime is not counted in `active_ms` (R7); on restore the engine
  emits the presence event with `resumed = true` (R12), the remainder continues.
- **Restored spot is taken, a free one exists:** the host places the character on a free spot of the same pool.
- **Restored spot is taken and none is free:** `RESTORE_FAILED`, the character returns to its origin.
- **Origin was a dangerous place** (jail, boss room, instance, olympiad): the origin recorded in the row is already
  replaced by a safe location before the session starts.
- **Action ended in the host** (out of bait, inventory full, ban, kick): the host calls back with the reason and the
  engine closes the session.
- **Session type disabled in the host's app settings:** running sessions finish normally, a new start is refused with
  `FEATURE_DISABLED` (see [App settings snapshot](#app-settings-snapshot)).
- **Client disconnect right before a start:** counts as leaving the game (R8), including the offline-trader command.
- **Duplicate stop / stop of a session that is not running:** idempotent, see [`offline-fishing.md`](009-commands/offline-fishing.md).

## Technical design

### Overview

A command reaches the host handler, which validates against the host's rules (R8) and asks the engine to start a
session of a type. The engine writes the row, calls `OfflineSessionHost.placeInWorld` and `startAction`, and from then
on owns the clock (`active_ms`), the restore after restarts and the end. Presence events (including the session end and
resume) go to the platform on the existing event rail.

### Structure

- `nx-gs-adapter-core` - the engine, `OfflineSessionStore`, the `OfflineSessionHost` SPI.
- `nx-gs-adapter-api` - session reason codes, presence additions, the settings snapshot DTO, `app` bucket,
  `offline_fishing` activity token, the commands.
- `nx-gs-db-migrations` - the changelog that creates `l2nx_offline_sessions` ([`037`](037-schema-migrations.md)).

### Key components

- **`OfflineSessionStore`** - JDBC access to the table, Java 8, runs on the adapter IO pool.
- **Engine** - lifecycle, `active_ms` flush, restore scheduling, end-by-time, presence event and report assembly.
- **`OfflineSessionHost`** (host-implemented) - places and removes the character, starts and stops the action, calls
  back into the engine; every rule and check sits behind it (R8).

### Data model

Table `l2nx_offline_sessions`, owned by the adapter and created by the changelog of
[`037`](037-schema-migrations.md).

| Column                 | Type                          | Purpose                                                                                                                                                       |
| ---------------------- | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`                   | `uuid` PK                     | UUIDv7 generated in Java; equals the platform `sessionId`                                                                                                     |
| `idempotency_key`      | `uuid` NOT NULL UNIQUE        | start idempotency: a repeated command finds the created session                                                                                               |
| `char_id`              | `int` NOT NULL                | character; history and rollback                                                                                                                               |
| `active_char_id`       | `int` NULL UNIQUE             | equals `char_id` while the session runs, `NULL` once closed: at most one active session per character (all three engines allow many `NULL` in a unique index) |
| `master_account_id`    | `uuid` NULL                   | master-account limit; taken from the command                                                                                                                  |
| `type`                 | `varchar(16)` NOT NULL        | `FISHING`, `PRIVATE_STORE`                                                                                                                                    |
| `account_name`         | `varchar(45)` NOT NULL        | per-account limit                                                                                                                                             |
| `hwid`                 | `varchar(300)` NULL           | snapshot of the character's hwid used for the one-fishing-per-computer limit, an empty value is stored as `NULL`; `NULL` means no per-device limit; kept for investigations |
| `initiator_ip`         | `varchar(45)` NULL            | IP of the app request that sent the start command (`initiatorIp`); for investigations                                                                         |
| `spot_pool`, `spot_id` | `varchar(32)`, `int` NOT NULL | the spot (fishing: pool `giran_harbor`, ids 1..1341; private store: a place of the trade zone)                                                                |
| `active_spot_key`      | `varchar(48)` NULL UNIQUE     | `<pool>:<spot>` while the session runs: one spot, one character                                                                                               |
| `origin_x/y/z/heading` | `int` NOT NULL                | where to return the character; a dangerous origin is already replaced by a town. `heading` is the facing direction (0-65535)                                  |
| `started_at`           | `datetime` NOT NULL           | real start (the character stood on the spot); used for the cooldown                                                                                           |
| `planned_ms`           | `bigint` NULL                 | the chosen duration; `NULL` for a private store                                                                                                               |
| `active_ms`            | `bigint` NOT NULL DEFAULT 0   | time in the world excluding restart downtime; drives end-by-time and the return-threshold rule                                                                     |
| `type_state`           | `clob` NULL                   | per-type JSON: fishing - rod, bait, what to return to the hands, interim catch; store - kind, lots, message, seller fee percent                               |
| `ended_at`             | `datetime` NULL               | `NULL` means active                                                                                                                                           |
| `end_reason`           | `varchar(32)` NULL            | end reason                                                                                                                                                    |
| `report_json`          | `clob` NULL                   | session report (initial design, see [Session report](#session-report---initial-design-not-approved-separate-design-session-pending)); sent in the end presence event and kept for history |

Indexes: `UNIQUE(active_char_id)`, `UNIQUE(active_spot_key)`, `UNIQUE(idempotency_key)`,
`(account_name, ended_at)`, `(hwid, ended_at)`, `(master_account_id, ended_at)`, `(char_id, started_at)`.

The seller fee of an app private store lives in `type_state` of its `PRIVATE_STORE` session: the host's trade list asks the
seller's session for the percent, so a sale pays the fee to any buyer, including a buyer in the game (stores opened in
the game pay no fee, as before). The separate per-character store-fee variables and the app-store origin marker are
removed.

Cleanup: closed sessions older than the host's retention are deleted at server start (R13).

### Lifecycle

1. Start: host validates (R8), engine inserts the row under `idempotency_key` (R5), `placeInWorld`, `startAction`.
2. Running: `active_ms` flushed every minute and on stop (R7); end-by-time when `active_ms >= planned_ms`.
3. End: by time, by stop command, by `LOGIN`, or by a host callback (action ended). The engine calls `stopAction` and
   `removeFromWorld`, fills `ended_at`, `end_reason`, `report_json`, clears `active_char_id` and `active_spot_key`,
   emits the end presence event.
4. Restart: rows with `ended_at IS NULL` are restored in batches after the configured delay (batch size and pace are
   host configuration), then a presence event with `resumed = true` is emitted per session.

End reasons (open vocabulary, values defined for the first two types):

- `FISHING`: `TIME_UP`, `STOPPED`, `LOGIN`, `OUT_OF_BAIT`, `DAILY_LIMIT`, `INVENTORY_FULL`, `BANNED`, `KICKED`,
  `RESTORE_FAILED`.
- `PRIVATE_STORE`: `SOLD_OUT`, `STOPPED`, `LOGIN`, `BANNED`, `KICKED`, `RESTORE_FAILED`.

On the wire each reason is its lower_snake token in `metadata.logout_reason` (R15).

### Contract additions (api, minor version, additions only)

- R14. `WellKnownServerOnlineBuckets` gains the bucket `app`; `WellKnownActivities` gains `offline_fishing`. The existing
  `offline_trade` activity is untouched and is also the activity token of an app private store: the session type tells
  them apart, and the check of offline-store places against the platform's usage works unchanged. See
  [`011`](011-events-online-snapshot.md) and [`017`](017-character-runtime-activity.md).
- R15. `CharacterPresenceEvent` gains:
  - `source` (`CLIENT` / `APP`; `null` reads as `CLIENT`) and `appType` (`FISHING` / `PRIVATE_STORE`, only with
    `source = APP`);
  - `sessionId` (the app session's id, only with `source = APP`);
  - `resumed` (`true` only on the `online = true` event of a session restored after a restart, same `sessionId`);
  - an optional `report` block on the end event (see [Session report](#session-report---initial-design-not-approved-separate-design-session-pending)).

  The end reason of an `APP` session rides the existing `metadata.logout_reason` (013 R14) as lower_snake tokens
  (`time_up`, `stopped`, `login`, `out_of_bait`, `daily_limit`, `inventory_full`, `sold_out`, `banned`, `kicked`,
  `restore_failed`); no new field. For an `APP` event `ip` is the `initiatorIp` of the start command (the app request
  IP) and `hwid` is the session's hwid snapshot (`null` when empty, never `""`); `source = APP` marks both as "from the
  app". Platform readers of "last game IP / HWID" take only `CLIENT` events; the session log shows both kinds with the
  mark. See [`013`](013-character-core-extension.md).
- R16. No new event families: presence is the only session event, so nx-social and the platform consume what they already
  consume.
- R17. The reason-code enum shared by the commands lives in the api (see
  [`offline-fishing.md`](009-commands/offline-fishing.md)); it includes `FEATURE_DISABLED` and `INVALID_DURATION`.

### App settings snapshot

Detail of R18. A game server's app behaviour is configured by the game designer in a settings file of the host (the
adapter does not prescribe its name or format; it is separate from the file with the adapter connection and credentials).
It holds which session types are enabled, the rule parameters (recently-left-the-game minutes, start cooldown, the return
threshold, restart window, restore delay and batch size, fishing minimum and maximum duration), the placed character's
title text and colour, and the retention of the host's session history. Spot pools are separate data files.

Host-side behaviour the contract relies on (host configuration, summarized because the snapshot follows it):

- The host watches the file and applies changes without a restart. It validates the whole file first: an error in any key
  applies nothing, the previous values keep working, the log names the key and the reason. On apply it logs the changed
  keys (old and new value) and publishes a fresh settings snapshot at once.
- Running sessions are not touched: new rules govern new starts. A disabled type lets running sessions finish and refuses
  new starts with `FEATURE_DISABLED`. This is a per-server product setting (the feature exists on some servers and not on
  others), not a feature flag.
- The file carries a header and a comment per key written for a non-technical reader (meaning, units, allowed values,
  example).

The snapshot is a new sync family `settings`: an entity with one row per server
[NEEDS CLARIFICATION: package, DTO name and whether it rides the runtime engine or a one-shot publish]:

| Part             | Content                                                                                                                                  |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| per session type | `type` (`FISHING`, `PRIVATE_STORE`, open) and `enabled`                                                                                  |
| rule parameters  | typed values the UI needs for hints: recently-left minutes, cooldown, return threshold, restart window, fishing minimum and maximum duration |

Published at adapter start and after every applied settings change; an unchanged snapshot is not republished. The
platform stores it per server (a row per kind, `app`, with the snapshot as JSON) so the app can grey out a disabled
type and show the thresholds. Fishing thresholds, the daily ceiling and the reset are not here: they travel in the
`usage` rows of each character ([`017`](017-character-runtime-activity.md)); they change only on a host restart, after
which the host republishes every usage row.

### Placed character (the "phantom" of the design)

A character loaded by the engine is called the app-loaded character; the design's "phantom" means exactly this: any
character placed in the world by an app command (fisher, app store, later mail/attributes/enchant). There is no separate
"stands while the player is in the app" kind. The generic wire contract is only what the platform must know: the
session, its type, its events. How the host renders and protects the placed character (title text and colour, display
only and not persisted; invulnerable; not a target; no trade, party, duel or summon; no online rewards; no "entered
game" / "disconnect" notifications) is host configuration and out of scope of this contract. Offline stores started in
the game (`offline_trade`) are not touched.

The host marks the placed character with its engine's offline-mode flag, so every existing exclusion of offline traders
(online rewards, AFK and captcha checks, olympiad, auto-farm and so on) applies for free. Places that read the flag as
"this is a store" (restoring offline traders, closing a store on a sell-out, the offline trade manager) tell the cases
apart by the session type.

## Session report - initial design, not approved (separate design session pending)

> Status: an initial sketch so the other specs have something to point at. It is NOT approved; the owner will run a
> separate design session. Do not implement from this section.

One general mechanism for any session, modeled on the "current session" window of the game client: the end presence
event may carry a `report` block.

- Totals: time in the world, experience, SP, adena change.
- Items: `{itemTemplateId, count, source}`; `source` is an open UPPER_SNAKE token (`FISHING`, `PICKUP`, `DROP`,
  `QUEST`, ...).
- Activity sections: `activities: [{type, ...}]`; fishing: catch by kind, casts. Fishing is marked by a `fishing`
  section plus `source = FISHING`; an offline fisher is recognised by the session's `appType = FISHING`.
- Today only offline fishing fills it. The report of a `CLIENT` session is a platform backlog item.
- Platform storage (summary): `gs_session_reports` (a row per session, totals and activity sections as JSON) and
  `gs_session_report_items` (item template by source, indexed by character and template), linked by `session_id`; kept
  as long as the sessions.
- The adapter keeps the same content in `report_json`.


## Rollout

Four phases, across repos:

1. **Adapter and platform, host untouched.** Adapter: migrations ([`037`](037-schema-migrations.md)), wrappers, session
   engine, SPI, contract, published to Maven Central. Platform: session `source`, `APP` filters, activity-usage storage
   reading both forms, `app` online bucket, offline-fishing commands answering `501`, notification filters, the
   permission granted to nobody.
2. **Host, first restart: app sessions.** Migrations through the adapter, private store from the app on sessions (hard
   cutover, no row migration: the old app-store marker and its character variables are removed, a leftover app-store row
   in the offline-trader table is restored as an ordinary offline store; host owners get an optional SQL to delete such
   rows before the restart), the recently-left-the-game rule, activity usage only, staff online window row.
3. **Host, second restart: offline fishing** with its datapack (spots, fishing and peace zone where needed); the
   permission goes to testers; the next morning restart verifies fisher restore. The host's app settings file and the
   settings snapshot (R18) ship with phase 2.
4. **Opening and cleanup, no host release:** permission to the player role, frontend, the platform drops the old
   `activities` branch once every game server of the tenant is on the phase-2 host, backlog by priority
   ([`TODO §7`](../TODO.md#7-recurringschedule-to-rrule) first).

**Straddle rule (contract host <-> platform).** The straddle lives only on the platform: the host switches to the new
shape in one release (it sends only the new form), the platform first reads both forms and, after the gate "all game
servers of the tenant are on the new host", deletes the old branch. The old branch is marked
`TODO: drop once ...` with a backlog item. The adapter follows the two-release deprecation rule of the root `CLAUDE.md`
where it removes an api member.

Each host phase goes to a test server first.

## Open questions

- [ ] [NEEDS CLARIFICATION: `OfflineSessionHost` method signatures and callback types]
- [ ] [NEEDS CLARIFICATION: package, DTO name and publish mechanism of the settings snapshot (R18)]
- [ ] [NEEDS CLARIFICATION: the session report (initial design only, owner's design session pending)]
- [ ] [assumed: the adapter exposes no REST/Kafka query for session history; the platform builds history from the
      events]

## Links

- Table migrations: [`037-schema-migrations`](037-schema-migrations.md)
- Commands: [`009-commands/offline-fishing.md`](009-commands/offline-fishing.md),
  [`009-commands/catalog.md`](009-commands/catalog.md)
- Events and buckets: [`013`](013-character-core-extension.md), [`011`](011-events-online-snapshot.md),
  [`017`](017-character-runtime-activity.md)
- Platform counterparts: `nx-gameservers/docs/specs/086-offline-fishing.md` (with updates to
  `043-sessions/spec.md`, `009-characters.md`, the online-snapshot spec and `052-private-store-start-commands.md`),
  `nx-social/docs/specs/056-fishing-notifications.md` (with `024-character-presence-notifications/spec.md`).
  The platform adds the permission `GS_SESSIONS_READ_OWN` (a player reads the session history and reports of their own
  characters).
- Host documentation: not linked here, this repository does not name tenant hosts.
