# Commands — Ban / Unban

> Owner: @n1rmata

## Problem

Operators cannot apply or lift a ban on a game-server from the platform, and the
platform has no durable picture of the bans a server already carries — whether
issued from the platform or in-game (GM command, anti-cheat). Ban / mute / jail
state lives entirely in the host's ban engine and never leaves the JVM.

This slice adds both halves:

- a **down-channel** command pair (`BanCommand` / `UnbanCommand`) on the
  existing `NxCommands` rail (see [`commands`](spec.md)) so the
  platform can apply / clear a ban on one server, and
- an **up-channel** sync DTO (`BanDbDto`) mirroring every persisted ban
  row onto the db-sync stream so the platform sees the full moderation state.

The contract is build-agnostic: it names the target dimension, the ban
kind, and the expiry. The host maps those onto its own engine and decides how a
ban is enforced.

Audience: platform-side moderation authors composing ban commands / consuming
the ban sync stream; host-side (host) authors wiring the handlers + the
schema-provider mapping.

## Requirements

> Sibling features carry the wire + dispatch plumbing:
>
> - [`commands`](spec.md) — Kafka commands topic + consumer + dispatch table + reply path + heartbeat slot. UNCHANGED by this slice.
> - [`db-sync`](../003-db-sync/spec.md) — CDC engine + `DbSchemaProvider` SPI + per-entity sync topics carrying the up-channel `BanDbDto`. UNCHANGED.
> - [`adapter-modules`](../002-adapter-modules/spec.md) — Tier-1 ServiceLoader `AdapterModule` discovery used to wire the host handlers.

### Down-channel — apply / clear

- [done] R1. `nx-gs-adapter-api.kafka.commands.ban.BanCommand` MUST ship as
  `NxCommand<BanResult>`. Final Java-8 POJO + builder; constructor enforces
  non-null `targetType` / `targetValue` / `banType` for programmatic
  construction (wire-path Gson bypasses the constructor → the handler
  re-validates and emits `VALIDATION_FAILED`). Fields:
  - `String targetType` — REQUIRED. A `WellKnownBanTargetTypes` value.
  - `String targetValue` — REQUIRED. The keyed datum for `targetType` (char id
    as a string, account login, plaintext IP, or HWID hash).
  - `String banType` — REQUIRED. A `WellKnownBanTypes` value.
  - `boolean permanent` — REQUIRED. `true` iff `expiresAt` is `null` (the constructor
    rejects any other pairing).
  - `@Nullable Instant expiresAt` — instant the ban lapses; `null` = permanent.
  - `@Nullable String reason` — human-readable reason, surfaced to the player
    and stored on the row.
  - `@Nullable String issuedBy` — admin display name / service id, stored for
    audit.

  Idempotent without a key: the host rule of R11 makes a repeated command with
  the same `expiresAt` return the same ban unchanged.

- [done] R2. `nx-gs-adapter-api.kafka.commands.ban.BanResult` MUST ship as the
  success payload:
  - `List<BanRecord> bans` — REQUIRED, never `null` or empty: a successful ban
    always yields at least one row. Every ban row the host created, extended or
    found, one per concrete dimension of a `HARD` fan-out, including a shadow
    chat ban.
  - `Instant observedAt` — REQUIRED. Host time the rows were observed after the
    change was applied (for a no-op, when the host found them). The
    platform writes the rows into its ban mirror at once and gates every later
    write of the same row (this reply or the ban sync stream) by it, newest
    observation wins. The sync stream's `SyncEvent.timestampEpochMs` is the
    instant the engine read the row, not its publish time (005 R12), and the
    adapter runs in the host JVM, so both stamps share one clock: a row read
    before the command and published after it loses to this reply.

  One `bans` entry is the wire record
  `nx-gs-adapter-api.kafka.commands.ban.model.BanRecord`:
  - `String source` — REQUIRED, non-blank. A token of the open vocabulary
    `BanSource`: `STANDARD` (a row of the host ban table, `row.id` = its primary
    key) or `SHADOW` (shadow chat ban, `row.id` = char id) — the same id spaces
    as the `ban` / `shadowban` sync entities (R7). A string, not the enum, because
    the platform reads with Jackson: an enum-typed field would fail the whole
    `BanResult` on a token it does not know yet. `BanSource.fromToken` reads an
    unknown token as `null`, and a consumer tolerates it (keeps the row, skips
    what needs the source). The builder also takes the enum.
  - `BanDbDto row` — REQUIRED. The row as it stands after the command, in the
    exact shape the sync stream carries (R7): the new term when the host
    created or extended the ban, the existing one when it returned it as is.

