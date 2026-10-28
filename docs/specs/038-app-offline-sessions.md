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

**Layering.** The adapter is transport plus a generic session engine: the table, the SPI, start idempotency,
`active_ms`, end by time, the restore after a restart, one session per character and one character per spot. It holds no
game rules. Who may start, cooldowns, per-account / per-device limits, duration bounds and location checks are host rules
enforced by the host. The adapter owns the app settings file that carries the tenant-agnostic parameters of those rules
(see [App settings file](#app-settings-file)) and hands them to the host typed; host-only rules stay in the host's own
configuration. The platform checks only ownership, server liveness and its rate limit.

**Status.** Phase 1 (adapter and platform) and phase 2 (host: app sessions, the private store on sessions, buy store
included) are shipped. Offline fishing (phase 3) is not implemented in the host: its start answers `FEATURE_DISABLED`.
The adapter parts phase 3 needs (R20-R22: the app settings file, the session-history read, `ClientIdentifiers`) are
released (api 0.111.0, core 0.45.0, db-migrations 0.3.0). The private-store start cooldown (R23) is implemented in the
adapter and not yet released (see [Rollout](#rollout)), as are the acknowledged end (R24), the open-sessions snapshot
(R25) and the check mode (R26).

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
  `OFFLINE_FISHING`). The engine receives host callbacks through the capability `NxAppSessions`: the action ended with a
  reason, and the character entered the game from a client. The engine's own parameters (return threshold, restore
  pace, retention) come from the adapter's app settings file (R20), not from the SPI. Shapes in
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
  duration bounds, location checks, stock checks) are enforced by the host. The adapter does not evaluate them: it
  carries their tenant-agnostic parameters in the app settings file (R20), gives the host the session facts a rule
  needs (R21), and the host reports a refusal with an adapter reason code from the shared enum (see
  [`offline-fishing.md`](009-commands/offline-fishing.md)). The time a character left the game MUST be recorded on every
  disconnect of a live client, including the in-game command that leaves the character as an offline trader: that
  command only works in a trade zone, which is a peaceful town zone, so no special case or bypass exists.
- [done] R9. A character logging in with a client MUST close its own session with reason `APP_LOGIN`, under the character's
  mutation lock and before the live character is loaded, so the last action cycle finishes before the character leaves
  the world; login is never blocked by the session. Logging in with another character of the same account MUST NOT close
  the session (that character's own live fishing start is then a host limit). A login before the post-restart restore
  has reached the character cancels the session. The host's login path calls `NxAppSessions.onClientLogin`.
- [done] R10. Login is never blocked by a session for good: at worst the engine's bounded wait runs out and the host
  refuses that one attempt with "try again" (`ClientLoginOutcome.PENDING`) while the close finishes in the background.
- [done] R11. A session survives a restart as open: the engine restores it on the schedule below with the same `id`.
  Restore that cannot happen ends the session with `APP_RESTORE_FAILED`. The platform's server-generation close does not
  touch `APP` sessions, and `SUPERSEDED` for an `APP` session has no generation boundary (a session lives across
  restarts). [planned] The platform reconciles `APP` sessions left open by mistake against the open-sessions snapshot
  (R25), not by guessing ([nx-gameservers#85](https://github.com/l2nx/nx-gameservers/issues/85)).
- [done] R12. There are no dedicated session events. The existing `CharacterPresenceEvent` is the session event: the end
  of a session is `online = false` with the required `logoutReason` (R15); a session restored after a restart is
  `online = true` with the same `sessionId` and `resumed = true` (R15).
- [done] R13. Closed sessions older than the retention MUST be deleted at server start. The retention is the key
  `app.history.retention-days` of the app settings file (R20).
- [done] R18. A typed snapshot of the server's app settings MUST be published as the first entity of the shared settings
  topic (see [Settings snapshot](#settings-snapshot)). The platform stores it per server. The adapter builds and
  publishes it from its own file (R20).
- [done] R20. The adapter MUST own the app settings file `l2nx-app.properties`: the key catalog, types, ranges and
  defaults, whole-file validation, hot reload, the settings snapshot, and a typed read-only view of the live values for
  the host (`NxAppRules` / `AppRules`). See [App settings file](#app-settings-file).
- [done] R21. The adapter MUST give the host a read of session facts for its start rules, so the host never queries
  `l2nx_*`: the open sessions of a master account, every open session of a type (the host's limits and spot pick at
  `hostReady`, before the restore), the last real start of a master account's session of a type (the fishing cooldown)
  and the last real start of a character's session of a type (the private-store cooldown, R23). See
  [Session-history read](#session-history-read).
- [done] R22. Every `hwid` and IP the adapter sends to the platform or stores in its table MUST pass
  `ClientIdentifiers` (`nx-gs-commons`): a placeholder becomes `null`. See [Client identifiers](#client-identifiers).
- [host rule] R23. A new app private-store start (sell, package sell or buy) of a character MUST be refused with
  `COOLDOWN` plus `retryAt` while less than `app.private-store.start-cooldown-minutes` passed since the real start of
  that character's previous app private-store session (owner decision 2026-10-23, against fast price dumping). The
  scope is the character, not the master account; only app sessions count (`OFFLINE_PRIVATE_STORE`; a store opened in
  the game does not); stopping a store is never refused. The host enforces it; the adapter carries the key (R20) and
  the read (R21). Implemented in the adapter, not yet released (api 0.112.0, core 0.46.0, db-migrations 0.4.0).
- [done] R24. **A session end reaches the platform.** Implemented in the adapter, not yet released. The end event is no longer fire-and-forget: the row records
  `end_published_at` once Kafka acknowledged the end event, and the engine's flush tick republishes the end of every
  closed row whose `end_published_at` is `NULL` until it is acknowledged. The events publisher gets a send with a
  completion callback for this. A republish is the same event as the first send, `eventId` included: a UUIDv7 whose
  timestamp is the end time and whose other 74 bits are the session id's own, so the platform deduplicates it by
  `eventId` as well as by `sessionId`. Rows closed before the column exist count as published (the migration fills
  `ended_at` into it), so an upgrade does not replay history.
  - One sender per end: the close claims the end before its row commits as closed, and a claim lasts until Kafka
    answers, or, once the acknowledgement is stored, until the next read of the unpublished ends. A flush tick never
    sends an end the close or an acknowledgement in flight still holds.
  - Acknowledgements are stored in batches, one `UPDATE ... WHERE id IN (...)` per up to 500 of them.
  - A failure that a later send cannot fix (the bound events facade reports no acknowledgement, the `character` family
    has no topic, the event type has no binding) marks the end published with one WARN instead of looping. Every other
    failure (a full queue, a broker error, a drop before connect) leaves the end for the next tick.
  - The flush tick reads the unpublished ends through the index `(end_published_at, ended_at)`, paging by the cursor
    `(ended_at, id)` until it has sent 500 ends or reached the end. Rows this version cannot read (written by a newer
    one) take their place in a page but are neither sent nor marked, so they never hide the rows behind them and a
    newer version still publishes them. A row whose end event cannot be built is logged once as an ERROR and skipped.
- [done] R25. **Open-sessions snapshot.** Implemented in the adapter, not yet released. Every 60 s and once at module start the engine publishes
  `AppSessionsSnapshotEvent {eventId, takenAt, sessionIds}`: the ids of every open session of this server, the ones
  running and the ones waiting for the post-restart restore (`PENDING_RESTORE`). It travels in the `character` event
  family with the server as the record key (a server without an id from `/connect` publishes no snapshot: an unkeyed
  one could overtake its successor); `eventId` is a UUIDv7 (dedup and order, as the online snapshot of
  [`011`](011-events-online-snapshot.md)). A session that is starting is listed from the moment its row is written,
  before its placement fixes `startedAt`. `takenAt` has the table's second precision and is read before the ids, so a
  session missing from a snapshot has `startedAt >= takenAt`; the platform must not close sessions that started at or
  after it. The platform knows the server's start
  from `ServerStartedEvent` (`011`) and needs no generation counter in the snapshot. The platform closes an open
  `APP` session that a snapshot newer than its login (past a grace) does not list. The snapshot is a full set, not a
  diff; an empty list is a valid snapshot of a server with no open session.
- [done] R26. **Check mode for start.** Implemented in the adapter, not yet released. The engine's own start checks (one active session per character, the
  spot pool's free spot) are available without side effects, so a check command ([`009`](009-commands/spec.md) R31) can
  run the start's path to its end without writing a row, taking a spot or placing the character.
  `NxAppSessions.check(characterId, spotPool, spotId)` answers its own SPI type `AppSessionCheckOutcome` (`ALLOWED`,
  `CHARACTER_BUSY` with the open session, `SPOT_TAKEN`, `UNAVAILABLE` with a detail); the start's
  `AppSessionStartOutcome` has no `ALLOWED`. The spot is validated and trimmed by the same code as a start request
  (`AppSessionStartRequest.normalizeSpotPool`, `requireValidSpotId`), so a check never accepts a spot a start would
  reject. A busy character is reported as `ALREADY_ACTIVE` with `scope = CHARACTER` in `AppSessionCheckResult`, as a
  refused start reports it.

**Non-goals:**

- Evaluating business rules, spot pool data, rendering the title, flags of the placed character: the host (R8). The
  adapter carries only the tenant-agnostic parameters (R20) and the session facts (R21).
- Frontend. The app screens consume the platform API.
- Trust levels for starting a session. [assumed: until a trust system exists, start is guarded by a permission on the
  platform side]. Trust is a separate item on the platform's backlog.
- The session report: an initial design only, not approved and not implemented in phase 1 (see
  [Session report](#session-report---initial-design-not-approved-separate-design-session-pending); [#42](https://github.com/l2nx/nx-gs-adapter/issues/42), the report of a client session is [#43](https://github.com/l2nx/nx-gs-adapter/issues/43)).
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
  `active_ms` is below the return threshold of the app settings file (one threshold for all session types, the same
  for fishing and private store); at the threshold or longer it stays where it is (the fishing spot, the store spot).
- **Origin was a dangerous place** (jail, boss room, instance, olympiad): the origin recorded in the row is already
  replaced by a safe location before the session starts.
- **Action ended in the host** (out of bait, inventory full, ban, kick): the host calls back with the reason and the
  engine closes the session.
- **Session type disabled in the app settings file:** running sessions finish normally, the host refuses a new start
  with `FEATURE_DISABLED` (see [App settings file](#app-settings-file)).
- **Client disconnect right before a start:** counts as leaving the game (R8), including the offline-trader command.
- **Private store restarted to change prices:** the stop always succeeds; the next start of that character waits for
  the private-store cooldown counted from the real start of the stopped session (R23), whatever the store kind.
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
  (`spi.capability`), their value types `OfflineSession`, `OfflineSessionPlacement`,
  `AppSessionStartRequest`, `AppSessionStartOutcome` (`spi.model`); session reason codes, presence additions (`source`,
  `activity`, `logoutReason`, `sessionId`, `resumed`), the closed enums `ActivityType`, `ServerOnlineBucket` and
  `LogoutReason`, the settings snapshot DTO, the commands. R20 adds the capability `NxAppRules` and the value types
  `AppRules` and `SpotOrder` (`spi.model.appsettings`).
- `nx-gs-adapter-core` - the engine (package `core.appsessions`): `AppSessionEngine`, `JdbcOfflineSessionStore`,
  the built-in module `AppSessionsModule` and the JVM-wide holder `AppSessionsRuntime`. R20 adds the package
  `core.appsettings` (`AppSettingsFile` parse and validate, `AppSettingsWatcher` reload, `AppSettingsRuntime` the live
  snapshot, the key catalog); publication is `core.settings` (`SettingsModule`, `SettingsPublisher`)
  ([`041`](041-settings-sync.md)).
- `nx-gs-commons` - `ClientIdentifiers` (R22), package `commons.identifiers`.
- `nx-gs-db-migrations` - the changelog that creates `l2nx_offline_sessions` ([`037`](037-schema-migrations.md)).

### Key components

**`OfflineSessionHost`** (api `spi.provider`, host-implemented, `ServiceLoader`; none leaves the engine disabled with an
INFO, two put it into `FAILED`):

```java
OfflineSessionPlacement placeInWorld(OfflineSession session); // placed(spotId[, typeState]) | refused(reason, detail) | refusedForNow(reason, detail)
void startAction(OfflineSession session);                      // throw = the action could not start
void stopAction(OfflineSession session);                       // every end of a started action; must be idempotent
void removeFromWorld(OfflineSession session, boolean returnToOrigin);
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
- The SPI carries no settings: the engine reads its return threshold, restore pace and retention from the live
  `AppRules` (R20) on every use, so an applied file change takes effect without a restart.
- The SPI receives an immutable `OfflineSession` view: id, idempotency key, character, activity, account name, master
  account, hwid, initiator IP, spot, origin, `startedAt`, `plannedMs`, `activeMs` (as of the call),
  `returnThresholdMs` (the threshold fixed at the real start, `null` for a row written before the column), `typeState`, and
  `endedAt` / `endReason` once closed.

**`NxAppSessions`** (api `spi.capability`, the engine as the host sees it). Reached through
`CommandContext.appSessions()`, `ConnectContext.appSessions()` and the static `NxAdapter.appSessions()`; all three are
one instance, usable from JVM start; a context built without it gets a no-op. Nothing throws into the caller.

```java
CompletableFuture<AppSessionStartOutcome> start(AppSessionStartRequest request);
CompletableFuture<Optional<OfflineSession>> stop(int characterId, ActivityType activity);
void onActionEnded(UUID sessionId, LogoutReason reason);   // async; APP_OUT_OF_BAIT, APP_DAILY_LIMIT, APP_INVENTORY_FULL, APP_SOLD_OUT, APP_INTERRUPTED, BANNED, KICKED
void hostReady();                                          // the world is loaded; releases the restore, idempotent
ClientLoginOutcome onClientLogin(int characterId);         // synchronous, bounded wait: CLOSED(session) | NONE | PENDING
Optional<OfflineSession> findActive(int characterId);      // in-memory, game-thread safe
CompletableFuture<Boolean> updateTypeState(UUID sessionId, @Nullable String typeStateJson);
List<OfflineSession> findOpenByMasterAccount(UUID masterAccountId, ActivityType activity);         // R21, in-memory
List<OfflineSession> findOpen(ActivityType activity);                                              // R21, in-memory
CompletableFuture<Optional<OfflineSession>> findLastStartedByMasterAccount(UUID masterAccountId, ActivityType activity); // R21, table
CompletableFuture<Optional<OfflineSession>> findLastStartedByCharacter(int characterId, ActivityType activity);           // R21, table
```

The adapter builds and publishes the settings snapshot from its own file (R20), so the capability has no publish
method. The R21 reads are specified in [Session-history read](#session-history-read).

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
restore of many sessions or a mass end never takes the whole pool. The file's `app.restore.batch-size` is capped at that
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

**The `appsettings` publisher** (`SettingsPublisher<AppSettings>` in `SettingsModule`, notified by the settings-file watcher, R20) sends `SettingsSyncEvent<AppSettings>` to `syncTopics.settings["appsettings"]`
(`<tenant>.gs.settings.sync`), key = the raw 16-byte `serverId` (as on the gd stream), header `Nx-Message-Type =
SettingsSyncEvent`, through the shared settings publisher of [`041-settings-sync`](041-settings-sync.md) (send on
connect, reconnect and change, the 5-minute tick, a missing map entry).

**Threads.** `nx-app-sessions-scheduler` (one daemon thread) runs the restore batches, the end-by-time timers and the
one-minute flush tick; all JDBC and SPI calls hop from it to `nx-io-N`, at most half of those workers at once. The
settings-file watcher has its own thread (see [App settings file](#app-settings-file)).

### Data model

Table `l2nx_offline_sessions`, owned by the adapter and created by the changelog of
[`037`](037-schema-migrations.md).

| Column                 | Type                          | Purpose                                                                                                                                                                                                                                                                                                          |
| ---------------------- | ----------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`                   | `uuid` PK                     | UUIDv7 generated in Java; equals the platform `sessionId`                                                                                                                                                                                                                                                        |
| `idempotency_key`      | `uuid` NOT NULL UNIQUE        | start idempotency: a repeated command finds the created session                                                                                                                                                                                                                                                  |
| `character_id`         | `int` NOT NULL                | character; history and rollback                                                                                                                                                                                                                                                                                  |
| `active_character_id`  | `int` NULL UNIQUE             | equals `character_id` while the session runs, `NULL` once closed: at most one active session per character (all three engines allow many `NULL` in a unique index)                                                                                                                                               |
| `master_account_id`    | `uuid` NULL                   | master-account limit (host rule, read through R21); taken from the command's `initiator.userId`                                                                                                                                                                                                                  |
| `activity`             | `varchar(32)` NOT NULL        | the session's `ActivityType`: `OFFLINE_FISHING`, `OFFLINE_PRIVATE_STORE` (app private store)                                                                                                                                                                                                                     |
| `account_name`         | `varchar(45)` NOT NULL        | per-account limit                                                                                                                                                                                                                                                                                                |
| `hwid`                 | `varchar(300)` NULL           | snapshot of the character's hwid for the host's one-fishing-per-device limit; a placeholder or empty value is stored as `NULL` (R22); `NULL` means no per-device limit; kept for investigations                                                                                                                  |
| `initiator_ip`         | `varchar(45)` NULL            | IP of the app request that sent the start command (`initiator.ip`), a placeholder stored as `NULL` (R22); for investigations and the host's per-IP fishing limit                                                                                                                                                 |
| `spot_pool`, `spot_id` | `varchar(32)`, `int` NOT NULL | the spot: a pool name and an id inside it, both chosen by the host (fishing: a pool of fishing spots; private store: a place of a trade zone)                                                                                                                                                                    |
| `active_spot_key`      | `varchar(48)` NULL UNIQUE     | `<pool>:<spot>` while the session runs: one spot, one character                                                                                                                                                                                                                                                  |
| `origin_x/y/z/heading` | `int` NOT NULL                | where to return the character; a dangerous origin is already replaced by a town. `heading` is the facing direction (0-65535)                                                                                                                                                                                     |
| `started_at`           | `datetime` NOT NULL           | real start (the character stood on the spot); the host's cooldowns read it through R21                                                                                                                                                                                                                           |
| `planned_ms`           | `bigint` NULL                 | the chosen duration; `NULL` for a private store                                                                                                                                                                                                                                                                  |
| `active_ms`            | `bigint` NOT NULL DEFAULT 0   | time in the world excluding restart downtime; drives end-by-time and the return rule (compared with the session's `return_threshold_ms`)                                                                                                                                                                         |
| `return_threshold_ms`  | `bigint` NULL                 | the return threshold fixed at the real start from `app.session.return-threshold-minutes`; the end compares `active_ms` with it, so a file change never moves a running session. `NULL` only for rows written before the column (changeset `0008-offline-sessions-return-threshold`), which use the current value |
| `type_state`           | `clob` NULL                   | per-type JSON: fishing - rod, bait, what to return to the hands, interim catch; store - kind (`SELL` / `PACKAGE_SELL` / `BUY`), lots, message, seller fee percent (`BUY`: lots by template, buyer fee percent)                                                                                                   |
| `ended_at`             | `datetime` NULL               | `NULL` means active                                                                                                                                                                                                                                                                                              |
| `end_reason`           | `varchar(32)` NULL            | end reason: the `LogoutReason` constant name                                                                                                                                                                                                                                                                     |
| `end_published_at`     | `datetime` NULL               | when Kafka acknowledged the end event (R24); `NULL` on a closed row means the end still has to reach the platform; the flush tick republishes it                                                                                                                                                                 |

Indexes: `UNIQUE(active_character_id)`, `UNIQUE(active_spot_key)`, `UNIQUE(idempotency_key)`,
`(account_name, ended_at)`, `(hwid, ended_at)`, `(master_account_id, ended_at)`,
`(master_account_id, activity, started_at)` (R21, the fishing cooldown read; changeset
`0009-offline-sessions-master-account-activity-started-index`), `(character_id, activity, started_at)` (R21, the
private-store cooldown read, see [Session-history read](#session-history-read); changeset
`0010-offline-sessions-character-activity-started-index`), `(character_id, started_at)` (from the table's first
changeset; no adapter query needs it since `0010`, kept because dropping it is a separate decision), `(ended_at)` (the
retention delete at engine start).

The seller fee of an app private store lives in `type_state` of its `OFFLINE_PRIVATE_STORE` session: the host's trade list asks the
seller's session for the percent, so a sale pays the fee to any buyer, including a buyer in the game (stores opened in
the game pay no fee, as before). The host keeps no separate per-character copy of the fee or of the app-store origin.

- [done] R19. A buy store started from the app (`StartPrivateStoreBuyCommand`, see [`catalog.md`](009-commands/catalog.md))
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
app settings file, default 2; `RECENTLY_LOGGED_OUT` plus `retryAt`) applies to the store start exactly as to
fishing. There is no online start path for app stores, so every app-started store is an app `OFFLINE_PRIVATE_STORE` session and
the seller fee always has a session to live in. The stop command closes ANY private store of the player's own character:
for an app session it closes the session (reason `APP_STOPPED`) and applies the return rule; a native `.offline` store or a
live player's store is just closed, as today. An app private store and a `.offline` store share the activity
`OFFLINE_PRIVATE_STORE`; `source` tells them apart, and `.offline` stores are not sessions.

Cleanup: closed sessions older than `app.history.retention-days` are deleted at server start (R13).

### Lifecycle

1. Start: host validates (R8), engine inserts the row under `idempotency_key` (R5, id = UUIDv7), `placeInWorld`,
   records the real `started_at` (and the host's spot and type state), `startAction`, then publishes `online = true, source = APP,
activity, sessionId, ip = initiator.ip, hwid, accountName`.
2. Running: `active_ms` counts while the action runs, flushed every minute (a write never lowers the stored value and
   never touches a closed row) and on stop and shutdown (R7); a timer ends the session when `active_ms >= planned_ms`
   (`planned_ms` null: no time limit).
3. End: by time (`APP_TIME_UP`), by stop command (`APP_STOPPED`), by `APP_LOGIN`, or by a host callback (action ended). The engine
   calls `stopAction` (when the action ran) and `removeFromWorld(returnToOrigin = active_ms < return_threshold_ms)`, fills
   `ended_at` and `end_reason`, clears `active_character_id` and `active_spot_key`, publishes `online = false, source = APP,
logoutReason`. A failed close in the table is logged and retried until the table takes it (the character stays busy
   meanwhile); the event goes out at once. Only if the JVM stops before a retry succeeds is the row, still open, restored
   at the next start (see Engine). The end event is acknowledged into `end_published_at`; an end that Kafka
   did not acknowledge is republished by the flush tick until it is (R24).
4. Server stop: running sessions keep their row open; `active_ms` is stored; nothing is published.
5. Restart: at engine start closed rows past the retention are deleted and rows with `ended_at IS NULL` wait for the
   restore. Once the `app-sessions` module has started and the host has called `hostReady` (whichever comes last), the
   first batch runs at once and the rest follow in batches at the file's pace (`app.restore.batch-size`, capped
   at the engine's share of the IO pool, every `app.restore.batch-pause-millis`; the start of the restore logs the effective pace):
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
  `APP_INVENTORY_FULL`, `APP_INTERRUPTED`, `BANNED`, `KICKED`, `APP_RESTORE_FAILED`.
- `OFFLINE_PRIVATE_STORE` (app store): `APP_SOLD_OUT`, `APP_STOPPED`, `APP_STOPPED_BEFORE_RESTORE`, `APP_LOGIN`, `BANNED`, `KICKED`,
  `APP_RESTORE_FAILED`.

`APP_INTERRUPTED` is the game stopping the placed character's action on its own: the character was moved off its spot
(a teleport), the tool or consumable the action needs vanished, or the action failed to restart. It is neither a staff
kick (`KICKED`) nor the player's stop command (`APP_STOPPED`): the session log and the player's notification name the
game as the cause, not staff or the player.

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
      `APP_INVENTORY_FULL`, `APP_SOLD_OUT`, `APP_INTERRUPTED`, `BANNED`, `KICKED`, `APP_RESTORE_FAILED`.

  `metadata.logout_reason` and its lower_snake tokens are gone. For an `APP` event `ip` is the `initiator.ip` of the
  start command (the app request IP) and `hwid` is the session's hwid snapshot (`null` when empty, never `""`);
  `source = APP` marks both as "from the app". Platform readers of "last game IP / HWID" take only `CLIENT` events; the
  session log shows both kinds with the mark. See [`013`](013-character-core-extension.md).

- R16. No new event families: presence is the only per-session event, so nx-social and the platform consume what they
  already consume. The open-sessions snapshot (R25) is a per-server fact in the existing `character` family,
  not a session event.
- R17. The reason-code enum shared by the commands lives in the api (see
  [`offline-fishing.md`](009-commands/offline-fishing.md)); it includes `FEATURE_DISABLED` and `INVALID_DURATION`.
  `StartPrivateStoreResult` gains `sessionId`, and the private-store start commands carry `idempotencyKey`; the app
  request IP is the command's `initiator.ip` (see [`catalog.md`](009-commands/catalog.md)).

### App settings file

Detail of R20 (and R18). A game server's app behaviour is set by its game designer in `l2nx-app.properties`, a file the
adapter owns: the adapter defines the keys, their types, ranges and defaults, reads and validates the file, reloads it
without a restart, publishes the settings snapshot, and hands the live values to the host as a typed object. The host
enforces the rules (R8) with those values; it parses nothing. The file is separate from `l2nx.properties`: that one is
the adapter connection and credentials, edited by infrastructure; this one is product behaviour of one server, edited by
its game designer. A session type that exists on some servers and not on others is a per-server product setting, not a
feature flag.

A reference file with every key, its default and an English comment per key (meaning, units, allowed values) lives in
the repository root as `l2nx-app.properties`, next to the `l2nx.properties` sample; the public configuration page links
it.

**Location and override order.** Adapter defaults < the server's file. The file sits in the game server's working
directory next to `l2nx.properties` (when `-Dl2nx.config-file` points elsewhere, next to that file). A missing key takes
its default, a missing file means all defaults. JVM system properties do not override `app.*` keys. Every default is
tenant-agnostic and every session type is off by default.

**Key admission rule.** A key enters this file (and the adapter) only when it is tenant-agnostic: meaningful for any
host build, with no tenant, server or host-class name in its name or default, and describable on the public docs without
host internals. A rule only one host has (for example its own fishing penalty tiers or daily ceiling) stays in that
host's own configuration, never in `l2nx-app.properties`.

**Key catalog.** Keys are lower case with dots and dashes, as in `l2nx.properties`. "Snapshot" marks a key the settings
snapshot carries.

| Key                                        | Type    | Range                                                                             | Default                       | Meaning                                                                                                                                                                                                                                                                                                                                    |
| ------------------------------------------ | ------- | --------------------------------------------------------------------------------- | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `app.phantom.title.text`                   | string  | 1-16 code points; blank = unset                                                   | `l2nx.app`                    | display-only title over a placed character; the platform's name by default, empty value: no override, the character shows its own title                                                                                                                                                                                                    |
| `app.phantom.title.color`                  | string  | 6 hex digits, RGB                                                                 | `FFFF00`                      | colour of that title; used only when the text is set                                                                                                                                                                                                                                                                                       |
| `app.session.recent-logout-minutes`        | int     | 0-60; 0 turns the rule off                                                        | `2`                           | no app start within this many minutes after the character left the game with a client (`RECENTLY_LOGGED_OUT`). Snapshot                                                                                                                                                                                                                    |
| `app.session.restart-window-minutes`       | int     | 0-120                                                                             | `15`                          | no app start when a scheduled restart is closer than this (`RESTART_SOON`). Snapshot                                                                                                                                                                                                                                                       |
| `app.session.return-threshold-minutes`     | int     | >= 0                                                                              | `30`                          | one threshold for every session type, fixed per session at its real start: a session that ends with `active_ms` below it returns the character to its origin, at or above it leaves the character in place. Snapshot                                                                                                                       |
| `app.private-store.enabled`                | boolean | `true` / `false`                                                                  | `false`                       | the app private store (`OFFLINE_PRIVATE_STORE`) on this server; off: `FEATURE_DISABLED`. Snapshot                                                                                                                                                                                                                                          |
| `app.private-store.start-cooldown-minutes` | int     | 0-1440; 0 turns the rule off                                                      | `5`                           | no new private-store start (sell, package sell, buy) of a character within this many minutes after the real start of that character's previous app private-store session (`COOLDOWN`, read through R21; R23). Stopping is never limited. Snapshot                                                                                          |
| `app.fishing.enabled`                      | boolean | `true` / `false`                                                                  | `false`                       | offline fishing (`OFFLINE_FISHING`) on this server; off: `FEATURE_DISABLED`. `true` requires `app.fishing.spot-pool`. Snapshot                                                                                                                                                                                                             |
| `app.fishing.start-cooldown-minutes`       | int     | 0-1440                                                                            | `15`                          | no new fishing start for any character of a master account within this many minutes after the real start of that master account's previous app fishing session (`COOLDOWN`, read through R21). Snapshot                                                                                                                                    |
| `app.fishing.min-duration-minutes`         | int     | >= 1                                                                              | `30`                          | shortest fishing a start may ask for (`INVALID_DURATION` below). Snapshot                                                                                                                                                                                                                                                                  |
| `app.fishing.max-duration-minutes`         | int     | >= `app.fishing.min-duration-minutes`                                             | `1440`                        | longest fishing a start may ask for (`INVALID_DURATION` above); the host's daily stop may still shorten the plan. Snapshot                                                                                                                                                                                                                 |
| `app.fishing.float-distance`               | int     | 90-250, game units                                                                | `170`                         | fixed distance from the fisher to the float along the spot's heading                                                                                                                                                                                                                                                                       |
| `app.fishing.spot-order`                   | enum    | `RANDOM` / `FIRST_FREE`                                                           | `RANDOM`                      | how the host picks a free spot of the pool                                                                                                                                                                                                                                                                                                 |
| `app.fishing.spot-pool`                    | string  | `[a-z0-9_]{1,32}`; blank = unset                                                  | unset                         | name of the spot pool fishing uses on this server; the pool itself (spot ids, coordinates, heading) is host data                                                                                                                                                                                                                           |
| `app.revive.enabled`                       | boolean | `true` / `false`                                                                  | `true`                        | revive of a dead character from the app ([`revive-character`](009-commands/revive-character.md)); off: `FEATURE_DISABLED`. On by default: with `NATIVE` it needs no other key. Snapshot (`revive.enabled`)                                                                                                                                 |
| `app.revive.town.mode`                     | enum    | `NATIVE` / `SPOT_POOL`                                                            | `NATIVE`                      | how the host picks the location of a `TOWN` revive: `NATIVE` as the game does for "to village", `SPOT_POOL` a spot of `app.revive.town.spot-pool`. `SPOT_POOL` requires a non-empty `app.revive.town.spot-pool`                                                                                                                            |
| `app.revive.town.spot-pool`                | list    | comma-separated `[a-z0-9_]{1,32}`, no repeats; blank = empty                      | empty                         | spot pools of a `SPOT_POOL` revive; may name the fishing pool; the pools are host data                                                                                                                                                                                                                                                     |
| `app.chat.enabled`                         | boolean | `true` / `false`                                                                  | `false`                       | chat with the app as a whole ([`send-chat-message`](009-commands/send-chat-message.md) R33); off: the host publishes no game chat, refuses every send from a character with `CHAT_DISABLED` (the platform's own lines still go out) and answers an in-game whisper to `Name*` out of game as to an unknown name. Snapshot (`chat.enabled`) |
| `app.chat.online-send-channels`            | list    | comma-separated `CLAN`, `ALLIANCE`, `PARTY`, `WHISPER`, no repeats; blank = empty | `CLAN,ALLIANCE,PARTY,WHISPER` | channels the app may send to while the character is in game (`CHANNEL_DISABLED` outside); empty: read-only. Snapshot (`chat.onlineSendChannels`)                                                                                                                                                                                           |
| `app.chat.offline-send-channels`           | list    | as above, `PARTY` invalid                                                         | `CLAN,ALLIANCE,WHISPER`       | channels the app may send to while the character is out of game (`CHANNEL_DISABLED` outside); empty: read-only. Snapshot (`chat.offlineSendChannels`)                                                                                                                                                                                      |
| `app.restore.batch-size`                   | int     | >= 1                                                                              | `10`                          | sessions restored per batch after a restart (capped at the engine's share of the IO pool)                                                                                                                                                                                                                                                  |
| `app.restore.batch-pause-millis`           | int     | >= 0                                                                              | `1000`                        | pause between restore batches                                                                                                                                                                                                                                                                                                              |
| `app.history.retention-days`               | int     | >= 7                                                                              | `90`                          | closed sessions older than this are deleted from the adapter's table at server start (R13); bounds the session-history read                                                                                                                                                                                                                |

A new session type adds one `app.<type>.enabled` key of the same form, not a new file layout. The spot pool name goes
into `spot_pool` of the session row; a restored session uses its stored pool, so a changed `app.fishing.spot-pool`
governs new starts only. A pool name the host has no data for is a host configuration error: the server still starts,
the host logs an ERROR and refuses fishing starts with `FEATURE_DISABLED`.

**Validation of the whole file.** The file is read as strict UTF-8 through a `Reader` (comments in any language survive;
a leading byte order mark is skipped) and checked as a whole before anything applies: bytes that are not UTF-8 or a
malformed `\uXXXX` escape reject the file with one ERROR, like an invalid value; every value is trimmed, then checked for its type and range; `true` /
`false` and the enum names are exact; cross-key rules hold (`max-duration-minutes >= min-duration-minutes`,
`app.fishing.enabled=true` requires `app.fishing.spot-pool`, `app.revive.town.mode=SPOT_POOL` requires a non-empty
`app.revive.town.spot-pool`; `PARTY` is invalid in `app.chat.offline-send-channels`); any key not in the catalog is an error (a typo is never a
silent default). One error anywhere rejects the file: the previous values keep working and the log has one ERROR per
problem, `app settings not applied, previous values stay: <key>: <reason>`.

**Load and hot reload.**

1. **First load** happens synchronously on the first `NxAdapter.appRules()` call or in `NxAdapter.start()`, whichever
   comes first, so the engine boot and the host's own boot never see the defaults instead of the file; a concurrent
   caller waits for it. No file: the defaults, one INFO. An invalid or unreadable file: the defaults (every type off) and
   the ERROR lines above. A file that moves during the first read (stamp or content changed across it, an editor
   still writing) is read again up to 3 times with a short pause, so the boot does not run on the defaults while a
   finished file exists; a file still moving after that leaves the defaults until the first poll. Bytes that do not
   decode count as moving when the file changed across the read (a multibyte character cut by an editor), so they are
   read again too. While the config cannot tell the host type, `appRules()` returns the defaults and loads nothing, and
   every later call does the same without reading the config again (no file IO per call on the game thread) until
   `start()` decides; a login server undoes a load that ran before.
2. **Polling.** The daemon thread `nx-app-settings-watcher` reads the whole (small) file about every 5 s and compares
   its bytes with the last file it took: a modification time and size can stay the same across an edit (coarse
   timestamps, `cp -p`, `rsync -t`, a same-length change). A read is taken only when the file's modification time, size
   and content did not move across it; otherwise an editor is still writing and the next tick reads again. A file that
   disappears after a successful load keeps the previous values with one WARN; an unreadable file keeps them with an
   ERROR. Stopping the watcher (shutdown, a login server undoing an early load) waits up to 2 s for a poll in flight; a
   poll that finishes after the stop neither swaps the rules nor publishes.
3. **Atomic swap.** A valid file becomes one immutable `AppRules`, swapped in by a single volatile write; a reader takes
   one snapshot per call and never sees a mix of old and new values.
4. **Change log.** When the new snapshot differs, one INFO line lists only the changed keys,
   `app settings updated: <key>: <old> -> <new>; ...`. A file saved again without changes logs nothing.
5. **Snapshot.** After the first load and after every applied change the adapter publishes the settings snapshot (below).

A login server has no app sessions: there the capability returns the defaults and nothing is watched.

**Effect on running sessions.** New values govern new starts; a running session keeps what the host copied into its
type state at start (the title). A disabled type lets running sessions finish and the host refuses new starts with
`FEATURE_DISABLED`. The restore reads the current `app.restore.*` values. The return threshold is fixed at the real
start: the engine stores it in the session row (`return_threshold_ms`) and compares with it at the end.

**What the host gets.** A read-only capability in api `spi.capability`, the same instance through
`NxAdapter.appRules()`, `ConnectContext.appRules()` and `CommandContext.appRules()`, usable from JVM start. The public `ConnectContext` constructor gained the `appRules`
parameter (its arity changed), so hosts and tests build a `ConnectContext` only through `ConnectContext.builder()`;
`CommandContext` is an interface with a default `appRules()` and has no constructor change:

```java
public interface NxAppRules {
    AppRules current(); // never null; the live snapshot (the defaults until a valid file is loaded); no IO, any thread
}
```

`AppRules` (api `spi.model.appsettings`, immutable, a Java 8 class with getters) carries every catalog key typed:
`@Nullable String phantomTitleText()`, `int phantomTitleColorRgb()`, `int recentLogoutMinutes()`,
`int restartWindowMinutes()`, `int returnThresholdMinutes()`, `boolean sessionTypeEnabled(ActivityType)` (`false` for a
type the catalog has no key for), `int privateStoreStartCooldownMinutes()`, `int fishingStartCooldownMinutes()`, `int fishingMinDurationMinutes()`,
`int fishingMaxDurationMinutes()`, `int fishingFloatDistance()`, `SpotOrder fishingSpotOrder()` (enum `RANDOM`,
`FIRST_FREE`), `@Nullable String fishingSpotPool()`, `boolean reviveEnabled()`, `ReviveTownMode reviveTownMode()`
(enum `NATIVE`, `SPOT_POOL`), `List<String> reviveTownSpotPools()` (empty unless set), `boolean chatEnabled()`,
`Set<ChatChannel> chatOnlineSendChannels()` (each `sendableFromApp()`), `Set<ChatChannel> chatOfflineSendChannels()`
(each `sendableOffline()`; empty = read-only), `int restoreBatchSize()`, `int restoreBatchPauseMillis()`,
`int historyRetentionDays()`, plus `static AppRules defaults()`. There is no change listener: the host reads `current()`
once per check and has no consumer for a push. `AppRules` enforces the catalog itself: its builder throws
`IllegalArgumentException` for a value outside its range or a broken cross-key rule, so no snapshot outside the catalog
exists; the ranges are public `AppRules.Range` constants, and the file validation reads the same constants.

**Module placement.** The typed view (`NxAppRules` in `spi.capability`, `AppRules`, `SpotOrder` and `ReviveTownMode` in `spi.model.appsettings`; the chat channels are the event vocabulary `ChatChannel`) lives in `nx-gs-adapter-api`, because a
host compiles against the contracts artifact alone. Parsing, validation, the key catalog and the watcher live in
`nx-gs-adapter-core`, package `core.appsettings`: they need the adapter's lifecycle and threads, the config-file location
resolved by `core.config`, the settings publisher and the engine as a consumer, all of which are core. `nx-gs-commons`
holds stateless helpers shared by adapter modules and tenant providers; it has no lifecycle and cannot see core, so the
settings runtime does not fit there.

#### Settings snapshot

The adapter publishes the snapshot (R18) itself from the live `AppRules`. It rides one topic for every settings DTO,
`<tenant>.gs.settings.sync`, routed by the envelope `entityName` the same way `<tenant>.gd.sync` is (see
[`030-gamedata-sync`](030-gamedata-sync/spec.md) §7). The first entity is `appsettings`, DTO `AppSettings` (api
`kafka.sync.settings.app`): one row per server.

| Part             | Content                                                                                                                                                                                                                                                                    |
| ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| per session type | the `ActivityType` of the session (`OFFLINE_FISHING`, `OFFLINE_PRIVATE_STORE`) and `enabled`, from `app.<type>.enabled`                                                                                                                                                    |
| rule parameters  | the keys marked "Snapshot" above: recently-left minutes, fishing start cooldown (`fishingStartCooldownMinutes`), private-store start cooldown (`privateStoreStartCooldownMinutes`), return threshold, restart window, fishing minimum and maximum duration (sent in ms)    |
| revive           | `revive.enabled` from `app.revive.enabled`; the whole `revive` group is `null` from a host on an adapter older than it, which the platform reads as "no revive on this server"                                                                                             |
| chat             | `chat.enabled`, `chat.onlineSendChannels`, `chat.offlineSendChannels` (`ChatChannel` names) from `app.chat.*` ([`041-settings-sync`](041-settings-sync.md) R9); `null` from a host on an adapter older than it, which the platform reads as that host's built-in behaviour |

Published on connect, after every applied change and on the 5-minute settings tick
([`041-settings-sync`](041-settings-sync.md) R2, R3). The title, restore pace, retention, float distance, spot order, spot pools and the revive town mode do not travel. The platform
stores the snapshot per server (type `APP`) and serves it to the app, which greys out a disabled type and shows the
thresholds. A host's own fishing thresholds,
daily ceiling and reset are not here: they travel in the `usage` rows of each character
([`017`](017-character-runtime-activity.md)) and change only on a host restart, after which the host republishes every
usage row.

### Session-history read

Detail of R21. The host's start rules need facts only the session table holds; the host never queries `l2nx_*`, so
the engine answers them. All are methods of `NxAppSessions`. The no-op capability answers an empty list for the
in-memory read, and its two table reads complete exceptionally, as the engine's facade does while the engine does not
run (booting, disabled, not started): neither may look like "no previous session".

```java
List<OfflineSession> findOpenByMasterAccount(UUID masterAccountId, ActivityType activity);
List<OfflineSession> findOpen(ActivityType activity);
CompletableFuture<Optional<OfflineSession>> findLastStartedByMasterAccount(UUID masterAccountId, ActivityType activity);
CompletableFuture<Optional<OfflineSession>> findLastStartedByCharacter(int characterId, ActivityType activity);
```

**`findOpenByMasterAccount`** - the master-account limit ("one fishing per master account").

- Input: a non-null master account id and a session activity (`OFFLINE_FISHING`, `OFFLINE_PRIVATE_STORE`).
- Output: every session of that activity whose `master_account_id` equals the input and that still holds its character:
  running, waiting for the post-restart restore, a start in flight (row written, placement not finished), or an end
  queued for a retry. Closed sessions are not in it; a row with a `NULL` master account never matches.
- Order: `startedAt` ascending, then `id`.
- Source: the engine's memory (it holds every open session), so it returns at once, does no IO and is safe on the game
  thread. Commands arrive only after the platform connect and the engine boots before it, so a start check always sees
  a booted engine.
- Semantics: it counts app sessions only. The host does not know the master account of a live player, so live fishing
  never counts against this limit; the host's per-account and per-device limits cover live and app fishing together,
  using the session's `accountName` and `hwid`. IP is not a limit subject of this read. The host's own
  per-IP limit counts an app session under `initiator_ip`
  ([`offline-fishing.md`](009-commands/offline-fishing.md) R12); the host holds that registry, the read is unchanged.
- Concurrency: the check and the engine `start` are two steps. Two starts for different characters of one master
  account can both pass unless the host serializes its fishing starts per master account (the table does not enforce
  this limit).

**`findOpen`** - every open session of a type, for the host's own state at `hostReady`.

- Input: a session activity.
- Output: every session of that activity that still holds its character, whatever its master account: running, waiting
  for the post-restart restore, a start in flight, or an end queued for a retry. Closed sessions are not in it.
- Order, source and the no-op answer: as for `findOpenByMasterAccount` (`startedAt`, then `id`; engine memory, no IO,
  any thread; the no-op and a facade without a running engine answer an empty list).
- Why: the host keeps its per-account and per-device limits (`accountName`, `hwid`) and its free-spot pick in its own
  memory. After a restart the sessions awaiting the restore are not in the world yet, and the restore reaches them in
  batches, so the host does not see them for a while: without this read a new start could pass the limits those
  sessions already use, and a new start or the restore of another session could pick their spot (`spotPool`, `spotId`),
  pushing their own restore to another spot or, with none free, to `APP_RESTORE_FAILED`. The host calls it right before
  `hostReady`, reserves the limits of every listed session and excludes their spots from its pick; the booted engine
  already holds every open row, so the list is complete before the restore starts. The spot is the one the engine
  stored, which the restore may still replace (`OfflineSessionPlacement.placed(spotId, typeState)`).

**`findLastStartedByMasterAccount`** - the fishing start cooldown (`COOLDOWN`, `retryAt = startedAt + app.fishing.start-cooldown-minutes`).
The cooldown is per master account: a fishing start of any character of that master account waits for the cooldown
after the real start of the master account's previous app fishing session.

- Input: a non-null master account id and a session activity.
- Output: the session of that master account and activity with the latest real start (`started_at`), open or closed,
  among the rows still in the table; ties go to the greater `id` (UUIDv7, time-ordered). Empty when there is none. Only
  sessions that really started count: a start still in flight (the engine holds its id in memory) is skipped, and a
  start rolled back before it reached `STARTED` deleted its row. A row with a `NULL` master account never matches.
- Source: a query of the few latest rows on the `(master_account_id, activity, started_at)` index on the engine's IO
  share; the starts in flight are taken from memory after the query, and a start that settled while the query ran
  (placed or rolled back) makes the engine query again. When every row read is a start that never happened, the next
  query reads one row more than the starts of that master account and activity in flight. At most three queries: after
  the third the first row it read that is not in flight answers, so starts that keep settling never hold an IO worker;
  that row may be a start that settled during the read (a stale `started_at`, or a start rolled back right then). The future completes on an adapter thread; the handler waits on its own
  thread, never on `ctx.io()`.
- Failure: a database error completes the future exceptionally, and so does a read while the engine does not run
  (`IllegalStateException`). The host refuses that start as temporarily unavailable, never treats it as "no previous
  session".

**`findLastStartedByCharacter`** - the private-store start cooldown (R23, `COOLDOWN`,
`retryAt = startedAt + app.private-store.start-cooldown-minutes`). The cooldown is per character: a private-store start
(sell, package sell or buy, all `OFFLINE_PRIVATE_STORE`) of a character waits for the cooldown after the real start of
that character's previous app private-store session, open or closed. A `0` key turns the rule off and the host skips the
read.

- Input: a character id and a session activity.
- Output, order, the starts in flight or rolled back, the bounded recheck (at most three queries) and failure: as for
  the master-account read above, with the character instead of the master account. A row with a `NULL` master account
  counts here: the subject is the character.
- Source: a query of the few latest rows on the `(character_id, activity, started_at)` index (changeset
  `0010-offline-sessions-character-activity-started-index`), the same shape as the master-account read.
- Name: the subject is in the name (`ByMasterAccount`, `ByCharacter`), not only in the parameter type: two overloads
  named `findLastStarted` read alike at a call site and leave the subject to the argument. The master-account read
  shipped as `findLastStarted(UUID, ActivityType)` in api 0.111.0 with no consumer yet; api 0.112.0 renames it, with no
  `@Deprecated` copy.

**Retention.** Rows leave the table only at engine start, and only closed ones whose `ended_at` is older than
`app.history.retention-days` (at least 7 days). The cleanup runs only when the live rules are the file's (a valid file
applied, or no file at all): with a rejected, unreadable or half-written file the defaults only stand in for an unknown
retention, so the engine deletes nothing that start and logs a WARN. The widest window a rule reads is a start
cooldown (fishing or private store), at most 1440 minutes each, so the read never misses a session a rule needs. Invariant: any rule window read through R21 stays below the minimum
retention; a key whose range would exceed it raises the retention minimum in the same change.

**Release.** R21 shipped in the one adapter release with R20, R22 and the migrations (changesets `0008`, `0009`): api
0.111.0, core 0.45.0, db-migrations 0.3.0. The per-character read and the private-store cooldown key (R23) ship in api
0.112.0 / core 0.46.0 / db-migrations 0.4.0, with the changeset `0010` (see [Rollout](#rollout)). `findOpen` ships in api
0.115.0 / core 0.48.0, with no migration.

### Client identifiers

Detail of R22. Game clients and hosts fill a missing hwid or IP with placeholders (`N/A`, `0`, `-`, an empty string). A
placeholder is not a value: counted as one it ties unrelated players together in a per-device limit, a ban or a unique
count. `ClientIdentifiers` in `nx-gs-commons` (package `commons.identifiers`, Java 8, stateless, not session-specific)
turns every placeholder into `null`:

```java
static @Nullable String hwid(@Nullable String raw);
static @Nullable String ip(@Nullable String raw);
```

- The value is trimmed first. `null`, empty and blank give `null`.
- Placeholders, case-insensitive: `n/a`, `n\a`, `na`, `n.a.`, `none`, `null`, `nil`, `unknown`, `undefined`,
  `disconnected`, `-`; a string of only `-` characters; a zero-filled value, that is a string that with `-`, `:`, `.`
  and whitespace removed is non-empty and only `0` characters (`0`, `0000`, the nil UUID, `00:00:00:00:00:00`,
  `0.0.0.0`, `0:0:0:0:0:0:0:0`). Each gives `null`. `ip` also treats the unspecified addresses `::` and the IPv4-mapped
  `::ffff:0.0.0.0` (case-insensitive, with the zero groups in any spelling, for example `0:0:0:0:0:ffff:0:0`) as
  placeholders. Nothing is parsed as an address and no name is resolved.
- Anything else is returned trimmed and otherwise verbatim: no case change, no format validation (the adapter is
  transport). The common list is shared; the methods are separate so the rules can diverge per kind.

**The adapter applies it at the wire boundary** to every hwid and IP it sends or stores:

| Field                                                                         | Where it is applied                                                                      |
| ----------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `CharacterPresenceEvent.ip`, `.hwid` (client and app events)                  | core events path, before the event is queued (the event is rebuilt when a value changes) |
| `AccountAuthAttemptEvent.hwid`                                                | core events path, as above                                                               |
| `BanDbDto.targetValue` when `targetType` is `IP` or `HWID`                    | db-sync, right after the row is mapped, before it is published                           |
| `CharacterLockDbDto.lockValue` when `lockType` is `IP`, `HWID` or `ITEM`      | db-sync, as above                                                                        |
| `l2nx_offline_sessions.hwid`, `.initiator_ip` (and the `OfflineSession` view) | the engine, on insert and on reading a row                                               |
| `CommandInitiator.ip`                                                         | commands consumer, at deserialization                                                    |

A placeholder never reaches the platform. db-sync computes its checksum in SQL over the raw columns, so a placeholder changing into another one (`N/A` to `0`) still resends the row with the same payload: harmless. The db-sync
`character` entity and the runtime-sync DTOs carry no hwid or IP today; a field added later falls under this rule in the
same change. A command's `initiator.ip` comes from the platform, passes it in the adapter's commands consumer when the
command is deserialized, and reaches the wire only through the session row.
`AccountAuthAttemptEvent.clientIp` is the socket address of the login connection: always known, required and not
normalized (only its `hwid` passes `ClientIdentifiers`).

Host code uses the same utility for its own checks (ban subjects, the de-duplication behind the online `UNIQUE` bucket,
the hwid snapshot it passes in `AppSessionStartRequest`), so host and adapter agree on what "no value" is.

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

Phases across repos, in delivery order:

1. **[done] Adapter and platform, host untouched.** Adapter: migrations ([`037`](037-schema-migrations.md)), the
   Liquibase 5 wrapper, session engine, SPI, contract (the new shapes and the removal of the old ones in one api
   release), published to Maven Central. Platform: `gs_sessions.source` / `activity`, `APP` filters, activity-usage
   storage, the closed `CharacterActivity` and bucket enums with their CHECKs and migrated data, the private legacy
   mapping for the old shapes, offline-fishing commands answering `501` (a deployed adapter answers an unknown
   `Nx-Message-Type` with `UNSUPPORTED_COMMAND`), the `source = APP` filters for the presence notifications, the
   permission seeded and granted to nobody. The platform sends `idempotencyKey` and `initiatorIp` on the private-store
   commands.
2. **[done] Host, first restart: app sessions.** Migrations through the adapter, the private store from the app on
   sessions (sell, package sell and buy; hard cutover, no row migration), the recently-left-the-game rule, activity
   usage only, the staff online window row, the login close (R9). In this phase the host still reads its own app settings
   file and publishes the snapshot through `NxAppSessions.publishSettings`; the fishing start answers
   `FEATURE_DISABLED`.
3. **[done] Adapter release for fishing** (api 0.111.0, core 0.45.0, db-migrations 0.3.0, commons 0.14.0). One api + core + migrations release carries R20 (the app settings file,
   `NxAppRules` / `AppRules`; removes `OfflineSessionHost.settings()`, `OfflineSessionSettings` and
   `NxAppSessions.publishSettings`), R21 (session-history read) and R22 (`ClientIdentifiers` in commons, applied at the
   wire boundary), plus the db-migrations changesets `0008` (adds
   `return_threshold_ms`) and `0009` (the `(master_account_id, activity, started_at)` index needed by R21).
   **[todo] Follow-up adapter release for the private-store cooldown** (api 0.112.0, core 0.46.0, db-migrations 0.4.0,
   R23): the key `app.private-store.start-cooldown-minutes`, `AppRules.privateStoreStartCooldownMinutes()`, the
   snapshot field `privateStoreStartCooldownMinutes`, `NxAppSessions.findLastStartedByCharacter(int, ActivityType)`,
   the `PrivateStoreReason.COOLDOWN` token and the changeset `0010` (the `(character_id, activity, started_at)` index).
   The same release renames `NxAppSessions.findLastStarted(UUID, ActivityType)` to `findLastStartedByMasterAccount`
   (no consumer yet) and the snapshot field `startCooldownMinutes` to `fishingStartCooldownMinutes` (one-release
   rename, no `@Deprecated` copy). Order (new vocabulary value): this api release, then the platform knows `COOLDOWN`
   for private-store refusals and reads the snapshot of a host on core before 0.46.0 with its own legacy mapping: no
   `privateStoreStartCooldownMinutes` there, and the fishing cooldown under `startCooldownMinutes`. The platform reads
   both cooldown names until the gate "every GS of the tenant on core 0.46.0+", then drops the old one. Then the host
   enforces R23 in its private-store start path.
   **[todo] Adapter release for the fishing host's restore and interrupt** (api 0.115.0, core 0.48.0): `NxAppSessions.findOpen(ActivityType)`
   (R21) and the new `LogoutReason.APP_INTERRUPTED`, accepted by `onActionEnded`. Additive, no migration. Order (new
   vocabulary value): this api release, then the platform enum and its CHECK learn `APP_INTERRUPTED`, then the host sends
   it; a host that never sends it needs no change.
4. **[todo] Host, second restart: offline fishing.** The host switches in one release: it reads its rule values from
   `NxAppRules`, drops its own settings-file code, uses R21 for `COOLDOWN` and the master-account limit and
   `ClientIdentifiers` for its own identifier checks, and runs offline fishing with its datapack (spot pool data, fishing
   and peace zones where needed). `app.fishing.enabled=true` and `app.fishing.spot-pool` only on the servers that get
   fishing. Every `app.*` line of a server's file must pass the adapter's validation (for example `app.fishing.spot-order`
   takes `RANDOM` / `FIRST_FREE`), so the datapack change ships in the same restart. The next morning restart verifies
   fisher restore.
5. **[todo] Platform opening.** The platform drops its `501`, the permission goes to a testers role, then, after a prod
   check, to the player role; frontend. The platform drops its legacy mapping of the old shapes once every game server of
   the tenant is on the phase-2 host; backlog by priority ([#40](https://github.com/l2nx/nx-gs-adapter/issues/40) first).

**Straddle rule (contract host <-> platform).** The host switches to the new shape in one release (it sends only the
new form). The adapter removes the old member in the SAME api release; it keeps no `@Deprecated` copy. The straddle lives
only on the platform (and nx-social for presence): until the gate "every game server of the tenant restarted on the new
host" it reads the old form of a not-yet-restarted host with its own private legacy DTO / raw-JSON mapping inside the
consumer slice, not with adapter classes, each piece marked `TODO: drop once ...` with a backlog item. Deploy order for a
new activity, bucket or reason: adapter enum (api release), then the platform enum and CHECK, then the host. The R20
removals need no platform straddle: the snapshot DTO and its topic do not change, only who builds it.

Each host phase goes to a test server first.

## Open questions

- [ ] Session report: initial design only, the owner's design session is pending (see
      [Session report](#session-report---initial-design-not-approved-separate-design-session-pending)).
- [ ] The placed character must not additionally get `online = true` from the host's normal enter-world path; the host
      emits only the session's own presence events.
- [ ] Limits of the app flows for the placed character and the ban-zone behaviour: host rules, not decided.
- [ ] Float distance 170 and spot order `RANDOM`: confirm on the first fishing end-to-end run.
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
