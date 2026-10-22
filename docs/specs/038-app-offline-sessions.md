# App offline sessions

> Owner: @n1rmata

## Problem

A player who is not in the game client wants their character to keep doing something in the world: stand in a private
store, fish. Today a private store opened from the app reuses the host's offline-trader mechanism, which is a
disconnected client with no notion of an app-driven lifecycle, a start/stop history, or a second kind of action. The
platform needs one generic mechanism: a character is placed into the world without a client by an app command, performs a
typed action, and leaves when the action ends, with the platform seeing every start, end and restart.

Offline fishing (see [`009-commands/offline-fishing.md`](009-commands/offline-fishing.md)) is the first new type and the
reason the mechanism is generalized; the private store from the app moves onto it.

## Requirements

> Siblings: [`037-schema-migrations`](037-schema-migrations.md) (owns the table), [`009-commands`](009-commands/spec.md)
> (rail, receipts), [`013-character-core-extension`](013-character-core-extension.md) (presence event),
> [`011-events-online-snapshot`](011-events-online-snapshot.md) (buckets `APP`, `OFFLINE_FISHING`),
> [`017-character-runtime-activity`](017-character-runtime-activity.md) (activity usage, `ActivityType`).

**Must:**

- [done] R1. The adapter MUST ship an app-session engine in `nx-gs-adapter-core`: a store (`OfflineSessionStore`, JDBC),
  `active_ms` accounting, the restore schedule after a restart, end-by-time and the events. The host never writes SQL
  against the session table.
