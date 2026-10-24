# nx-gs-adapter-api

Subproject of the `nx-gs-adapter` monorepo. See [`../CLAUDE.md`](../CLAUDE.md) for repo-wide
conventions (per-module versioning, Maven Central publishing, the one-release rule for breaking wire
changes, license).

This file is a **map plus the invariants**. It names packages and the contracts that are easy to get
wrong; it deliberately does not enumerate DTO fields — the classes are the source of truth and the
design rationale lives in `docs/specs/` (index: [`../docs/CLAUDE.md`](../docs/CLAUDE.md)). A prose
copy of the wire shape drifts silently; this one already had.

## Purpose

Contracts-only artifact: pure Java interfaces and POJOs defining the wire shape exchanged by the
L2NX game-server adapter and its consumers. Published as `app.l2nx:nx-gs-adapter-api`. Consumed by
`:nx-gs-adapter-core` and the sync modules on the adapter side, by `nx-tenants` (`/connect` handler)
and `nx-gameservers` / `nx-gamedata` on the platform side, and by host forks implementing the Tier-2
provider SPIs.

## Package map

Root package `app.l2nx.gs.adapter.api`.

| package                        | what lives there                                                                                                                                              | design     |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| `rest`                         | `/connect` request/response, Kafka credentials, `SyncTopics`, `MessagingTopics`                                                                               | 001        |
| `kafka`                        | `NxHeaders` — the wire-level header contract                                                                                                                  | 008        |
| `kafka.sync.db.<entity>`       | CDC per-entity DTOs                                                                                                                                           | 003, 005   |
| `kafka.sync.gd.<entity>`       | static game-data catalog DTOs, payload of `GameDataSyncEvent<T>`                                                                                              | 030        |
| `kafka.sync.runtime.<entity>`  | volatile runtime-state DTOs                                                                                                                                   | 006        |
| `kafka.sync.settings.<entity>` | host settings snapshots, payload of `SettingsSyncEvent<T>` (root of `kafka.sync.settings`)                                                                    | 038        |
| `kafka.events.<family>`        | outbound discrete-fact / snapshot event DTOs, grouped by family                                                                                               | per family |
| `kafka.commands.<group>`       | inbound command DTOs; the package root holds `NxCommand`, `CommandResult`, `CommandStatus` (+ nested `Tier`), `CommandProblem` and the `OwnerVerified` marker | 009        |
| `kafka.ops`                    | `HeartbeatEvent`; its stats and state payloads in `kafka.ops.model`                                                                                           | 001        |
| `spi`                          | `AdapterModule`, the two contexts, package-private `NoOp*`; sub-packages `provider`, `model`, `capability`                                                    | 002, 034   |

Current entity / family / group names (the directory listing is authoritative — check it before
assuming):

- **db sync**: `alliance`, `announcement`, `ban`, `character`, `clan`, `item`, `rating`
- **gd sync**: `armorsettemplate`, `augmentation.lifestone` (entity `lifestone`), `augmentation.targetgroup` (entity
  `augmentationtargetgroup`), `classtemplate`, `experiencelevel`, `gearscore`, `instancetemplate`, `itemoption`,
  `itemtemplate`, `npctemplate`, `recipetemplate`, `skill`, `soulcrystaltemplate`; the gd root holds the envelope
  `GameDataSyncEvent` and `StatModifier`, shared by `skill` and `itemoption`
- **runtime sync**: `character`, `character.activity.usage` (entity name `characteractivityusage`: a per-character usage pack, also rides the db channel,
  routed by entity name)
- **settings sync**: `app` (entity name `appsettings`)
- **events**: `account`, `castle`, `character`, `characterlog`, `chat`, `gameevents`,
  `mail`, `olympiad`, `premiumpurchase`, `privatestore`, `privatetrade`, `raid`, `schedule`,
  `server.online` (family key `serveronline`), `sync`; `push` — facts published by platform services (not the host) to
  `<tenant>.push.events` for nx-social pushes
- **commands**: `announcement`, `ban`, `captcha`, `character`, `gd`, `item`,
  `mail`, `offline.fishing`, `privatestore`, `session` (shared app-session refusal vocabulary), `sync`, `telegram`

The command group is a code-organization bucket only — the commands topic stays single.

