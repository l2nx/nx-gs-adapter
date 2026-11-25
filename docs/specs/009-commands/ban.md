# Commands — Ban / Unban

> Owner: @n1rmata
> Counterpart: `nx-gameservers/docs/specs/030-bans/spec.md`. Design of the `subject*` pair approved 2026-11-24
> (l2nx/nx-gameservers#214), released in api 0.133.0 (`subject*`) and 0.134.0 (`publicReason`); the former names
> left the api in 0.135.0 (R13).

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

The contract is build-agnostic: it names the subject (type and value), the ban
type, and the expiry. The host maps those onto its own engine and decides how a
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
  non-null subject pair / `banType` for programmatic
  construction (wire-path Gson bypasses the constructor → the handler
  re-validates and emits `VALIDATION_FAILED`). Fields:
  - `String subjectType` — REQUIRED. A `WellKnownBanSubjectTypes` value.
  - `String subjectValue` — REQUIRED. The keyed datum for `subjectType` (char id
    as a string, account login, plaintext IP, or HWID hash).
  - `String banType` — REQUIRED. A `WellKnownBanTypes` value.
  - `boolean permanent` — REQUIRED. `true` iff `expiresAt` is `null` (the constructor
    rejects any other pairing).
  - `@Nullable Instant expiresAt` — instant the ban lapses; `null` = permanent.
  - `@Nullable String publicReason` — human-readable reason, shown to the player
    and stored on the row.
  - `@Nullable String issuedBy` — admin display name / service id, stored for
    audit.

  Idempotent without a key: the host rule of R11 makes a repeated command with
  the same `expiresAt` return the same ban unchanged.

- [done] R2. `nx-gs-adapter-api.kafka.commands.ban.BanResult` MUST ship as the
  success payload:
  - `List<BanRecord> bans` — REQUIRED, never `null` or empty: a successful ban
    always yields at least one row. Every ban row the host created, extended or
    found, a shadow chat ban included.
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
  `NxCommand<UnbanResult>` — the inverse of `BanCommand`. Fields `subjectType` /
  `subjectValue` / `banType`, all REQUIRED (same vocab + null-check contract as R1).
  Clearing
  a ban that is not present is a no-op success, not an error.

- [done] R4. `nx-gs-adapter-api.kafka.commands.ban.UnbanResult` MUST ship as the
  success payload:
  - `List<BanRef> unbannedBans` — REQUIRED, never `null`; the cleared rows, empty
    when no matching ban existed (a no-op success). No separate flag: it would only
    repeat `!unbannedBans.isEmpty()`.
  - `Instant observedAt` — REQUIRED, as in R2.

  One `unbannedBans` entry is `nx-gs-adapter-api.kafka.commands.ban.model.BanRef`:
  `String source` (REQUIRED, non-blank `BanSource` token, as `BanRecord.source`)
  and `long id` (in the id space of `source`); the pair, not the id alone, names
  the row.

- [done] R5. `nx-gs-adapter-api.kafka.commands.ban.model.WellKnownBanSubjectTypes` MUST
  ship the canonical `UPPER_SNAKE_CASE` open-string subject vocabulary:
  - `CHARACTER` — `subjectValue` = char id as a string.
  - `ACCOUNT` — whole login account; `subjectValue` = account login.
  - `IP` — `subjectValue` = plaintext IP.
  - `HWID` — `subjectValue` = HWID hash.

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
    table; `BanDbDto.id` = the char id, `subjectType` `CHARACTER`, `banType`
    `CHAT_SHADOW` (`BanSource.SHADOW`).

  `BanDbDto.id` therefore lives in the space named by its entity (on the sync
  stream) or its `source` (in a command reply); the same number in both spaces
  names two different bans. Only `id` is REQUIRED; everything else is
  `@Nullable` so a host surfaces the subset its schema carries. Fields:
  - `long id` — REQUIRED. Row id in the space of the entity / source above.
  - `@Nullable String subjectType` — a `WellKnownBanSubjectTypes` value
    (`CHARACTER` / `ACCOUNT` / `IP` / `HWID`).
  - `@Nullable String subjectValue` — keyed datum for `subjectType`.
  - `@Nullable String subjectName` — human-readable subject name (character /
    account name) surfaced because the in-game admin UI displayed it.
  - `@Nullable String banType` — a `WellKnownBanTypes` value.
  - `@Nullable Instant expiresAt` — `null` = permanent (host "no expiry"
    sentinel maps to `null`).
  - `@Nullable String publicReason` / `@Nullable String issuedBy`.

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

- [done] R9. The host MUST register a `BanCommand` handler and an `UnbanCommand`
  handler via `NxCommands.on(...)` (in its `HostCommandsModule.onConnect`).
  Each handler:
  - validates `subjectType` / `subjectValue` / `banType` (known vocab) and emits
    `VALIDATION_FAILED` on a missing / unrecognized value, `NOT_FOUND` when the
    subject character / account does not exist, `FORBIDDEN` on host policy
    rejection;
  - applies / clears the ban on the host ban engine, hopping blocking
    JDBC onto `ctx.io()` and game-thread mutations onto `ctx.host().sync(...)`;
  - returns `BanResult` / `UnbanResult` with every affected row (R2, R4) and
    the host time of the change.

- [done] R10. The host's `DbSchemaProvider` MUST add a `ban` entity mapping its
  engine's ban columns onto `BanDbDto` in `mapEntity`, translating the
  engine's ban enum onto the platform-canonical `WellKnownBanTypes` /
  `WellKnownBanSubjectTypes` vocabulary and mapping the host's "no expiry"
  sentinel to a `null` `expiresAt`.

- [host rule] R11. **A repeated ban extends, never shortens.** Every host's
  `BanCommand` handler:
  - an active ban of the same subject and `banType` exists, and the new term
    ends later than its term or is permanent → the host extends that ban to
    the new term (same row, same id) and returns it with the new `expiresAt`;
  - an active ban exists and the new term ends at or before its term → the
    host returns it as is, with its own `expiresAt`;
  - no active ban → the host creates a new row.

  A term is never shortened by `BanCommand`; a shorter term goes through
  `UnbanCommand` and a new `BanCommand`. So a repeated command with the same
  `expiresAt` is idempotent without an idempotency key: the second delivery
  returns the same ban unchanged.

- [host rule] R12. **A login ban by IP or HWID kicks.** A `GAME_LOGIN` ban whose
  subject is `IP` or `HWID` disconnects every online player connected with that
  value, on every host. A clientless character (an offline trader, an app session)
  is not matched by such a ban; its own `CHARACTER` or `ACCOUNT` ban reaches it. A
  host that does not follow the rule yet enforces such a ban from the next login.

- [done] R13. **One name per field.** The former `target*`, `reason`, `removedBans`,
  `WellKnownBanTargetTypes` and its `HARD` fan-out travelled next to the new names in
  api 0.133.0 and 0.134.0, read as a whole pair, and were removed in 0.135.0 once
  every production game server ran core 0.57.3 (2026-11-25). A reader ignores the
  former fields an older host still writes. `HARD` is an unknown `subjectType`
  (`VALIDATION_FAILED`, R9): the platform sends one command per subject.

## Compatibility

The removal of the former names (R13) is breaking for source, binary and wire: a host on api 0.134.0 or newer reads the
commands of the platform, and the platform reads the new names every such host writes. The steps for a host are in
`docs/public/{en,ru}/migration.md`.

The ban command, result and vocabulary types and `BanDbDto` themselves are older (`api/v0.67.0`); no adapter-core change
is required (commands route by `Message-Type`; the DTO rides the standard db-sync flow). A host on an older api jar
does not register the handlers and does not map the `ban` entity until rebuilt against the new api.

The full-row `BanResult` / `UnbanResult` (R2, R4) replaced the earlier `banIds` / `removedBanIds` + `{id, expiresAt}`
form and the `removed` flag in one api release (`api/v0.131.0`), with no deprecated fields; `bans` is never empty and
`source` is a `String` token: the host switches in one release, and the platform reads replies of a host not restarted
yet with its own legacy mapping until every game server of the tenant runs the new build (platform spec
`nx-gameservers/docs/specs/030-bans/spec.md` §2). A host on an older build may still return an active ban as is instead
of extending it (R11) until it is restarted on a build that follows the rule.

## Non-goals

- **Adapter-side ban enforcement / policy.** The adapter ships the contract; the
  host owns enforcement and policy rejection.
- **Multi-server or multi-subject commands.** A `BanCommand` names one subject on exactly one server (routed by
  `Server-Id`); the platform issues one command per subject. Rejected: a batch command (a partial failure inside one reply).
- **A dedicated `unban` sync DTO.** A removed ban surfaces as a `DELETED` op on
  the `ban` sync entity (consumers MUST explicitly handle `DELETED` — topics use
  bounded retention, not log compaction).

## Links

- Sibling reference (concrete-command handler + DTO migration pattern):
  [`docs/specs/009-commands/send-mail.md`](send-mail.md)
- Commands rail infrastructure: [`docs/specs/009-commands/spec.md`](spec.md)
- Up-channel sync infrastructure: [`docs/specs/003-db-sync/spec.md`](../003-db-sync/spec.md)