- [done] R2. The adapter MUST define an SPI `OfflineSessionHost`, implemented by the host: `placeInWorld` (put the
  character on a spot of the session type), `removeFromWorld` (take it out and, if the session lasted less than the return
  threshold, return it to its origin), `startAction` / `stopAction` (run or stop the typed action, fishing for
  `OFFLINE_FISHING`) and the settings the engine needs. The engine receives host callbacks through the capability
  `NxAppSessions`: the action ended with a reason, and the character entered the game from a client. Shapes in
  [Key components](#key-components).
- [done] R3. A session's type is its `ActivityType` (see [`017`](017-character-runtime-activity.md)), a closed set: this
  spec defines `OFFLINE_FISHING` and `OFFLINE_PRIVATE_STORE` (the app private store). Further types (offline mail-style actions,
  attribute, enchant work) reuse the same engine and each first adds its `ActivityType` value in the api (the
  `OFFLINE_<ACTION>` naming also names its online bucket, see [`011`](011-events-online-snapshot.md)).
- [done] R4. A session MUST be a game session with `source = APP`: its `sessionId` is the session row's `id` (UUIDv7),
  carried by the presence events (R15) so the platform's session log needs no second identity.
- [done] R5. Start MUST be idempotent by the command's `idempotencyKey` (stored as `idempotency_key`): a repeated command
  finds the session it already created and returns it instead of starting a second one.
- [done] R6. At most one active session per character and at most one character per spot, enforced by the table (see
  Data model), not only by host checks.
- [done] R7. `active_ms` MUST count time spent in the world without the restart downtime. It is flushed once a minute and
  on stop. End by time compares `active_ms` with `planned_ms`.
- [host rule] R8. Business rules (who may start, the recently-left-the-game rule, cooldown, restart window, one-fishing limits,
  duration bounds, location checks, stock checks) live in the host and its configuration. The adapter does not know
  them; the host reports a refusal with an adapter reason code from the shared enum (see
  [`offline-fishing.md`](009-commands/offline-fishing.md)). The time a character left the game MUST be recorded on every
  disconnect of a live client, including the in-game command that leaves the character as an offline trader: that
  command only works in a trade zone, which is a peaceful town zone, so no special case or bypass exists.
- [wip] R9. A character logging in with a client MUST close its own session with reason `APP_LOGIN`, under the character's
  mutation lock and before the live character is loaded, so the last action cycle finishes before the character leaves
  the world. Logging in with another character of the same account MUST NOT close the session. A login before the
  post-restart restore has reached the character cancels the session. Adapter side done
  (`NxAppSessions.onClientLogin`); the host's login path calls it in phase 2.
- [wip] R10. Login is never blocked by a session for good: at worst the engine's bounded wait runs out and the host
  refuses that one attempt with "try again" (`ClientLoginOutcome.PENDING`) while the close finishes in the background.
- [done] R11. A session survives a restart as open: the engine restores it on the schedule below with the same `id`.
  Restore that cannot happen ends the session with `APP_RESTORE_FAILED`. The platform's server-generation close does not
  touch `APP` sessions, and `SUPERSEDED` for an `APP` session has no generation boundary (a session lives across
  restarts). Reconciling `APP` sessions left open by mistake is a platform task ([nx-gameservers#85](https://github.com/l2nx/nx-gameservers/issues/85)).
- [done] R12. There are no dedicated session events. The existing `CharacterPresenceEvent` is the session event: the end
  of a session is `online = false` with the required `logoutReason` (R15); a session restored after a restart is
  `online = true` with the same `sessionId` and `resumed = true` (R15).
- [done] R13. Closed sessions older than the retention supplied by the host MUST be deleted at server start. The retention
  is a key of the host's app settings (R18), read by the engine through the `OfflineSessionHost` SPI.
- [done] R18. A typed snapshot of the server's app settings MUST be published as the first entity of the shared settings
  topic (see [App settings snapshot](#app-settings-snapshot)). The platform stores it per server.

**Non-goals:**

- Business rules, spot pools, titles, flags of the placed character: host and its configuration (R8).
- Frontend. The app screens consume the platform API.
- Trust levels for starting a session. [assumed: until a trust system exists, start is guarded by a permission on the
  platform side]. Trust is a separate item on the platform's backlog.
- The session report: an initial design only, not approved and not implemented in phase 1 (see
  [Session report](#session-report---initial-design-not-approved-separate-design-session-pending); [`TODO §10`](../TODO.md#10-session-report-design), the report of a client session is [`TODO §11`](../TODO.md#11-report-of-a-client-session)).
- Fishing notifications ("fishing ended", "resumed"): a separate task, initial design deferred in
  `nx-social/docs/specs/056-fishing-notifications.md` (nx-social TODO §32). Only the `source = APP` filter for the "entered
  game" / "disconnect" notifications is in this iteration.
- Mail notifications for the session owner; today the platform shows "character not in game" as it does for offline
  stores.

### Edge cases

- **Start retried after a timeout:** same `idempotencyKey`, returns the existing session (R5).
- **Character already in a session or in the world with a client:** refused with the shared reason codes, not by a
  constraint violation reaching the caller.
- **Login mid-session:** R9; the closing event is still emitted.
- **Restart mid-session:** the session row stays open; downtime is not counted in `active_ms` (R7); on restore the engine
  emits the presence event with `resumed = true` (R12), the remainder continues.
- **Restored spot is taken, a free one exists:** the host places the character on a free spot of the same pool.
- **Restored spot is taken and none is free:** `APP_RESTORE_FAILED`, the session ends and the return rule below applies.
- **Stop while the session is open but the post-restart restore has not reached the character yet:** the engine cancels
  the restore and closes the session with `APP_STOPPED_BEFORE_RESTORE` (the character never re-entered the world); the
  return rule applies.
- **Stop while the server is down:** the stop never reaches the host (the platform answers `409 SERVER_OFFLINE` before
  dispatch), nothing changes, the session stays open and is restored after the start.
- **Return rule (any session type, any end reason):** when a session ends, the character returns to its origin only if
  `active_ms` is below the return threshold from the host's app settings (one threshold for all session types, the same
  for fishing and private store); at the threshold or longer it stays where it is (the fishing spot, the store spot).
- **Origin was a dangerous place** (jail, boss room, instance, olympiad): the origin recorded in the row is already
  replaced by a safe location before the session starts.
- **Action ended in the host** (out of bait, inventory full, ban, kick): the host calls back with the reason and the
  engine closes the session.
- **Session type disabled in the host's app settings:** running sessions finish normally, a new start is refused with
  `FEATURE_DISABLED` (see [App settings snapshot](#app-settings-snapshot)).
- **Client disconnect right before a start:** counts as leaving the game (R8), including the offline-trader command.
- **Duplicate stop / stop of a session that is not running:** idempotent success `{sessionId: null, stopped: false}`, see
  [`offline-fishing.md`](009-commands/offline-fishing.md).

## Technical design

### Overview

A command reaches the host handler, which validates against the host's rules (R8) and asks the engine to start a
session of a type. The engine writes the row, calls `OfflineSessionHost.placeInWorld` and `startAction`, and from then
on owns the clock (`active_ms`), the restore after restarts and the end. Presence events (including the session end and
resume) go to the platform on the existing event rail.

### Structure

- `nx-gs-adapter-api` - the SPI `OfflineSessionHost` (`spi.provider`), the capability `NxAppSessions`
  (`spi.capability`), their value types `OfflineSession`, `OfflineSessionPlacement`, `OfflineSessionSettings`,
  `AppSessionStartRequest`, `AppSessionStartOutcome` (`spi.model`); session reason codes, presence additions (`source`,
  `activity`, `logoutReason`, `sessionId`, `resumed`), the closed enums `ActivityType`, `ServerOnlineBucket` and
  `LogoutReason`, the settings snapshot DTO, the commands.
- `nx-gs-adapter-core` - the engine (package `core.appsessions`): `AppSessionEngine`, `JdbcOfflineSessionStore`,
  `AppSettingsPublisher`, the built-in module `AppSessionsModule` and the JVM-wide holder `AppSessionsRuntime`.
- `nx-gs-db-migrations` - the changelog that creates `l2nx_offline_sessions` ([`037`](037-schema-migrations.md)).

### Key components

**`OfflineSessionHost`** (api `spi.provider`, host-implemented, `ServiceLoader`; none leaves the engine disabled with an
INFO, two put it into `FAILED`):

```java
OfflineSessionPlacement placeInWorld(OfflineSession session); // placed(spotId[, typeState]) | refused(reason, detail) | refusedForNow(reason, detail)
void startAction(OfflineSession session);                      // throw = the action could not start
void stopAction(OfflineSession session);                       // every end of a started action; must be idempotent
void removeFromWorld(OfflineSession session, boolean returnToOrigin);
OfflineSessionSettings settings(); // returnThresholdMs, retentionDays, restoreBatchSize, restoreBatchIntervalMs
```

- The engine calls every method from an adapter IO thread and waits; the host hops to its game thread itself and runs
  inline when it is already there. Calls for one session never overlap. `updateTypeState` called on the engine's thread
  inside one of these calls runs inline: queued behind the running call it would wait for itself. From a thread the call
  hopped to it is queued, so that thread must not wait for it.
- `placeInWorld` places on the session's spot; on a restore the spot may be taken and the host may choose another free
  spot of the same pool (the engine moves `spot_id` / `active_spot_key`; a conflict there ends the restore with
  `APP_RESTORE_FAILED`).
- What the placement changed in the type state (a spot taken, a fee paid, lots gone during the downtime) comes back as
  `placed(spotId, typeState)`. The engine stores it in the same write as the spot, so `startAction` already sees it and
  a restart in between cannot lose it; `null` keeps the stored type state.
- `refusedForNow` is a refusal expected to pass within seconds (another operation holds the character). A start reports
  it like `refused`; a restore puts the session back at the end of the queue and tries again with a later batch, at
  most `RESTORE_ATTEMPTS` (5) times, then ends it with `APP_RESTORE_FAILED`.
- `removeFromWorld` covers three cases: the app-loaded character in the world (move to the origin when asked, store, take
  out); a character that is not in the world (restore failed or never happened, login before the restore: only the
  stored location changes, to the origin when asked); a character played with a client is never touched.
- `settings()` is read on every use, so a reloaded host settings file applies without a restart; when it throws the
  engine keeps the last good value.
- The SPI receives an immutable `OfflineSession` view: id, idempotency key, character, activity, account name, master
  account, hwid, initiator IP, spot, origin, `startedAt`, `plannedMs`, `activeMs` (as of the call), `typeState`, and
  `endedAt` / `endReason` once closed.

**`NxAppSessions`** (api `spi.capability`, the engine as the host sees it). Reached through
`CommandContext.appSessions()`, `ConnectContext.appSessions()` and the static `NxAdapter.appSessions()`; all three are
one instance, usable from JVM start; a context built without it gets a no-op. Nothing throws into the caller.

```java
CompletableFuture<AppSessionStartOutcome> start(AppSessionStartRequest request);
CompletableFuture<Optional<OfflineSession>> stop(int characterId, ActivityType activity);
void onActionEnded(UUID sessionId, LogoutReason reason);   // async; APP_OUT_OF_BAIT, APP_DAILY_LIMIT, APP_INVENTORY_FULL, APP_SOLD_OUT, BANNED, KICKED
void hostReady();                                          // the world is loaded; releases the restore, idempotent
ClientLoginOutcome onClientLogin(int characterId);         // synchronous, bounded wait: CLOSED(session) | NONE | PENDING
Optional<OfflineSession> findActive(int characterId);      // in-memory, game-thread safe
CompletableFuture<Boolean> updateTypeState(UUID sessionId, @Nullable String typeStateJson);
void publishSettings(AppSettings settings);
```

- `start` outcomes: `STARTED`, `EXISTING` (same `idempotencyKey`, open or closed), `CHARACTER_BUSY` (another open session
  of the character, from memory or from the table's `UNIQUE(active_character_id)`, including one whose end is still being
  retried against the table), `SPOT_TAKEN` (`UNIQUE(active_spot_key)`), `PLACEMENT_REFUSED` (the host's reason),
  `UNAVAILABLE` (engine not running, database error, `startAction` threw). Only a unique-key violation is classified
  (PostgreSQL SQLState `23505`, MySQL / MariaDB vendor error `1062`), never surfaced as SQL; any other integrity error is
  a failure (`UNAVAILABLE`). A start that does not reach `STARTED` publishes nothing, a character already placed goes
  back to its origin, and its row is deleted. A delete that fails is retried in the background (see Engine); until it
  succeeds the character counts as busy, and if the JVM stops first the row is restored as an open session at the next
  start.
- `AppSessionStartRequest` checks the columns' lengths, requires a session activity (`OFFLINE_FISHING`, `OFFLINE_PRIVATE_STORE`;
  a new session type adds its value there), turns blank optional strings into `null`. Business rules stay in the host.
- `stop` takes the activity so the stop command of one session type cannot close another; no open session of that
  activity is an empty result (the handler answers `{sessionId: null, stopped: false}`).
- `onClientLogin` waits for the close (at most 15 s); before the engine has booted it waits for the boot (at most 30 s).
  `CLOSED` and `NONE` let the login go on. `PENDING` means a wait ran out (or the waiting thread was interrupted): the
  host refuses this login with a "try again" message, and the close goes on in the background (after a boot wait, as
  soon as the engine is ready), so the next attempt finds the session closed. It must not run on the thread the host's
  SPI hops to unless those hops run inline.
- `hostReady` is the host's signal that its world is loaded and can take characters back; the host calls it once, after
  its own offline characters are back, so app sessions come back right behind them. It returns at once from any thread;
  a call before the engine has booted (the host may load its world before the platform connect) is remembered and
  forwarded when the engine is ready; a second call does nothing. Without it pending sessions are never restored (see
  Lifecycle, Restart).
- `findActive` also returns, until the retry reaches the table, a session the engine ended (carrying `endedAt`) or a
  start it rolled back whose write the table refused; the character stays busy for starts meanwhile.
- Futures complete on adapter threads; a handler waits on its own thread, never on `ctx.io()`.

**Engine** (`AppSessionEngine`). Every operation of one session (start, restore, end, type state) runs on that session's
serial executor over the adapter IO pool, so a session's operations never overlap and no lock is held while the host or
the database works. `active_ms` counts on a monotonic clock only while the action runs.

All engine work shares one bounded executor that holds at most half of the adapter IO workers (`l2nx.io.workers / 2`,
at least one), because each task blocks on the host or the database and the pool also serves every other module: a
restore of many sessions or a mass end never takes the whole pool. The host's `restoreBatchSize` is capped at that
limit.

A close or a rolled-back start whose write the table refuses is kept in a retry queue (with the original `ended_at`,
`end_reason` and `active_ms`) and retried on every flush tick, before each restore batch and once at shutdown, until it
succeeds. While it is queued the character counts as busy: `start` answers `CHARACTER_BUSY` (or `EXISTING` for the same
`idempotencyKey` of an ended session) and `findActive` returns it. A restore never runs for a character with a queued
end. If the JVM stops before the retry succeeds, the row is still open in the table and the next start restores it; the
end event already went out, so the platform sees that session resumed. That is accepted: the alternative, losing a
character that the table says is in the world, is worse, and the window needs a database outage that outlasts the
retries and a stop.

**`OfflineSessionStore`** (`JdbcOfflineSessionStore`). Writes through the one host `JdbcConnectionSource`: each call
borrows a connection, makes it writable and transactional and hands it back in the modes it was lent with (so the
source's database user needs write rights on `l2nx_*`, see [`004`](004-jdbc-connection-source.md) R3). The
changelog's `uuid` maps to a native `UUID` on PostgreSQL (bound as an object) and to text on MariaDB and MySQL (bound
as a string); `datetime` columns hold UTC written and read through an explicit UTC calendar; instants are stored with
second precision because MariaDB and MySQL `DATETIME` keep no fraction. Rows written by a newer adapter (an unknown
`activity` or `end_reason`) are skipped with an ERROR.

**`AppSessionsModule`** (`app-sessions`, built into core, registered in core's
`META-INF/services/...AdapterModule`). `onConnect` binds the presence events and the settings topic; `start` arms the
restore and the flush; `stop` stores `active_ms`. Its order against `db-sync`, `gd-sync`, `runtime-sync` and host modules
does not matter: it reads only its own context and the restore waits for `hostReady`, not for the module order.

**Restore trigger.** The engine keeps two one-way flags, "module started" (set by `AppSessionsModule.start()`) and "host
ready" (set by `NxAppSessions.hostReady()`); whichever is set last queues the pending sessions and schedules the first
batch at once, and a third flag lets only one of the two racing callers do so. The module starts before the host has
loaded its world, so the restore cannot follow the module start alone: a timer would be a guess, and a slow boot would
place characters into a world not yet loaded.

**`AppSessionsRuntime`** boots the engine on the adapter's connect thread right after the schema migration and before
the platform connect: it discovers the SPI and the connection source, deletes closed sessions past the retention and
loads the open ones. A login is therefore answered while the platform is down; presence events produced before the
connect are buffered and published on connect. Disabled adapter, login server or failed migration: no engine, the
capability starts nothing and finds nothing.

**`AppSettingsPublisher`** sends `SettingsSyncEvent<AppSettings>` to `syncTopics.settings["appsettings"]`
(`<tenant>.gs.settings.sync`), key = the raw 16-byte `serverId` (as on the gd stream), header `Nx-Message-Type =
SettingsSyncEvent`. A snapshot published before the connect is sent on connect; the last one is sent again after every
reconnect; a snapshot equal to the last sent one apart from `updatedAt` is not sent again (a failed send clears that
memory); a missing topic is a single WARN and the snapshot is skipped.

**Threads.** `nx-app-sessions-scheduler` (one daemon thread) runs the restore batches, the end-by-time timers and the
one-minute flush tick; all JDBC and SPI calls hop from it to `nx-io-N`, at most half of those workers at once.
`OfflineSessionHost.settings()` is the one SPI method also called from the scheduler thread and from the thread that
starts the modules, so it must answer at once without hopping to the game thread.

### Data model

Table `l2nx_offline_sessions`, owned by the adapter and created by the changelog of
[`037`](037-schema-migrations.md).

| Column                 | Type                          | Purpose                                                                                                                                                                                                        |
| ---------------------- | ----------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`                   | `uuid` PK                     | UUIDv7 generated in Java; equals the platform `sessionId`                                                                                                                                                      |
| `idempotency_key`      | `uuid` NOT NULL UNIQUE        | start idempotency: a repeated command finds the created session                                                                                                                                                |
| `character_id`         | `int` NOT NULL                | character; history and rollback                                                                                                                                                                                |
| `active_character_id`  | `int` NULL UNIQUE             | equals `character_id` while the session runs, `NULL` once closed: at most one active session per character (all three engines allow many `NULL` in a unique index)                                             |
| `master_account_id`    | `uuid` NULL                   | master-account limit; taken from the command                                                                                                                                                                   |
| `activity`             | `varchar(32)` NOT NULL        | the session's `ActivityType`: `OFFLINE_FISHING`, `OFFLINE_PRIVATE_STORE` (app private store)                                                                                                                   |
| `account_name`         | `varchar(45)` NOT NULL        | per-account limit                                                                                                                                                                                              |
| `hwid`                 | `varchar(300)` NULL           | snapshot of the character's hwid used for the one-fishing-per-computer limit, an empty value is stored as `NULL`; `NULL` means no per-device limit; kept for investigations                                    |
| `initiator_ip`         | `varchar(45)` NULL            | IP of the app request that sent the start command (`initiatorIp`); for investigations                                                                                                                          |
| `spot_pool`, `spot_id` | `varchar(32)`, `int` NOT NULL | the spot: a pool name and an id inside it, both chosen by the host (fishing: a pool of fishing spots; private store: a place of a trade zone)                                                                  |
| `active_spot_key`      | `varchar(48)` NULL UNIQUE     | `<pool>:<spot>` while the session runs: one spot, one character                                                                                                                                                |
| `origin_x/y/z/heading` | `int` NOT NULL                | where to return the character; a dangerous origin is already replaced by a town. `heading` is the facing direction (0-65535)                                                                                   |
| `started_at`           | `datetime` NOT NULL           | real start (the character stood on the spot); used for the cooldown                                                                                                                                            |
| `planned_ms`           | `bigint` NULL                 | the chosen duration; `NULL` for a private store                                                                                                                                                                |
| `active_ms`            | `bigint` NOT NULL DEFAULT 0   | time in the world excluding restart downtime; drives end-by-time and the return rule (compared with the one return threshold shared by all session types)                                                      |
| `type_state`           | `clob` NULL                   | per-type JSON: fishing - rod, bait, what to return to the hands, interim catch; store - kind (`SELL` / `PACKAGE_SELL` / `BUY`), lots, message, seller fee percent (`BUY`: lots by template, buyer fee percent) |
| `ended_at`             | `datetime` NULL               | `NULL` means active                                                                                                                                                                                            |
| `end_reason`           | `varchar(32)` NULL            | end reason: the `LogoutReason` constant name                                                                                                                                                                   |

Indexes: `UNIQUE(active_character_id)`, `UNIQUE(active_spot_key)`, `UNIQUE(idempotency_key)`,
`(account_name, ended_at)`, `(hwid, ended_at)`, `(master_account_id, ended_at)`, `(character_id, started_at)`,
`(ended_at)` (the retention delete at engine start).

The seller fee of an app private store lives in `type_state` of its `OFFLINE_PRIVATE_STORE` session: the host's trade list asks the
seller's session for the percent, so a sale pays the fee to any buyer, including a buyer in the game (stores opened in
the game pay no fee, as before). The host keeps no separate per-character copy of the fee or of the app-store origin.

- [todo] R19. A buy store started from the app (`StartPrivateStoreBuyCommand`, see [`catalog.md`](009-commands/catalog.md))
  is an `OFFLINE_PRIVATE_STORE` session like the sell stores. Its `type_state` carries store kind `BUY`, the lots by
  template (`itemTemplateId`, `count`, `priceAdena`; a buy lot has no item instance) and
  `buyerFeePercent`. The buyer fee lives in `type_state` for the same reason as the seller fee: the host's trade list
  asks the owner's session, so every fill pays it, a seller in the game included. `count` is
  what is still wanted. The session hooks branch on the store kind: a fill decrements the buy lots from the buy list, and
  the store is sold out when the buy list is empty after a fill (not "sell list empty"); the fee lookup, the `type_state`
  validity check, the slot limit at start and restore (the buy-store limit for `BUY`) and the store-type token are per kind
  too. The restore re-creates the buy list from `type_state` with the remaining counts, all-or-nothing like a sell store,
  and does not re-check adena. When every wanted count is filled the host reports `APP_SOLD_OUT` through `onActionEnded`
  and the session ends, as a fully sold sell store does. Adena is not reserved: every fill re-checks the owner's adena
  (price plus fee), a fill the owner cannot cover is refused and the store stays open. A fill of an app buy store is
  reported with the purchase-event `source` `CLIENT`: there is no app command that sells into a store.

Decision 2026-10-15: starting a private store from the app is allowed ONLY for a character that is NOT in the game. A
character online with a client is refused with `CHARACTER_ONLINE`; the recently-left-the-game rule (minutes from the
host's app settings file, default 2; `RECENTLY_LOGGED_OUT` plus `retryAt`) applies to the store start exactly as to
fishing. There is no online start path for app stores, so every app-started store is an app `OFFLINE_PRIVATE_STORE` session and
the seller fee always has a session to live in. The stop command closes ANY private store of the player's own character:
for an app session it closes the session (reason `APP_STOPPED`) and applies the return rule; a native `.offline` store or a
live player's store is just closed, as today. An app private store and a `.offline` store share the activity
`OFFLINE_PRIVATE_STORE`; `source` tells them apart, and `.offline` stores are not sessions.

Cleanup: closed sessions older than the host's retention are deleted at server start (R13).

### Lifecycle

1. Start: host validates (R8), engine inserts the row under `idempotency_key` (R5, id = UUIDv7), `placeInWorld`,
   records the real `started_at` (and the host's spot and type state), `startAction`, then publishes `online = true, source = APP,
activity, sessionId, ip = initiatorIp, hwid, accountName`.
2. Running: `active_ms` counts while the action runs, flushed every minute (a write never lowers the stored value and
   never touches a closed row) and on stop and shutdown (R7); a timer ends the session when `active_ms >= planned_ms`
   (`planned_ms` null: no time limit).
3. End: by time (`APP_TIME_UP`), by stop command (`APP_STOPPED`), by `APP_LOGIN`, or by a host callback (action ended). The engine
   calls `stopAction` (when the action ran) and `removeFromWorld(returnToOrigin = active_ms < threshold)`, fills
   `ended_at` and `end_reason`, clears `active_character_id` and `active_spot_key`, publishes `online = false, source = APP,
logoutReason`. A failed close in the table is logged and retried until the table takes it (the character stays busy
   meanwhile); the event goes out at once. Only if the JVM stops before a retry succeeds is the row, still open, restored
   at the next start (see Engine).
4. Server stop: running sessions keep their row open; `active_ms` is stored; nothing is published.
5. Restart: at engine start closed rows past the retention are deleted and rows with `ended_at IS NULL` wait for the
   restore. Once the `app-sessions` module has started and the host has called `hostReady` (whichever comes last), the
   first batch runs at once and the rest follow in batches at the host's pace (`restoreBatchSize`, capped at the
   engine's share of the IO pool, every `restoreBatchIntervalMs`; the start of the restore logs the effective pace):
   `placeInWorld` (+ the new spot and type state when the host changed them), `startAction`, then `online = true,
resumed = true` with the same `sessionId`. A placement refused for now goes back to the queue (see the SPI). Any
   other refused or failed placement or action ends the session with `APP_RESTORE_FAILED` (return rule applies, `removeFromWorld` of a
   character that never re-entered the world). A row whose `active_ms` already reached `planned_ms` ends with `APP_TIME_UP`
   without being placed. A stop before the restore reached it ends with `APP_STOPPED_BEFORE_RESTORE`, a login with `APP_LOGIN`;
   either cancels its restore. Until `hostReady` every pending session stays in that state, with no timeout; stops and
   logins keep closing them this way. Ends still queued for a retry are written before the next restore batch, and a
   character with such an end is never restored.

End reasons (the `LogoutReason` values of app sessions; the set is closed):

- `OFFLINE_FISHING`: `APP_TIME_UP`, `APP_STOPPED`, `APP_STOPPED_BEFORE_RESTORE`, `APP_LOGIN`, `APP_OUT_OF_BAIT`, `APP_DAILY_LIMIT`,
  `APP_INVENTORY_FULL`, `BANNED`, `KICKED`, `APP_RESTORE_FAILED`.
- `OFFLINE_PRIVATE_STORE` (app store): `APP_SOLD_OUT`, `APP_STOPPED`, `APP_STOPPED_BEFORE_RESTORE`, `APP_LOGIN`, `BANNED`, `KICKED`,
  `APP_RESTORE_FAILED`.

On the wire each reason is the enum constant name in the required `logoutReason` field (R15).

### Contract additions (api)

All of this ships in one api release together with the removal of the shapes it replaces (see the root `CLAUDE.md` ›
breaking wire changes): the adapter keeps no `@Deprecated` copy. The straddle for hosts that have not restarted yet
lives on the platform, in its own private legacy mapping (see Rollout).

- R14. Three closed enums, constant name = wire value (`UPPER_SNAKE_CASE`):
  - `ActivityType`: `FISHING`, `READING`, `AUTOFARMING`, `AUTOMACRO`, `PRIVATE_STORE`, `OFFLINE_PRIVATE_STORE`, `OFFLINE_FISHING`; used by
    `characteractivityusage.type` and the presence `activity` (see [`017`](017-character-runtime-activity.md));
  - `ServerOnlineBucket`: `TOTAL`, `UNIQUE`, `OFFLINE_PRIVATE_STORE`, `FISHING`, `APP`, `OFFLINE_FISHING` (see
    [`011`](011-events-online-snapshot.md));
  - `LogoutReason`: every reason of R15.

  Removed in the same release: `WellKnownActivities`, `CharacterRuntimeDto.activities`, the string-typed `Activity`
  model class, `WellKnownServerOnlineBuckets`, and the `logout_reason` constants of `WellKnownPresenceMetadata`.
  Hosts cannot invent tokens: a new activity, bucket or reason is an api release first, then the platform enum and
  CHECK, then the host.

- R15. `CharacterPresenceEvent` gains:
  - `source` (`CLIENT` / `APP`, read leniently with `SessionSource.fromToken`; the purchase-event `source` metadata uses the
    same vocabulary and its change from `in_game` / `remote` is a breaking wire change the platform straddles until every
    game server restarted), REQUIRED on every presence event; the host sets `CLIENT` for a client session, there
    is no "null reads as CLIENT" in this contract;
  - `activity` (`ActivityType`), only with `source = APP`: `OFFLINE_FISHING` or `OFFLINE_PRIVATE_STORE`;
  - `sessionId` (the app session's id, only with `source = APP`);
  - `resumed` (`true` only on the `online = true` event of a session restored after a restart, same `sessionId`);
  - `logoutReason` (`LogoutReason`), REQUIRED on every `online = false` event, a plain logout included:
    - client: `LOGOUT`, `DISCONNECT` (involuntary connection loss: network drop, client crash, DDoS), `OFFLINE_PRIVATE_STORE`, `SERVER_STOP`;
    - common to client and app sessions: `BANNED`, `KICKED` (a client session reports a kick or a ban with them, not as `DISCONNECT`);
    - app-only: `APP_TIME_UP`, `APP_STOPPED`, `APP_STOPPED_BEFORE_RESTORE`, `APP_LOGIN`, `APP_OUT_OF_BAIT`, `APP_DAILY_LIMIT`,
      `APP_INVENTORY_FULL`, `APP_SOLD_OUT`, `BANNED`, `KICKED`, `APP_RESTORE_FAILED`.

  `metadata.logout_reason` and its lower_snake tokens are gone. For an `APP` event `ip` is the `initiatorIp` of the
  start command (the app request IP) and `hwid` is the session's hwid snapshot (`null` when empty, never `""`);
  `source = APP` marks both as "from the app". Platform readers of "last game IP / HWID" take only `CLIENT` events; the
  session log shows both kinds with the mark. See [`013`](013-character-core-extension.md).

- R16. No new event families: presence is the only session event, so nx-social and the platform consume what they already
  consume.
- R17. The reason-code enum shared by the commands lives in the api (see
  [`offline-fishing.md`](009-commands/offline-fishing.md)); it includes `FEATURE_DISABLED` and `INVALID_DURATION`.
  `StartPrivateStoreResult` gains `sessionId`, and the private-store start commands carry `idempotencyKey` and
  `initiatorIp` (see [`catalog.md`](009-commands/catalog.md)).

### App settings snapshot

Detail of R18. A game server's app behaviour is configured by the game designer in a settings file of the host (the
adapter does not prescribe its name or format; it is separate from the file with the adapter connection and credentials).
It holds which session types are enabled, the rule parameters (recently-left-the-game minutes, start cooldown, the one return
threshold shared by all session types, restart window, restore batch size and interval, fishing minimum and maximum duration), the placed character's
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

The snapshot rides one topic for every settings DTO, `<tenant>.gs.settings.sync`, routed by the envelope `entityName` the
same way `<tenant>.gd.sync` is (see [`030-gamedata-sync`](030-gamedata-sync/spec.md) §7). The first entity is
`appsettings`, DTO `AppSettings` (api, a `settings` package next to the other sync DTOs): one row per server.

| Part             | Content                                                                                                                                      |
| ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| per session type | the `ActivityType` of the session (`OFFLINE_FISHING`, `OFFLINE_PRIVATE_STORE`) and `enabled`                                                 |
| rule parameters  | typed values the UI needs for hints: recently-left minutes, cooldown, return threshold, restart window, fishing minimum and maximum duration |

Published at adapter start and after every applied settings change; an unchanged snapshot is not republished. The
platform stores it per server (table `gs_server_settings`, column `type` = `APP`, with the snapshot as JSON) so the app
can grey out a disabled type and show the thresholds; it is not exposed to the frontend yet. Fishing thresholds, the
daily ceiling and the reset are not here: they travel in the `usage` rows of each character
([`017`](017-character-runtime-activity.md)); they change only on a host restart, after which the host republishes every
usage row.

### Placed character (the "phantom" of the design)

A character loaded by the engine is called the app-loaded character; the design's "phantom" means exactly this: any
character placed in the world by an app command (fisher, app store, later mail/attributes/enchant). There is no separate
"stands while the player is in the app" kind. The generic wire contract is only what the platform must know: the
session, its type, its events. How the host renders and protects the placed character (title text and colour, display
only and not persisted; invulnerable; not a target; no trade, party, duel or summon; no online rewards; no "entered
game" / "disconnect" notifications) is host configuration and out of scope of this contract. Offline stores started in
the game (`.offline`) are not sessions and are not touched.

The host marks the placed character with its engine's offline-mode flag, so every existing exclusion of offline traders
(online rewards, AFK and captcha checks, olympiad, auto-farm and so on) applies for free. Places that read the flag as
"this is a store" (restoring offline traders, closing a store on a sell-out, the offline trade manager) tell the cases
apart by the session type.

## Session report - initial design, not approved (separate design session pending)

> Status: an initial sketch so the other specs have something to point at. It is NOT approved and NOT implemented in
> phase 1: no adapter column, no field on the presence event, no platform table. The owner will run a separate design
> session. Do not implement from this section.

One general mechanism for any session, modeled on the "current session" window of the game client. A report would later
ride the end presence event.

- Totals: time in the world, experience, SP, adena change.
- Items: `{itemTemplateId, count, source}`; `source` is a token such as `FISHING`, `PICKUP`, `DROP`, `QUEST`.
- Activity sections: `activities: [{type, ...}]`; fishing: catch by kind, casts. An offline fisher is recognised by the
  session's `activity = OFFLINE_FISHING`.
- The first filler would be offline fishing. The report of a `CLIENT` session is a platform backlog item.
- Platform storage idea: a row per session with totals and activity sections as JSON, and a row per item by source,
  linked by `session_id`, kept as long as the sessions.

## Rollout

Four phases, across repos:

1. **Adapter and platform, host untouched.** Adapter: migrations ([`037`](037-schema-migrations.md)), the Liquibase 5
   wrapper, session engine, SPI, contract (the new shapes and the removal of the old ones in one api release),
   published to Maven Central. Platform: `gs_sessions.source` / `activity`, `APP` filters, activity-usage storage, the
   closed `CharacterActivity` and bucket enums with their CHECKs and migrated data, the private legacy mapping for the
   old shapes, offline-fishing commands answering `501` (a deployed adapter answers an unknown `Nx-Message-Type` with
   `UNSUPPORTED_COMMAND`), the `source = APP` filters for the presence notifications, the permission seeded and granted to
   nobody. The platform already sends `idempotencyKey` and `initiatorIp` on the private-store commands (an old host
   ignores unknown fields).
2. **Host, first restart: app sessions.** Migrations through the adapter, private store from the app on sessions (hard
   cutover, no row migration: the old app-store marker and its character variables are removed, a leftover app-store row
   in the offline-trader table is restored as an ordinary offline store; host owners get an optional SQL to delete such
   rows before the restart), the recently-left-the-game rule, activity usage only, staff online window row.
3. **Host, second restart: offline fishing** with its datapack (spots, fishing and peace zone where needed); the
   permission goes to testers; the next morning restart verifies fisher restore. The host's app settings file and the
   settings snapshot (R18) ship with phase 2.
4. **Opening and cleanup, no host release:** permission to the player role, frontend, the platform drops its legacy
   mapping of the old shapes once every game server of the tenant is on the phase-2 host, backlog by priority
   ([`TODO §7`](../TODO.md#7-recurringschedule-to-rrule) first).

**Straddle rule (contract host <-> platform).** The host switches to the new shape in one release (it sends only the
new form). The adapter removes the old member in the SAME api release; it keeps no `@Deprecated` copy. The straddle lives
only on the platform (and nx-social for presence): until the gate "every game server of the tenant restarted on the new
host" it reads the old form of a not-yet-restarted host with its own private legacy DTO / raw-JSON mapping inside the
consumer slice, not with adapter classes, each piece marked `TODO: drop once ...` with a backlog item. Deploy order for a
new activity, bucket or reason: adapter enum (api release), then the platform enum and CHECK, then the host.

Each host phase goes to a test server first.

## Open questions

- [ ] Session report: initial design only, the owner's design session is pending (see
      [Session report](#session-report---initial-design-not-approved-separate-design-session-pending)).
- [ ] The placed character must not additionally get `online = true` from the host's normal enter-world path; the host
      emits only the session's own presence events.
- [ ] Limits of the app flows for the placed character and the ban-zone behaviour: host configuration, not decided.
- [ ] Proposed numbers (defaults of the app settings keys) are listed in the host documentation and not final.
- [assumed: the adapter exposes no REST/Kafka query for session history; the platform builds history from the
  events]

## Links

- Table migrations: [`037-schema-migrations`](037-schema-migrations.md)
- Commands: [`009-commands/offline-fishing.md`](009-commands/offline-fishing.md),
  [`009-commands/catalog.md`](009-commands/catalog.md)
- Events and buckets: [`013`](013-character-core-extension.md), [`011`](011-events-online-snapshot.md),
  [`017`](017-character-runtime-activity.md)
- Platform counterparts: `nx-gameservers/docs/specs/086-offline-fishing.md` (with updates to
  `043-sessions/spec.md`, `009-characters.md`, the online-snapshot spec and `052-private-store-start-commands.md`),
  `nx-social/docs/specs/024-character-presence-notifications/spec.md` (the `source = APP` filter; fishing notifications are a separate deferred task, `nx-social/docs/specs/056-fishing-notifications.md`).
  The platform seeds only the permission `GS_COMMANDS_OFFLINE_FISHING_OWN` in this iteration, granted to nobody; a
  player's session history waits for its screen (platform backlog).
- Host documentation: not linked here, this repository does not name tenant hosts.