Layout rule (spec 034): inside a slice, two or more supporting types of one kind (value objects,
enums, vocabulary enums) live in `model/`; the slice root keeps its commands / events and
singles. Package-private helpers (`*Lists`, `NoOp*`) stay with the classes that use them — moving
them would force them public. Entity DTO aggregates (`sync.db.*`, `sync.gd.npctemplate`) stay flat; `sync.gd.skill` keeps the aggregate
in its root and splits effect / condition / learn / produced types into sub-packages.

## SPI tiers

- **Tier-1** `AdapterModule` (`spi`) — a module plugged into adapter-core via `ServiceLoader`.
- **Tier-2** (`spi.provider`, mapping types in `spi.model`) — the per-domain provider SPIs a host
  implements: `DbSchemaProvider` (+ `EntityMapping`,
  `PrimarySource`, `ChildSource`, `ParentRef`), `RuntimeStateProvider` (+ `RuntimeEntityMapping`,
  `RuntimeRow`), and the gd catalog providers (`ItemTemplateProvider`, `SkillProvider`, …, plus the
  singleton `GearScoreRulesetProvider` and the optional `GameDataReadinessProvider` — absent means
  "always ready", see spec 030), and `OfflineSessionHost` (+ `OfflineSession`, `OfflineSessionPlacement`)
  — the game side of app sessions; none disables the engine, two fail it (spec 038).
- **Tier-3** `JdbcConnectionSource` (`spi.provider`) — how a host hands the adapter a pooled
  connection. Read-only is the consumer's call after every borrow (db-sync reads, the migrator and the
  adapter's own stores write), so a provider must not force it.
- **`SchemaMigrator`** (`spi.provider`, + checked `SchemaMigrationException`) — migrates the adapter-owned
  `l2nx_*` tables; implemented by the adapter's `nx-gs-db-migrator-liquibase5`, not by the host (spec 037).
- **Capabilities** (`spi.capability`) handed to modules through `ConnectContext` / `CommandContext`:
  `NxEvents`, `NxCommands` (+ `CommandHandler`, `HostExecutor`, `DeferredReply`), `NxSync`,
  `NxGameData`, `NxAppSessions` (+ `AppSessionStartRequest`, `AppSessionStartOutcome`, `ClientLoginOutcome` in
  `spi.model`; also reachable as the static `NxAdapter.appSessions()` for host code outside a module),
  `NxAppRules` (+ `AppRules`, `SpotOrder` in `spi.model.appsettings`; the live `l2nx-app.properties` values, also
  the static `NxAdapter.appRules()`, spec 038 R20),
  `NxCommandReceipts` (+ `spi.model.receipt`; via `CommandContext` and the static `NxAdapter.commandReceipts()`, spec
  040). Each ships a `NoOp*` fallback so a context wired without that runtime never returns null; the receipts
  fallback throws instead of doing nothing, since a dropped receipt write loses money.

They all live here so a host provider depends on the contracts artifact alone.

## Contracts worth calling out

- **Timestamps are UTC `Instant` only.** Every timestamp field in every wire DTO (`kafka.sync.*`,
  `kafka.events.*`, `kafka.commands.*`, `kafka.ops.*`, `rest.*`) MUST be `java.time.Instant` — never
  `OffsetDateTime`, `ZonedDateTime`, `LocalDateTime`, `LocalDate`, `LocalTime`, `java.util.Date`,
  `java.util.Calendar`, or `java.sql.{Date,Time,Timestamp}`. The platform operates strictly on UTC;
  any zoned/local type risks a host-timezone leak. Schema providers read source columns through
  `JdbcNulls.nullableInstantFromEpochMillis(...)` / `instantFromEpochMillisOrSentinel(...)` (in
  `:nx-gs-commons`), not `rs.getTimestamp(...).toLocalDateTime(...)`. Enforced at build time by
  `WireTimestampConformanceTest` (reflective classpath scan — the build fails on any violation).
- **SQL identifiers are validated, not quoted.** Anything passed via `EntityMapping` /
  `PrimarySource` / `ChildSource` (tableName / pkColumn / fkColumn / hashedColumns) MUST match
  `^[A-Za-z_][A-Za-z0-9_]{0,63}$`. Schema-qualified names, quoted identifiers, and anything outside
  that pattern are rejected at engine start — no runtime escaping is performed.
- **`SyncEvent` `DELETED` carries `payload=null`, and that is not a Kafka tombstone.** Topics use
  bounded retention, not log compaction, so consumers MUST handle the `DELETED` op explicitly rather
  than relying on the null-value tombstone protocol.