- [done] R3. `nx-gs-adapter-api.kafka.commands.ban.UnbanCommand` MUST ship as
  `NxCommand<UnbanResult>` — the inverse of `BanCommand`. Fields `targetType` /
  `targetValue` / `banType`, all REQUIRED (same vocab + null-check contract as
  R1). A `HARD` target clears every concrete dimension for the subject. Clearing
  a ban that is not present is a no-op success, not an error.

- [done] R4. `nx-gs-adapter-api.kafka.commands.ban.UnbanResult` MUST ship as the
  success payload:
  - `List<BanRef> removedBans` — REQUIRED, never `null`; the cleared rows, empty
    when no matching ban existed (a no-op success). No separate `removed` flag:
    it would only repeat `!removedBans.isEmpty()`.
  - `Instant observedAt` — REQUIRED, as in R2.

  One `removedBans` entry is `nx-gs-adapter-api.kafka.commands.ban.model.BanRef`:
  `String source` (REQUIRED, non-blank `BanSource` token, as `BanRecord.source`)
  and `long id` (in the id space of `source`); the pair, not the id alone, names
  the row.

- [done] R5. `nx-gs-adapter-api.kafka.commands.ban.WellKnownBanTargetTypes` MUST
  ship the canonical `UPPER_SNAKE_CASE` open-string target-dimension vocabulary:
  - `CHARACTER` — `targetValue` = char id as a string.
  - `ACCOUNT` — whole login account; `targetValue` = account login.
  - `IP` — `targetValue` = plaintext IP.
  - `HWID` — `targetValue` = HWID hash.
  - `HARD` — command-only fan-out marker: the host expands one `HARD` command
    into the full concrete set (character + account + IP + HWID) for the same
    subject. A persisted row NEVER carries `HARD` — it surfaces as the concrete
    dimension it was expanded into, and `HARD` is not valid on `BanDbDto`.

- [done] R6. `nx-gs-adapter-api.kafka.commands.ban.WellKnownBanTypes` MUST ship
  the canonical `UPPER_SNAKE_CASE` open-string ban-kind vocabulary. The
  contract names _what is restricted_, not how the host enforces it; a host maps
  its own engine enum onto these:
  - `GAME_LOGIN` — blocks entering the game (login rejected).
  - `CHAT` — visible chat mute (player is told chat is forbidden).
  - `CHAT_SHADOW` — silent chat mute (messages reach only the sender).
  - `PARTY` — blocks forming / joining a party.
  - `JAIL` — confines to the jail zone; duration counted in online time.

### Up-channel — persisted-ban mirror

- [done] R7. `nx-gs-adapter-api.kafka.sync.db.ban.BanDbDto` MUST ship as the wire
  DTO for one persisted ban (payload of `SyncEvent<BanDbDto>` on the
  platform-supplied per-tenant ban sync topic). The topic carries two entities,
  told apart by the envelope `entityName`, each with its own id space:
  - `ban` — a row of the host ban table; `BanDbDto.id` = that row's primary key
    (`BanSource.STANDARD`).
  - `shadowban` — a shadow chat ban, kept on the character rather than in the ban
    table; `BanDbDto.id` = the char id, `targetType` `CHARACTER`, `banType`
    `CHAT_SHADOW` (`BanSource.SHADOW`).

  `BanDbDto.id` therefore lives in the space named by its entity (on the sync
  stream) or its `source` (in a command reply); the same number in both spaces
  names two different bans. Only `id` is REQUIRED; everything else is
  `@Nullable` so a host surfaces the subset its schema carries. Fields:
  - `long id` — REQUIRED. Row id in the space of the entity / source above.
  - `@Nullable String targetType` — a `WellKnownBanTargetTypes` value
    (`CHARACTER` / `ACCOUNT` / `IP` / `HWID`; never `HARD`).
  - `@Nullable String targetValue` — keyed datum for `targetType`.
  - `@Nullable String targetName` — human-readable target name (character /
    account name) surfaced because the in-game admin UI displayed it.
  - `@Nullable String banType` — a `WellKnownBanTypes` value.
  - `@Nullable Instant expiresAt` — `null` = permanent (host "no expiry"
    sentinel maps to `null`).
  - `@Nullable String reason` / `@Nullable String issuedBy`.

  The contract carries both up-channel and down-channel directions: a ban
  applied via `BanCommand` AND a ban issued in-game (GM / anti-cheat) both
  surface here, giving the platform the full moderation picture.

