# character-core-extension

> Owner: @n1rmata

## Problem

The platform's `CharacterDbDto` (CDC) and `CharacterRuntimeDto` (runtime) carried only the basic identity, progression
and vitals set. A generic "Characters" admin page on any L2 build also needs the account owner, the noble status, a
soft-delete mark and an explicit online flag. CDC does not fit online: logins and logouts happen more often than its
cycle ticks. The feature needs a compact extension of the wire DTOs and of the runtime channel, with an explicit
online / offline mark, that drags in no build-specific extras (lock flags, blockedEXP, IP / HWID, see
[Deferred fields and filters](#deferred-fields-and-filters-backlog)).

## Requirements

**Must:**

- [done] R1. `CharacterDbDto` MUST add the optional fields `accountName: String`, `nobless: Boolean`,
  `scheduledDeletionAt: Instant`, `online: Boolean`, all nullable; a provider whose source has no matching column leaves
  them `null`. `id` and `name` became **required** (the constructor throws an NPE on a null name), which any L2J schema
  guarantees structurally; a schema provider MUST skip dirty rows before DTO assembly (reference integration:
  `CharacterMapping.mapEntity` returns `null` for rows with a null `char_name`, which the engine reads as "skip this pk
  this cycle").
- [done] R2. `CharacterRuntimeDto` MUST add the optional field `online: Boolean`. Wire convention (byte budget):
  - a regular live-state row: `online = null` (omitted on the wire by `serializeNulls=false`); a consumer MUST read
    omitted or `null` as `true`;
  - a one-shot OFFLINE tombstone: `online = false` explicit, vitals and coordinates `null` (omitted); the row exists only
    to flip the platform-side presence;
  - an explicit `online = true` is allowed but wasteful: a provider should prefer `null` for a regular ONLINE.
- [done] R3. The host `CharacterMapping` MUST surface the new fields through the hashed columns `account_name`,
  `nobless`, `deletetime` on top of the existing ones. The source sentinel `deletetime = 0` becomes `null` (the L2J
  convention for "not marked for deletion"). The source `nobless` (tinyint 0/1) becomes a Boolean.
- [done] R4. The host `CharacterRuntimeMapping` MUST keep an in-memory `previousOnline: Set<Long>` of ids between ticks.
  On every `snapshot()` the mapping:
  - emits a regular `CharacterRuntimeDto` (vitals and coordinates) for every player online now, with `online = null`;
  - emits a one-shot OFFLINE tombstone (`CharacterRuntimeDto{id, online=false}`) for every id that was in
    `previousOnline` and is missing from the current online set;
  - overwrites `previousOnline = currentOnline`, so tombstones are not carried to the next tick.
- [done] R5. `CharacterRuntimeMapping.hash(dto)` MUST include the `online` field in the FNV-1a fold, so an ONLINE to
  OFFLINE transition passes the engine's hash diff and is published.
- [done] R6. Adapter restart semantics: after a restart `previousOnline` is empty, and the mapping does NOT emit
  OFFLINE tombstones for players who logged out while the adapter was down. Recovering such stale `online=true` rows is
  the job of the platform-side sweeper (see `nx-gameservers/docs/specs/009-characters.md` §4 R10).

- [done] R12. The discrete `CharacterPresenceEvent` (`events.character` family, a single-event family) MUST carry
  `eventId: UUIDv7` (REQUIRED, `occurredAt` is derived from it), `online: boolean` (REQUIRED, `true` = login, `false` =
  logout) and the optional `accountName`, `ip`, `hwid`. The character is `charId: long` (REQUIRED, also the partition
  key), renamed to `characterId` by [`043`](043-session-results.md) R2. The host emits it from the standard
  packet path (enter-world for a login, player delete for a logout) through its presence publisher, bound in `onConnect`
  of the host's events module. Cheat or custom clients bypass the packet path, so no event comes; the runtime tombstones
  and the CDC `online` are the fallback.

- [done] R13. `CharacterDbDto.online` (CDC source), the runtime tombstones and the discrete events MUST all land in one
  column `gs_characters.online` with a timestamp-gated UPSERT (the newest `presence_at` wins). Reading is a plain
  `SELECT online`.

- [wip] R14. The logout event `CharacterPresenceEvent` (`online=false`) MUST carry why the session closed in the required
  enum field `logoutReason` (see R15). The metadata key `logout_reason` and its lower_snake tokens (`disconnect`,
  `offline_trade`, `server_stop`) are gone from the api. Client session values: `LOGOUT` (a normal player exit),
  `DISCONNECT` (a lost connection; a kick is no longer in it and arrives as `KICKED`, a ban as `BANNED`),
  `OFFLINE_PRIVATE_STORE` (the player left and the character stayed as an offline trader; the game session closes and
  [planned] a store session opens, R17), `SERVER_STOP` (the host sends it for every player in the world at server
  stop). The platform builds the session log and "last login / logout" from this
  (`nx-gameservers/docs/specs/043-sessions/spec.md`); a logout notification goes out only on `DISCONNECT`.

- [wip] R15. `CharacterPresenceEvent` MUST gain the fields below. Additive on the wire, but the API release that adds
  them also removes the `metadata.logout_reason` constants of `WellKnownPresenceMetadata` (the adapter keeps no
  `@Deprecated` copy; the platform and nx-social read the old form of not-yet-restarted hosts with their own legacy
  mapping until every game server of the tenant restarted on the new host). There are no separate session-end or
  session-resumed events: the presence event is the session event.
  - `source` (`CLIENT` / `APP`), REQUIRED on every presence event. The host sets `CLIENT` for a client session; there is
    no "null reads as `CLIENT`" in the adapter contract (only the platform maps the missing field of an old core to
    `CLIENT`, in its own legacy mapping);
  - `activity` (`ActivityType`, see [`017`](017-character-runtime-activity.md)): REQUIRED with `source = APP`
    (`OFFLINE_FISHING` or `OFFLINE_PRIVATE_STORE`, the app private store); with `source = CLIENT` absent on a session
    played in the client and, [planned] per R17, `OFFLINE_PRIVATE_STORE` on a native offline store session;
  - `sessionId`: a start and its matching end carry the same id, fresh per session; on `APP` the app session's id;
  - `resumed` flag (`true` only on the `online = true` event of a session restored after a restart, same `sessionId`,
    `APP` only);
  - `logoutReason` (`LogoutReason`, closed enum, constant name on the wire), REQUIRED on every `online = false` event,
    a plain logout included. Client values: `LOGOUT`, `DISCONNECT` (involuntary connection loss: network drop, client
    crash, DDoS), `OFFLINE_PRIVATE_STORE`, `SERVER_STOP`, `SOLD_OUT` (a native offline store sold everything,
    R17). Common to client and app sessions: `BANNED`, `KICKED` (a client session reports a kick or a ban with them, not
    as `DISCONNECT`). App-only values: `APP_TIME_UP`, `APP_STOPPED`, `APP_STOPPED_BEFORE_RESTORE`, `APP_LOGIN`,
    `APP_OUT_OF_BAIT`, `APP_DAILY_LIMIT`, `APP_INVENTORY_FULL` (no free slot for the catch), `APP_OVERWEIGHT`
    (the catch would overload the character), `APP_SOLD_OUT`, `APP_INTERRUPTED` (the game stopped the placed character's
    action: a teleport, the tool or consumable vanished, the action failed to restart; not `KICKED`, not `APP_STOPPED`),
    `APP_RESTORE_FAILED`. A new value lands in order: adapter enum (api release), then the platform enum and its CHECK,
    then the host; `APP_INTERRUPTED` came in api 0.115.0.

  An `APP` event describes a character placed in the world by an app command
  ([`038-app-offline-sessions`](038-app-offline-sessions.md)):
  - `sessionId` is the app session's id;
  - the end of a session is `online = false` with the app-session `logoutReason`;
  - a session continued after a restart is `online = true` with the same `sessionId` and `resumed = true`;
  - `ip` is the IP of the app request that started the session (`initiator.ip` of the start command) and `hwid` is the
    hwid snapshot the engine used for the one-fishing-per-computer limit (`null` when empty, never `""`); `source = APP`
    marks both as "from the app".

  The platform filters `APP` out of "entered game" / "disconnect" notifications. "Last login / logout", the last game
  IP / HWID (character card, account's last IP, unique counts, the referral HWID check) read only `CLIENT` events
  [planned] with no `activity`: a native store session is not the player in the game. The session log shows every kind
  with its mark.

- [done] R16. **Session result.** An `online = false` event MAY carry `result` (`SessionResult`), on `CLIENT` and `APP`
  sessions alike, and never on `online = true`. `null` when the host has none or the character was not placed in this JVM.
  Shapes, enums and the byte budget are in [`043-session-results`](043-session-results.md).

- [wip] R17. **Native offline store.** The client command that leaves a character as an offline trader ends the game
  session with `OFFLINE_PRIVATE_STORE` and opens a separate session under a new `sessionId`, `source = CLIENT`,
  `activity = OFFLINE_PRIVATE_STORE`, which ends when the trader leaves the world (`SOLD_OUT`, `KICKED`, `BANNED`,
  `SERVER_STOP`). The constructor allows `CLIENT` with `OFFLINE_PRIVATE_STORE` and no other `activity`; `activity` stays required with `APP`, and
  `resumed` stays `APP`-only, so a restore after a restart is a new session. Details in
  [`043`](043-session-results.md#native-offline-store).

**Should:**

- [done] R7. `CharacterRuntimeDto.online` SHOULD NOT serialize on the wire when `null` (`serializeNulls=false`, the
  default of the wire producer). At a peak of ~5-10k characters per tick this saves ~50-100 KB/min against an explicit
  `true` on every live-state row.

**Could:**

- [todo] R8. The mapping COULD keep a map of recently offlined ids with a TTL, to re-emit OFFLINE on an ack failure
  detected at the next tick. Today the platform-side idle sweeper covers it: lost acks cost about a minute.

**Non-goals:**

- `accessLevel`, `lastAccess`, `onlineTime`, `hero`, lock flags, blockedEXP, last-login IP / HWID,
  character_auth_history: deferred to separate slices (see
  [Deferred fields and filters](#deferred-fields-and-filters-backlog)).
- A discrete login / logout event family on a separate channel: the runtime presence channel is enough.
- A tombstone mechanism at the engine level (rather than the mapping level): not needed, the semantics sit behind the
  mapping.

## Open questions

- [assumed: a 60 s platform-side idle threshold covers lost OFFLINE tombstones and adapter restarts without false
  flickering under coarse network blips (10 s tick x 6 = 60 s safety).]
- [assumed: the `online` column of `characters` (L2J) is _not_ read through CDC: the engine uses only the runtime channel
  for presence. The alternative (CDC online) gives DB-authoritative truth but clutters CDC with login / logout UPDATEs.]

## Links

- Parent feature: [`docs/specs/003-db-sync/spec.md`](003-db-sync/spec.md), the CDC mapping of `CharacterDbDto`.
- Parent feature: [`docs/specs/006-runtime-sync.md`](006-runtime-sync.md), the runtime engine of `CharacterRuntimeDto`.
- Deferred backlog: [Deferred fields and filters](#deferred-fields-and-filters-backlog).
- Platform side: `nx-gameservers/docs/specs/009-characters.md`.
- App sessions (`source = APP`): [`038-app-offline-sessions`](038-app-offline-sessions.md).
- Session result and native store sessions: [`043-session-results`](043-session-results.md).

---

## Technical design

### Overview

An extension of the existing character DTOs and host mappings, with no new modules and no change to the engine
internals. Every change stays local:

- `nx-gs-adapter-api`: new fields in the two POJO DTOs plus their builder methods.
- host `CharacterMapping` (db sync): a few new hashed columns plus readers in `CharacterRow`.
- host `CharacterRuntimeMapping` (runtime sync): an in-memory online-set diff and tombstone generation.

### Wire schema deltas

#### `CharacterDbDto` (kafka.sync.db.character)

```java
+ @Nullable String  accountName;   // characters.account_name
+ @Nullable Boolean nobless;       // characters.nobless (tinyint 0/1)
+ @Nullable Instant scheduledDeletionAt;    // characters.deletetime (BIGINT epoch ms; 0→null)
```

JSON:

```json
{
  "id": 54321,
  "name": "Cliodhna",
  "accountName": "kiryl@nexus",
  "nobless": true,
  "scheduledDeletionAt": "2026-06-01T12:00:00Z",
  ...
}
```

#### `CharacterRuntimeDto` (kafka.sync.runtime.character)

```java
+ @Nullable Boolean online;        // null/true=ONLINE; false=OFFLINE tombstone
```

Wire (live-state row, online):

```json
{ "id": 54321, "curHp": 4321, ... }   // online omitted → ONLINE
```

Wire (offline tombstone):

```json
{ "id": 54321, "online": false } // vitals null → omitted
```

### Mapping-side details

#### Host CharacterMapping

`HASHED` extended:

```java
List.of("char_name", "account_name", "level", "sex", "race",
        "classid", "base_class", "clanid", "pvpkills", "pkkills",
        "karma", "title", "nobless", "deletetime")
```

`mapRow` reads:

- `account_name` (varchar) via `rs.getString`
- `nobless` (tinyint) via `JdbcNulls.nullableInt`, mapped to `Boolean` via `code != 0`
- `deletetime` (bigint epoch ms) via `JdbcNulls.nullableLong`, mapped to `Instant.ofEpochMilli` with `0` → `null`

UPDATE storm risk: `account_name` (changes only on the `TransferCharToAccount` command, rare), `nobless` (a one-time
quest), `deletetime` (the mark-for-delete flow, rare). All are safe as hashed columns.

#### Host CharacterRuntimeMapping

State:

```java
private final Set<Long> previousOnline = new HashSet<>();
```

`snapshot()` algorithm:

```
currentOnline = []
rows = []
for player p in GameObjectsStorage.getPlayers() where p.isOnline():
    currentOnline.add(p.objectId)
    rows.add(RuntimeRow{p.objectId, toDto(p)})   // online=null

for id in previousOnline:
    if id not in currentOnline:
        rows.add(RuntimeRow{id, offlineTombstone(id)})   // online=false

previousOnline = currentOnline    # tombstones NOT carried forward
return rows
```

`hash(dto)` now mixes `online` into FNV-1a (when non-null).

Single-instance assumption: the mapping is held by one host `RuntimeStateProvider` instance, which ServiceLoader
instantiates exactly once per JVM, so the stateful field `previousOnline` is correct. If the SPI starts instantiating the
mapping per tick the feature breaks; no JUnit test guards this.

### Data flows

1. **Tick K** (steady state, 9.5k online players):
   - the mapping builds `currentOnline` (9.5k entries), creates 9.5k ONLINE rows and no tombstones (the typical case:
     nobody logged out at the moment of the tick).
   - the engine hash-diffs: for ~95% of ids the hash is unchanged, 0 publishes; for ~5% (moved, damaged, healed) CHANGED
     publishes.
   - `previousOnline = currentOnline`.
2. **Tick K+1** (30 players logged out):
   - the mapping builds `currentOnline` (9470 entries).
   - removed = 30 ids; the mapping adds 30 OFFLINE tombstones to the rows.
   - engine: the 30 OFFLINE rows have a new hash (including `online=false`), CHANGED publishes on the topic.
   - `previousOnline = currentOnline` (without the removed 30). The tombstones are forgotten.
3. **Tick K+2** (the offlined players do not return):
   - the mapping does not see the departed in `previousOnline` and emits no repeated tombstones: a clean seam between
     mapping-level presence and engine-level hash diff.

### Adapter restart

`previousOnline` resets. On the first tick the mapping emits a full snapshot of ONLINE rows for every current player;
OFFLINE tombstones for players who left while the adapter was down are NOT emitted (the mapping does not remember them).
Those rows stay `online=true` on the platform until the idle sweeper fires (see
`nx-gameservers/.../character-core-extension`).

### Memory budget

A `HashSet<Long>` of ~10k entries is ~640 KB (Long boxing plus HashMap overhead). If it becomes a bottleneck, replace it
with a `LongOpenHashSet`. Not a bottleneck today.

### Integration points

- **nx-gs-adapter-api 0.26+:** two POJO DTOs with new fields. Wire back-compat: old consumers ignore unknown fields
  (Jackson / Gson run with `fail-on-unknown-properties=false` / lenient).
- **Host integration:** the update of `CharacterMapping` and `CharacterRuntimeMapping`; a compile-time bump of
  nx-gs-adapter-api.
- **Platform side:** `nx-gameservers` ingests the new fields and presence (see the parallel feature there).

### Decisions

- **`online` as a Boolean, not an enum.** At high load (10k characters x 6 ticks/min) the enum strings `"ONLINE"` /
  `"OFFLINE"` through Gson cost an extra 50-100 KB/min. A Boolean is 5 bytes (`false`), and omit-on-null is 0 bytes for
  a regular ONLINE. The meaning comes from the null convention, not from a distinct enum.
- **A stateful mapping for presence, not an engine-level reset marker.** A mapping-level previous-online is local
  know-how of one entity and needs no engine API extension. A reset marker on the wire (a protocol-level signal from the
  adapter to the platform) would be less generic: it would need reset semantics in the engine. The platform's idle
  sweeper closes stale online after an adapter restart more cheaply.
- **Tombstones are NOT carried forward after the first emit.** If the engine's ack of an OFFLINE tombstone failed, it is
  lost; the platform closes the gap with the idle sweeper (~1 min). A retry buffer in the mapping (recently offlined plus
  a TTL) is planned as a future option (R8, Could).

### Extension points

- **A new presence kind (for example AWAY):** extends `online` to a third state. A Boolean is limited, so this would go
  back to an enum. The decision waits for a real ops case.
- **Per-server-id presence in platform-side reconciliation:** already supported through the `Nx-Server-Id` header on the
  runtime topic.

---

## Deferred fields and filters (backlog)

Fields and filters of the platform's character model deliberately left out of the current core. When the design of a
block matures it lands as a separate slice.

### Deferred fields

| Field                                         | Reason for deferral                                                                                            |
| --------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| `lastAccess`                                  | Whether to read `characters.lastAccess` or derive it from online-set transitions is not decided.               |
| `onlineTime`                                  | The same; on top of that, vanilla L2J writes `onlinetime` only at logout, which lags real time.                |
| `hero`                                        | A generic L2 field, but not critical for the first UI iteration.                                               |
| `blockedExp`                                  | Build-specific: a concrete key in `character_variables`.                                                       |
| Last-login `ip`, `hwid`                       | Needs modelling of `character_auth_history` or an equivalent; deferred.                                        |
| `coinsDeposited` (derived)                    | Not the character domain: computed by the shop / transfers service. A cross-service read on the platform side. |
| `tgUsers[]`                                   | Not the character domain: held in nx-users / telegram-link.                                                    |
| Clan `crestPath`, `allyCrestPath`, `clanName` | Resolved through the Clan entity, not duplicated in Character.                                                 |

### Deferred filters

| Filter                              | Reason for deferral                                                                                                                                     |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ip`, `hwid`                        | No last-login IP / HWID fields in the core, no character_auth_history.                                                                                  |
| `lock-ip / lock-hwid / lock-items`  | Rest on build-specific lock flags.                                                                                                                      |
| `blocked-exp`                       | Rests on a build-specific flag.                                                                                                                         |
| `unique-auth` (HWID / IP / HWID_IP) | Needs an aggregate of character_auth_history.                                                                                                           |
| `store-mode` (multiple)             | The `privateStore` enum is already in the DTO, but the "several modes at once" filter semantics are not specified; added when the frontend asks for it. |
| `telegram-user`                     | Not the character domain (see tgUsers above).                                                                                                           |

### Shipped (out of the backlog)

- `accessLevel`: synced as a build-agnostic string (`CharacterDbDto.accessLevel`, `@Nullable String`): numeric text
  (`"7"`) on int builds, the role name verbatim on string-role builds, `null` when the source has none. No parsing, no
  enum (read through JDBC `getString`).
- Character locks (`lockIp` / `lockHwid` / `lockItem`): synced as `CharacterDbDto.locks: List<CharacterLockDbDto>`
  (`lockType` in `WellKnownCharacterLockTypes` = IP / HWID / ITEM, `lockValue?`). One entry per **active** lock (the value
  is present, non-blank, not `"0"`). Filters by lock flags (`lock-ip` / `lock-hwid` / `lock-items`) stay deferred: only
  the list itself is synced.

### Notes

- The build-specific attribute `blockedEXP` waits for a separate design: a generic key-value extension or typed optional
  fields. It is not modelled until a core-agnostic form appears.
- Any field or filter from this backlog lands through a separate design spec, not hastily in the existing core.