- **Closed vocabularies are enums, constant name on the wire.** `ActivityType` (`domain.character`),
  `SessionSource`, `LogoutReason`, `ServerOnlineBucket`, `UsageUnit`, `AppSessionRefusalReason`. A new value is an api
  release first, then the platform enum and CHECK, then the host. A wire consumer must treat an unknown constant as
  data it cannot place (skip with a metric), never as a reason to stop a partition.
- **`CharacterPresenceEvent` is validated in the constructor, and Gson/Jackson may bypass it.** `source` is required;
  `activity` exactly when `source=APP`; `resumed` only on `online=true` + APP; `logoutReason` exactly when
  `online=false`. A consumer re-checks what it depends on. For an APP event `sessionId` is the app session id, `ip` the
  initiator IP of the app request and `hwid` the snapshot the character carried.
- **`characteractivityusage` is packed per character.** One row per `characterId`, payload = the COMPLETE set of that
  character's usage rows (`CharacterActivityUsagesDto`); the platform replaces the character's rows with it, so an
  empty list means "no rows", not "unchanged". At most one row per `(type, key)`.
- **`ServerOnlineSnapshotEvent.buckets` keys are `ServerOnlineBucket` constant names.** Buckets are independent axes
  and overlap; `TOTAL` and `UNIQUE` are required, nothing is derived by summing.
- **Settings ride one topic.** `SyncTopics.settings` maps every settings entity to `<tenant>.gs.settings.sync`; the
  consumer routes by the envelope `entityName`. Each record is the full current settings of the server.
- **`OfflineSessionHost` is called from adapter IO threads, which block on it.** The host hops to its game thread
  itself (inline when already there). `NxAppSessions.start` / `stop` / `updateTypeState` return futures completed on
  the IO pool: wait on them from the consumer thread or a host thread, never from `ctx.io()`. A start never surfaces
  SQL: every table conflict is an `AppSessionStartOutcome` status. `onClientLogin` answers `PENDING` when its bounded
  wait runs out: the host refuses that login with "try again". `placeInWorld` returns the type state it changed instead
  of writing it, and `refusedForNow` makes a restore retry later. Sessions open before a restart are restored only after
  the host calls `NxAppSessions.hostReady()` once its world is loaded; a host that never calls it never gets them back.
  New app-session types use `characterId`, never `charId`
  (the legacy `charId` of older DTOs stays as it is on the wire).
- **`NxCommandReceipts` is the host's only door to the receipts table.** Operations taking a `Connection` run inside
  the host's transaction and never commit, roll back or close it; `...OwnTx` ones report a database failure as
  `false`. Every call throws `IllegalStateException` until the schema is migrated in the JVM. Status names
  (`ReceiptStatus`) are stored values.
- **App-session command refusals** carry `reason` (an `AppSessionRefusalReason` name), plus `retryAt` (ISO-8601
  instant) or `scope` (`AlreadyActiveScope`) where the reason defines them; the `CommandStatus` per reason is on the
  enum.
- **`CharacterRuntimeDto` must keep exactly ONE constructor.** It binds through implicit
  constructor-parameter names; adding an overload (even a back-compat one) makes creator detection
  ambiguous and consumers silently stop deserializing the whole channel.
- **Presence is encoded by omission.** `CharacterRuntimeDto.online`: `null`/omitted = ONLINE (byte
  budget), explicit `false` = one-shot OFFLINE tombstone, explicit `true` is allowed but redundant.
  Consumers MUST read omitted/`null` as `true`.
- **`CharacterDbDto.classes` is the full roster, and it does not say which class is active.** Exactly
  one entry is `MAIN`; the class being played is `CharacterDbDto.classId`. Per-class `exp` / `sp` are
  deliberate UNHASHED ride-alongs — read in `mapRow` but absent from `hashedColumns()`, because they
  tick on every kill — so they never trigger a sync event and are only as fresh as the source's last
  full store. The runtime channel carries the live values.
- **`CharacterDbDto.display` null ≠ empty appearance.** `null` = the provider does not sync
  appearance (the consumer keeps what it stored); a present object with `null` fields = "this value is
  unknown / not set" and overwrites. Colors are only what the player set persistently, in RGB — never
  the color a host renders after its own overrides (spec 035).
- **Partition keys are per event TYPE, not per family.** A family can mix keyed and round-robin
  types (`raid`: `RaidKillEvent` keys on `bossNpcId`, `BossRespawnSnapshotEvent` uses `null`). The
  authoritative mapping is one `register(...)` line per type in adapter-core's `EventTypeRegistry` —
  read it there; the same file also holds the `Nx-Message-Type` value used for polymorphic dispatch.