### Core / platform wiring

- [todo] R8. The platform MUST advertise the ban sync topic on `/connect` and
  run a consumer for `SyncEvent<BanDbDto>`, plus route `BanCommand` /
  `UnbanCommand` onto the existing commands topic (single topic — the
  `kafka.commands.ban` package split is for Javadoc / IDE discovery only). No
  new adapter-core dispatch code: commands route by `Message-Type` through
  the existing `NxCommands` consumer; the ban DTO rides the standard db-sync
  per-entity flow.

### Host (host)

- [todo] R9. The host MUST register a `BanCommand` handler and an `UnbanCommand`
  handler via `NxCommands.on(...)` (in its `HostCommandsModule.onConnect`).
  Each handler:
  - validates `targetType` / `targetValue` / `banType` (known vocab) and emits
    `VALIDATION_FAILED` on a missing / unrecognized value, `NOT_FOUND` when the
    targeted character / account does not exist, `FORBIDDEN` on host policy
    rejection;
  - expands `HARD` into the concrete dimension set;
  - applies / clears the ban on the host ban engine, hopping blocking
    JDBC onto `ctx.io()` and game-thread mutations onto `ctx.host().sync(...)`;
  - returns `BanResult` / `UnbanResult` with every affected row (R2, R4) and
    the host time of the change.

- [todo] R10. The host's `DbSchemaProvider` MUST add a `ban` entity mapping its
  engine's ban columns onto `BanDbDto` in `mapEntity`, translating the
  engine's ban enum onto the platform-canonical `WellKnownBanTypes` /
  `WellKnownBanTargetTypes` vocabulary and mapping the host's "no expiry"
  sentinel to a `null` `expiresAt`.

- [host rule] R11. **A repeated ban extends, never shortens.** Every host's
  `BanCommand` handler, per concrete dimension (after the `HARD` fan-out):
  - an active ban of the same target and `banType` exists, and the new term
    ends later than its term or is permanent → the host extends that ban to
    the new term (same row, same id) and returns it with the new `expiresAt`;
  - an active ban exists and the new term ends at or before its term → the
    host returns it as is, with its own `expiresAt`;
  - no active ban → the host creates a new row.

  A term is never shortened by `BanCommand`; a shorter term goes through
  `UnbanCommand` and a new `BanCommand`. So a repeated command with the same
  `expiresAt` is idempotent without an idempotency key: the second delivery
  returns the same ban unchanged.

## Compatibility

Purely additive. The `kafka.commands.ban.*` command/result/vocab types and
`kafka.sync.db.ban.BanDbDto` are new in `nx-gs-adapter-api` (released as
`api/v0.67.0`). No existing wire shape changes; no adapter-core change is
required (commands route by `Message-Type`; the DTO rides the standard
db-sync flow). A host on an older api jar simply does not register the handlers
and does not map the `ban` entity until rebuilt against the new api.

The full-row `BanResult` / `UnbanResult` (R2, R4) replaces the earlier
`banIds` / `removedBanIds` + `{id, expiresAt}` form and the `removed` flag in
one api release (`api/v0.131.0`), with no deprecated fields; `bans` is never
empty and `source` is a `String` token: the host switches in one release, and the platform reads
replies of a host not restarted yet with its own legacy mapping until every
game server of the tenant runs the new build (platform spec
`nx-gameservers/docs/specs/030-bans/spec.md` §2). A host on an older build may
still return an active ban as is instead of extending it (R11) until it is
restarted on a build that follows the rule.

## Non-goals

- **Adapter-side ban enforcement / policy.** The adapter ships the contract; the
  host owns enforcement, fan-out expansion, and policy rejection.
- **Multi-server fan-out.** A `BanCommand` targets exactly one server (routed by
  `Server-Id`); the platform issues one command per server in scope.
- **A dedicated `unban` sync DTO.** A lifted ban surfaces as a `DELETED` op on
  the `ban` sync entity (consumers MUST explicitly handle `DELETED` — topics use
  bounded retention, not log compaction).

## Links

- Sibling reference (concrete-command handler + DTO migration pattern):
  [`docs/specs/009-commands/send-mail.md`](send-mail.md)
- Commands rail infrastructure: [`docs/specs/009-commands/spec.md`](spec.md)
- Up-channel sync infrastructure: [`docs/specs/003-db-sync/spec.md`](../003-db-sync/spec.md)
