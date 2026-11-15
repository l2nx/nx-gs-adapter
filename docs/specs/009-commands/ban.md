# Commands — Ban / Unban

> Owner: @n1rmata

## Problem

Operators cannot apply or lift a ban on a game-server from the platform, and the
platform has no durable picture of the bans a server already carries — whether
issued from the platform or in-game (GM command, anti-cheat). Ban / mute / jail
state lives entirely in the host's punishment engine and never leaves the JVM.

This slice adds both halves:

- a **down-channel** command pair (`BanCommand` / `UnbanCommand`) on the
  existing `NxCommands` rail (see [`commands`](spec.md)) so the
  platform can apply / clear a ban on one server, and
- an **up-channel** sync DTO (`BanDbDto`) mirroring every persisted punishment
  row onto the db-sync stream so the platform sees the full moderation state.

The contract is build-agnostic: it names the target dimension, the punishment
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
  - `@Nullable Instant expiresAt` — instant the ban lapses; `null` = permanent.
  - `@Nullable String reason` — human-readable reason, surfaced to the player
    and stored on the row.
  - `@Nullable String issuedBy` — admin display name / service id, stored for
    audit.

  Idempotent without a key: the host rule of R11 makes a repeated command with
  the same `expiresAt` return the same ban unchanged.

- [done] R2. `nx-gs-adapter-api.kafka.commands.ban.BanResult` MUST ship as the
  success payload:
  - `List<Long> banIds` — never `null`; empty when the ban kind is not
    persisted as an id-bearing row. The ids of the ban rows the host created,
    extended or found, so the platform can correlate the request with the rows
    that later arrive on the ban sync stream. A `HARD` fan-out returns one id
    per concrete dimension.
  - `@Nullable List<BanRecord> bans` — the same rows in the same order, each
    with its effective expiry after the command. `null` = the host does not
    report expiries (a host built against an api before this field); never
    `null` from a host on the current api. The constructor rejects a list whose
    ids differ from `banIds`.

  One `bans` entry is the wire record
  `nx-gs-adapter-api.kafka.commands.ban.model.BanRecord`:
  - `long id` — REQUIRED. Host ban row primary key, the same id as in
    `banIds`.
  - `@Nullable Instant expiresAt` — the term the row carries after the
    command: the new term when the host created or extended the ban, the
    existing one when it returned the ban as is. `null` = permanent.

- [done] R3. `nx-gs-adapter-api.kafka.commands.ban.UnbanCommand` MUST ship as
  `NxCommand<UnbanResult>` — the inverse of `BanCommand`. Fields `targetType` /
  `targetValue` / `banType`, all REQUIRED (same vocab + null-check contract as
  R1). A `HARD` target clears every concrete dimension for the subject. Clearing
  a ban that is not present is a no-op success, not an error.

- [done] R4. `nx-gs-adapter-api.kafka.commands.ban.UnbanResult` MUST ship as the
  success payload — `boolean removed` (`false` = no matching ban existed, a
  no-op success) + `List<Long> removedBanIds` (never `null`; the cleared
  row ids, empty when nothing matched or the kind is not id-bearing).

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
  the canonical `UPPER_SNAKE_CASE` open-string punishment-kind vocabulary. The
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
  platform-supplied per-tenant ban sync topic). Only the primary key `id` is
  REQUIRED; everything else is `@Nullable` so a host surfaces the subset its
  schema carries. Fields:
  - `long id` — REQUIRED. Host punishment row primary key.
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
  - applies / clears the ban on the host punishment engine, hopping blocking
    JDBC onto `ctx.io()` and game-thread mutations onto `ctx.host().sync(...)`;
  - returns `BanResult` / `UnbanResult` with the affected ban row ids;
    `BanResult.bans` carries every returned row with its effective expiry.

- [todo] R10. The host's `DbSchemaProvider` MUST add a `ban` entity mapping its
  engine's punishment columns onto `BanDbDto` in `mapEntity`, translating the
  engine's punishment enum onto the platform-canonical `WellKnownBanTypes` /
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

`BanResult.bans` (R2) is additive on the wire: a host built against an
older api omits it and the platform reads `null` ("expiry not reported"). The
`BanResult` constructor takes `bans` as a new second parameter (source-breaking
for host code that calls the constructor; the builder is unaffected). A host on
an older build may still return an active ban as is instead of extending it
(R11) until it is restarted on a build that follows the rule.

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
