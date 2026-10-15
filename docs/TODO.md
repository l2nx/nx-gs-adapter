# TODO

Living backlog of deferred work. A spec's "deferred" item gets an entry here in the same pass; when
it is picked up, it moves to "Done / moved into a spec".

## Open

### 1. Formalize captcha consequences in `SendCaptchaResult`

- **Want:** a typed, host-agnostic description of what the host did after a captcha check (kick,
  jail, account ban, escalation stage) instead of the free-form `metadata` map.
- **To decide:** whether a dedicated model is needed at all — bans and jails created by a failed
  check already reach the platform through the synced `BanDbDto` rows, so a reference to the ban
  (`banId`) may be enough; kick has no ban vocabulary yet.
- **Why:** consumers (nx-sac-sentinel dossier, admin UI) parse host-defined keys today, and the key
  set is explicitly unstable.
- **Related:** [`009-commands/captcha.md`](specs/009-commands/captcha.md) R4,
  [`009-commands/ban.md`](specs/009-commands/ban.md).

### 2. `HostExecutor.sync` timeout leaves the task queued

- **Want:** a `sync` timeout that means the task will not run: a task the game pool has not started
  yet is skipped, one already running is reported as such instead of as a timeout.
- **To decide:** the shape of the "already started" signal (return normally after waiting it out, or
  a distinct exception), and whether `UNAVAILABLE` replies keep `error.cause=host-executor-timeout`.
- **Why:** today the caller gets `UNAVAILABLE` and the queued task may still mutate game state later
  (kick, ban, mail, item delivery). Each non-idempotent handler would need its own claim flag;
  a host handler sidestepped it by switching to `host().async`.
- **Related:** [`009-commands`](specs/009-commands/spec.md).

### 3. Audit the other gd-sync DTOs for the canonical/raw shape

- **Want:** go through the remaining gd-sync DTOs (item, armor set, NPC, cubic, recipe, soul crystal, class, instance)
  and check them against the skill contract rules: a vocabulary value travels as a canonical api enum plus its
  `<name>Raw` engine token (N1), no lossy mapping (N2), and no assumption that a single game line
  (HighFive-only or Essence-only) is the one in play.
- **To decide:** which fields are real vocabularies worth an enum versus free engine data, and how each retyped
  field is rolled out (a retype is breaking, see the compatibility section of
  [`skill-contract.md`](specs/030-gamedata-sync/skill-contract.md)).
- **Why:** deferred from the skill contract. The item follow-up only adds fields: `ItemGrade` and
  `WeaponType.PISTOLS` already exist in the api.
- **Related:** [`030-gamedata-sync/skill-contract.md`](specs/030-gamedata-sync/skill-contract.md),
  [`030-gamedata-sync/spec.md`](specs/030-gamedata-sync/spec.md) §7.

### 4. Mail refuse / return and delete commands

- **Want:** platform commands for the recipient-side actions that stay in the game client after
  `AcceptMailCommand`: refuse / return a mail and delete a mail.
- **To decide:** whether they are needed at all (no functional requirement yet), and the shape: both would
  reuse the receipt and unknown-outcome model of `accept-mail.md`, and a COD mail's refusal returns the
  attachments to the sender.
- **Why:** deliberately out of scope of the accept design; recorded so it is not lost.
- **Related:** [`009-commands/accept-mail.md`](specs/009-commands/accept-mail.md) non-goals.

### 5. Command: send a character to a peace zone

- **Want:** a platform command that places an offline character at one of a configured list of peace-zone
  points (host-configured, for example the main town hubs), so that offline mail send and accept, which
  require a peace-zone position, become usable for characters that logged out in the field.
- **To decide:** the wire shape (target point key vs host-chosen), who may issue it (owner only, with the same
  ownership check as the other player commands), whether it costs anything, and what happens to an offline
  trader's seat.
- **Why:** offline send with attachments and offline accept refuse a character whose last position is outside
  a peace zone, which strands characters that logged out in the field.