- **Augmentation chances are the host's own roll, not a model to replay.** `AugmentationOutcome.chancePercent` is the
  final marginal of one slot; slot marginals are not combinable, and `LifeStone` rejects outcomes of unlisted groups,
  repeats and slot sums over 100. Contract: `docs/specs/030-gamedata-sync/augmentation-contract.md`.
- **`SNAPSHOT_COMPLETE count=0` is ignored by the platform** (no-op + alert, nothing is deleted; reconcile is also
  skipped when count != rows received). A gd provider with nothing to give yet should return `null` (burst
  aborted, no marker) rather than an empty collection. See spec 030 §2.
- **`ConnectContext.io()` / `CommandContext.io()` (binary-breaking for external implementers).**
  `CommandContext.io()` is abstract — any external implementer (test doubles, alternate adapters)
  MUST implement it. `ConnectContext` has one canonical constructor that grows with every capability (`io`,
  `appSessions`, ...): build it with the builder. Blocking IO (JDBC, HTTP) MUST hop onto these
  executors. Sanctioned exception: the db-sync resync handlers resolve cascades synchronously because
  the ack needs the counts, bounded by `ResyncRowsCommand.MAX_PKS`.

## Conventions

- **Enum-like open-string fields are `UPPER_SNAKE_CASE`**; free-form identifiers (effect-handler
  names, icon paths, localized text) stay verbatim. Icon names are emitted as-is — the platform
  lowercases them consumer-side.
- **Item-template references are `itemTemplateId`** (+ `itemTemplateCount`); a concrete item instance
  is `itemId`.
- **Units live in the field name** — `respawnSec`, `reuseDelayMs`, `chancePercent`. Non-physical
  counts / ids stay unsuffixed.
- **Boolean fields carry no `is` prefix.**
- **Vocabularies are enums** (root `CLAUDE.md` → Naming conventions): an open vocabulary gets a lenient
  `fromToken(String)` returning `null` for an unknown token (delegate to `Vocabularies.fromToken(Class, String)`, never a hand-written loop), because hosts MAY publish tokens outside the canonical
  set and consumers must tolerate them. Legacy `WellKnown*` constant classes convert when their family is touched
  ([#33](https://github.com/l2nx/nx-gs-adapter/issues/33)).

## Constraints

- **Java 8 source + target** — no `var`, no `Stream.toList()`, no records, no `Map.of`, no text
  blocks, no switch expressions, no pattern matching. Stream API + lambda + Optional are fine.
- **Zero runtime dependencies** — pure JDK. No Spring, no Lombok, no Jackson, no Gson; JSON binding
  is the consumer's business. One exception: `org.jspecify:jspecify` for `@Nullable` / `@NonNull` —
  `RetentionPolicy.CLASS`, so no runtime cost. Wired as `api(libs.jspecify)` so consumers get the
  annotations transitively and can run their own nullness checking against the wire types.
- **POJOs, not records** (records are Java 14+): final fields, static `builder()`, `equals`/`hashCode`.
  Every multi-field DTO ships a hand-written `Builder`. The all-args constructor of a wire DTO stays public: platform
  consumers deserialize it with Jackson, which needs a visible creator. Producers build through `builder()`.
- **Public API → Javadoc mandatory**, by substance (semantics, units, null-rules, invariants), never
  a restatement of the field name.
- **No framework annotations** — `@Component`, `@JsonProperty`, `@NotBlank` and friends are forbidden;
  the artifact is consumed by both Spring and non-Spring sides.
- **Constructor parameter names are preserved** (`-parameters` javac flag) so parameter-name-binding
  deserializers build the POJOs without annotations.

## Versioning

Slash-namespaced tag `api/vX.Y.Z` releases this module independently. Fallback when no
`-Pnx-gs-adapter-api.version=...` is passed: the literal in this module's `build.gradle.kts`. Release
flow and the breaking-change rule (one release, no `@Deprecated`) live in [`../CLAUDE.md`](../CLAUDE.md).

## Testing

- **Naming**: `methodName_shouldExpectedBehavior` or `methodName_shouldExpectedBehavior_whenCondition`
- JUnit 5, no Mockito (pure DTOs — nothing to mock)
- Test what builders / `equals` / `hashCode` actually guarantee, not framework code
