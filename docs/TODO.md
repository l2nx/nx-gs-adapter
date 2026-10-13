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