- **Related:** [`009-commands/send-player-mail.md`](specs/009-commands/send-player-mail.md) R9,
  [`009-commands/accept-mail.md`](specs/009-commands/accept-mail.md) R12.

### 6. Missing config: one WARN and `DISABLED`, not an error

- **Want:** a host started without `l2nx.properties` (and without `-Dl2nx.*` overrides) logs a single
  WARN ("adapter not configured, disabled") and moves to `DISABLED`, the same way an explicit
  `l2nx.enabled=false` does.
- **To decide:** the WARN wording and which signal counts as "not configured" (no file and no
  `l2nx.*` system property). A present config with `l2nx.enabled=true` and a missing or invalid
  required key stays an ERROR and `FAILED`.
- **Why:** `ConfigResolver.resolve()` reads the required keys (`l2nx.gs-key` / `l2nx.ls-key`,
  `l2nx.platform-url`) before `l2nx.enabled`, so a host run locally without a config logs
  `Adapter failed to start due to config error` with a stack trace and ends in `FAILED`. A local
  login or game server run looks broken although nothing is wrong.
- **Related:** [`001-adapter-bootstrap.md`](specs/001-adapter-bootstrap.md).

### 7. RecurringSchedule to RRULE

- **Want:** replace `RecurringSchedule` (weekly weekday + time slots) on `BossRespawnEntry`, `CastleSnapshotEntry` and
  `GameEventEntry` with an RRULE (RFC 5545) in UTC (`DTSTART:<UTC>Z` + `RRULE:...`, no `TZID`; the host converts its
  local server time to UTC), one-off moments stay ISO-8601. This is the platform canon and what
  `characteractivityusage.resetSchedule` already uses.
- **To decide:** whether the three entries carry the RRULE next to the old schedule for a window; which host cron shapes
  are not expressible as a weekly rule today and become expressible.
- **Why:** the weekly-slot model cannot express monthly or interval schedules, and two schedule formats in one api is a
  trap for consumers.
- **How:** one api release adds the RRULE field and removes the old schedule (no `@Deprecated` copy, see the root
  `CLAUDE.md`); the straddle lives on the platform only (it reads the old shape of a not-yet-restarted host with its
  own private legacy mapping until every game server of the tenant restarted on the new host, then drops that branch).
- **Related:** [`017`](specs/017-character-runtime-activity.md) R13,
  [`038`](specs/038-app-offline-sessions.md) Rollout (phase 4, first backlog item).

### 8. Move `l2nx_command_receipts` SQL from the host into an adapter store

- **Want:** an adapter-side receipt store (like `OfflineSessionStore`) owning the SQL of `l2nx_command_receipts`, so the
  host stops carrying its own queries against an adapter-owned table.
- **To decide:** the store's API and how the host's existing claim / replay / boot-recovery logic maps onto it without
  changing the at-most-once semantics of [`009-commands`](specs/009-commands/spec.md) R11.
- **Why:** [`037`](specs/037-schema-migrations.md) moves only the DDL; the table is adapter-owned but its SQL still lives
  in the host.
- **Related:** [`037-schema-migrations`](specs/037-schema-migrations.md),
  [`009-commands/send-player-mail.md`](specs/009-commands/send-player-mail.md) R6.

### 9. INSTANCE-type activity usage

- **Want:** report a character's presence in instances (for example training, labyrinth, olympiad-like instances) as
  `characteractivityusage` rows of type `INSTANCE`, with `key` = the instance template id.
- **To decide:** the new `ActivityType` value (the enum is closed, so `INSTANCE` is an api release first), which
  instances count, and how `used` / `limit` map onto instance entry limits.
- **Why:** the platform wants one place for "how much of each limited activity is left", instances included.
- **Related:** [`017-character-runtime-activity`](specs/017-character-runtime-activity.md) R16.

### 10. Session report design

- **Want:** a separate design session for the general session report (totals, items by source, activity sections),
  currently an initial sketch in [`038`](specs/038-app-offline-sessions.md) › "Session report - initial design, not
  approved". Not implemented in phase 1: no adapter column, no field on the presence event, no platform table.
- **To decide:** where the report travels (for example on the end presence event), the field names, the `source`
  vocabulary, activity section shapes, size limits, who may read it.
- **Why:** the sketch is enough for offline fishing notifications but is not an approved contract.
- **Related:** [`038`](specs/038-app-offline-sessions.md), [`013`](specs/013-character-core-extension.md) R15.

### 11. Report of a CLIENT session

- **Want:** fill the same report mechanism (once it is designed, item 10) for sessions of a live game client, not only
  for app sessions.
- **To decide:** where the host collects the numbers (in-game "current session" window source), the cost per logout.
- **Why:** the platform keeps the history of every session; no session has a report until item 10 is designed.
- **Related:** item 10.

### 12. Liquibase 4.x wrapper for a Java 8/11 host

- **Want:** a `nx-gs-db-migrator-liquibase4` wrapper (Java 8 bytecode, `compileOnly` Liquibase 4.x, the last 4.x line)
  implementing `SchemaMigrator` over the same `nx-gs-db-migrations` changelog, for a host that cannot run Java 17.
- **To decide:** the exact 4.x pin (re-check that it is the last 4.x release), and whether a database migrated under the
  4.x wrapper must validate under the 5.x wrapper (unchanged checksums, so a host may upgrade Liquibase later).
- **Why:** only the Liquibase 5 wrapper ships now ([`037`](specs/037-schema-migrations.md)); no current host needs the
  4.x line, so it is not built speculatively. Build it when a Java 8/11 host appears.
- **Related:** [`037-schema-migrations`](specs/037-schema-migrations.md) R5.

### 13. Configurable online snapshot cadence

- **Want:** the online snapshot interval as a host configuration property instead of a constant.
- **To decide:** whether the platform should be able to tune it per server, and the lower bound.
- **Why:** the host builds the snapshot on a fixed delay; no operator has asked for another interval yet, so it is not built.
- **Related:** [`011-events-online-snapshot`](specs/011-events-online-snapshot.md) R7.

## Done / moved into a spec

- **`SendMailCommand` / `SendMailResult` / `ItemDeliveryError` Javadoc aligned with the receipt contract** - retry
  replays or is refused, never delivers twice; the reference host is all-or-nothing with `itemErrors` empty
  (`api/v0.102.0`). See [`009-commands/send-mail.md`](specs/009-commands/send-mail.md).
- **Deprecated boss-kind and division shim, raw-seconds activity keys** - removed in `api/v0.93.1`
  (`RaidBossKind`, `RaidKillEvent.bossKind`, `BossRespawnEntry.kind`, `WellKnownBossDivisions`,
  `WellKnownBossMetadata`, `WellKnownActivityMetadata.ELAPSED_SECONDS` / `SECONDS_REMAINING` /
  `SECONDS_TO_NEXT_TIER`). See [`014-events-raid`](specs/014-events-raid.md) R4,
  [`017-character-runtime-activity`](specs/017-character-runtime-activity.md) R4.
- **`skill_type` as an api enum `SkillType`** - decided against in the skill contract (`api/v0.101.0`): the skill
  type is an engine-specific axis with no cross-line meaning, so it travels raw as `Skill.skillTypeRaw`; the host
  derives its meaning into canonical effects (`SkillEffectType`, `derived = true`). Filters by skill kind move to
  effect types. See [`030-gamedata-sync/skill-contract.md`](specs/030-gamedata-sync/skill-contract.md).
- **Private-store contract renames and `dropped` removal** - done in the fee/accept wave without a compatibility
  window (the commands are not open to players yet): `tax` is `buyerFeePercent` (`Integer`, required),
  `taxAdena` is `buyerFeeAdena`, `MAX_TAX_PERCENT` is `MAX_FEE_PERCENT`, the `tax_*` metadata keys are gone,
  `StartPrivateStoreResult.dropped` and `ALL_LINES_DROPPED` are removed. See
  [`009-commands/catalog.md`](specs/009-commands/catalog.md) › Breaking changes of this release and
  [`019`](specs/019-death-status-ratings-events.md) R26. Former items 4 and 5.
