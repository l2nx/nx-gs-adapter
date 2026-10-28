# Settings sync

> Owner: @n1rmata
> Status: implemented 2026-10-25, not released.
> Counterparts: `nx-gameservers/docs/specs/090-settings.md` (storage and `GET /settings`),
> `bohpts-core/docs/l2nx/011-game-settings.md` (a host's `GameSettingsProvider`).

## Problem

The platform shows a server's rules to the player before the player acts: whether an app session type is enabled, the
offline private-store fee, the mail fee, the enchant cap, the attribute limits. Today only the adapter's own app settings
reach the platform (`appsettings`, [`038`](038-app-offline-sessions.md)), and only when that file changes. The engine's
own configuration never leaves the server, so the app hardcodes values (an offline store fee constant in the frontend)
that drift from the server the moment a game designer edits a config and reloads it.

Both kinds of settings can change without a restart: the adapter reloads `l2nx-app.properties`, and an engine may reload
its configuration on an admin command the adapter cannot observe.

Audience: platform settings authors; host authors wiring a `GameSettingsProvider`.

## Requirements

> Siblings: [`030-gamedata-sync`](030-gamedata-sync/spec.md) §7 (settings topic routing),
> [`038-app-offline-sessions`](038-app-offline-sessions.md) (the app settings file and its `AppSettings` contents).

**Must:**

- [done] R1. Every settings entity MUST ride `<tenant>.gs.settings.sync` (`syncTopics.settings[<entityName>]`) in a
  `SettingsSyncEvent`, routed by `entityName`, key = the raw 16-byte `serverId`, header `Nx-Message-Type =
SettingsSyncEvent`. Each record is the full current settings of one server; the newest wins. Entities: `appsettings`
  (`AppSettings`, [`038`](038-app-offline-sessions.md#settings-snapshot)) and `gamesettings` (`GameSettings`, R4).
- [done] R2. The adapter MUST rebuild and send every settings entity it has **every 5 minutes, unconditionally** (no skip
  of an unchanged snapshot). The tick covers a configuration reload the adapter cannot observe and heals a snapshot the
  platform lost. The period is a constant, not a configuration key.
- [done] R3. Besides the tick, a snapshot MUST be sent at once on the platform connect handshake and, for
  `appsettings`, after every applied change of the app settings file. A `gamesettings` provider that is not ready yet on
  connect (`null` or a throw) is retried every 15 s until the first snapshot is built; a failed send of it is healed by
  the tick, like any failed send. A change-driven send of a snapshot
  equal to the last sent one apart from `updatedAt` is skipped; if the send it was compared with fails, the skipped one
  is sent instead. The tick never skips.
- [done] R4. `GameSettings` (api `kafka.sync.settings.game`, entity name `gamesettings`) MUST carry the engine's
  configuration **as the engine applies it**: the host resolves its configuration (derived values, fallbacks, one cap
  reused for another item kind) into the published value. `null` (a group or a nullable field) = the build has no such
  setting; inside `attribute` the attribute counts are required (a build without attributes publishes no group).
  `attribute.chances` and `attribute.limits` are keyed by the closed vocabulary `AttributeItemType`: an engine item type
  outside it is a new vocabulary value (adapter, then platform, then host), not a raw token.

  | Group          | Fields                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
  | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
  | `enchant`      | `maxWeapon`, `maxArmor`, `maxJewelry`: the server-wide caps the engine applies; `null` = no server-wide cap (scroll and item limits apply). Whether the engine refuses at the cap or clamps the attempt while spending the scroll is the engine's                                                                                                                                                                                                                   |
  | `attribute`    | `maxWeaponAttributes`, `maxArmorAttributes`; `oppositePairs`: `{attribute, opposite}` (an armor never holds both); `chances`: `{grade?, type, target?, chancePercent}`; `limits`: `{grade, type, target, maxPower}`, the **only** power-limit table: the host expands grade x type x target with its configuration applied (`0` = the type cannot be used on that grade); `chancePercent` is the server's base chance, per-account bonuses are not a server setting |
  | `mail`         | `feeAdena`, `feeAdenaPerAttachment`                                                                                                                                                                                                                                                                                                                                                                                                                                 |
  | `privateStore` | `offlineFeeItemTemplateId`, `offlineFeeItemTemplateCount`, `offlineFeePeriodHours`: the fee of the offline mode of a private store (a store with a live client is free); `null` = no offline fee                                                                                                                                                                                                                                                                    |

- [done] R5. `updatedAt` of every settings entity MUST be the moment the snapshot was built. The platform keeps a row
  only when its content changed, so its `updated_at` reads "these settings hold since".
- [done] R6. A host MUST provide `GameSettings` through `spi.provider.GameSettingsProvider`, discovered by
  `ServiceLoader`. The adapter calls it on every tick and on connect, so a reloaded configuration reaches the platform
  within one period. No provider registered: no `gamesettings` snapshot, nothing logged. The provider throws: one WARN
  per failure streak, no snapshot on that tick, the next tick retries.
- [done] R7. A settings entity absent from the tenant directory's `/connect` settings map is a configuration error: the
  adapter logs `ERROR` once per connect and skips that entity until the next connect.

- [done] R8. The canonical term for neck, ear and finger equipment is `jewelry`. The same api release renames
  `LifeStoneGrade.ACCESSORY` to `JEWELRY` (no deprecated copy); a host switches in its release, and the platform reads
  `ACCESSORY` from a not-yet-restarted host with its own legacy mapping (`nx-gamedata/docs/specs/025-augmentation-via-adapter.md`).

- R9. `AppSettings` MUST carry the server's chat settings in a `chat` group (`ChatSettings`), built from the
  `app.chat.*` keys of the app settings file ([`send-chat-message`](009-commands/send-chat-message.md) R33):
  `enabled`, `onlineSendChannels`, `offlineSendChannels`. Channels are `ChatChannel` names with `sendableFromApp()`
  (`CLAN`, `ALLIANCE`, `PARTY`, `WHISPER`), carried as strings so a platform reads a channel a newer host adds instead
  of failing; the host lists them in `ChatChannel` declaration order. An empty list means the app only reads chat in
  that character state; `enabled = false` refuses every send from a character, not the platform's own lines
  (announcements, staff personas). `ChatSettings` reads its lists null-safe and hands them out unmodifiable even when
  Gson bypasses its constructor. The group is `null` only from a host on an adapter older than it; the platform reads that
  as the behaviour of such a host (chat on, its built-in channels), not as "chat off". Additive: a platform older than
  the group ignores it.

**Non-goals:**

- Any rule enforcement from these values: the engine decides every action; the snapshot only lets the platform show
  the rules.
- Rates, drop and experience multipliers: not requested by a consumer.

## Technical design

### Publisher

`core.settings` holds `SettingsPublisher<T>`, one instance per entity name (send, key, missing-topic handling, the
last-sent memory of R3), the built-in module `SettingsModule` (`settings`) and
`GameSettingsSource`, which wraps the discovered provider. `SettingsModule` owns both publisher instances;
`AppSettingsRuntime` (`core.appsettings`) only exposes the live rules as a snapshot, a change listener the watcher fires
after every applied change, and whether this JVM is a login server. The dependency runs one way, `core.settings` ->
`core.appsettings`. The watcher no longer publishes the first load before connect: `start()` sends it.

The tick runs on the daemon thread `nx-settings-tick`, armed by `SettingsModule` on connect and stopped on disconnect
and shutdown; the app-sessions module no longer touches settings. `onConnect` only wires the publishers; `start()` sends
both entities at once (the R3 send on connect) and arms the tick. A login server without a provider arms no tick.
A snapshot built earlier than the last one accepted for its entity is dropped on every path (change, tick, connect), so
a slow provider call never overtakes a newer snapshot. `KafkaProducer.send()` runs outside the publisher's state
monitor: the producer's I/O thread reports a failed send through the same monitor and must never wait behind a
blocked send. Each
tick asks every source for its current snapshot; a provider call hops to `nx-io-N` like every SPI call, so a slow engine
read never stalls the tick of the other entity. Settings publication is part of the adapter core and does not depend on
the app-sessions module being enabled.

Volume: two records per server every 5 minutes.

### Contract additions (api)

- `kafka.sync.settings.game`: `GameSettings` (`ENTITY_NAME = "gamesettings"`, `updatedAt`, the four nullable groups),
  `EnchantSettings`, `AttributeSettings` (+ `AttributeOppositePair`, `AttributeChance`, `AttributeLimit`),
  `MailSettings`, `PrivateStoreSettings`. Java 8 immutable classes with builders, like `AppSettings`.
- `spi.provider.GameSettingsProvider`: `@Nullable GameSettings gameSettings(Instant builtAt)`; the host stamps
  `updatedAt = builtAt` (R5), `null` = not ready, no snapshot this time.
- `domain.item.AttributeItemType` (`STONE` … `ENERGY`) and `domain.item.AttributeTarget` (`WEAPON`, `ARMOR`): the
  vocabularies of `attribute.chances` and `attribute.limits`.
- `kafka.sync.settings.app.ChatSettings` (`enabled`, `onlineSendChannels`, `offlineSendChannels`) and the nullable
  `AppSettings.chat` (R9); `AppSettings`' public constructor takes it as a new parameter.

### Edge cases

- **Platform older than the entity:** it skips an unknown `entityName` with a WARN per record. Rollout puts the platform
  first (below).
- **Configuration reloaded twice within a period:** only the state at the next tick travels; the platform shows it at
  most 5 minutes late. An `appsettings` change travels at once (R3), so a disabled session type is never offered for
  long.
- **Snapshot lost on the platform** (consumer bug, database restore): the next tick restores it.
- **Kafka down:** the send fails like any settings send; the last-sent memory is cleared and the next tick sends again
  (a Kafka reconnect is not an adapter event; the platform connect handshake runs once per JVM).
- **Clock stepped back:** ordering between snapshots uses a monotonic build sequence, not `updatedAt`, so a wall-clock
  step never drops a newer snapshot.

## Tests

- Unit: the tick sends both entities unconditionally; change-driven sends skip an equal snapshot; `updatedAt` is the
  build moment; a throwing provider gives one WARN per streak and no snapshot; a missing map entry gives one ERROR per
  connect.
- Unit: `GameSettings` builders reject a negative cap, count or power; `null` groups serialize as `null`.
- Unit: the `chat` group carries the three `app.chat.*` values with their defaults; an absent group reads as `null`;
  empty channel lists survive the wire.

## Rollout

1. nx-gs-adapter releases: `commons` first (`ServiceProviders.single`, used by core, db-sync, gd-sync and runtime-sync),
   then api, core and the sync modules pinned to it. Harmless alone: no host provides `gamesettings` yet.
2. nx-gamedata on the new api with its legacy read of `LifeStoneGrade` `ACCESSORY` (R8) — before any host sends
   `JEWELRY`. Removal gate: no host of any tenant runs an api below this release.
3. nx-gameservers: `gs.settings` with kind `GAME`, ingest of `gamesettings`, `GET /settings`; nx-users: the
   `GS_SETTINGS_READ` catalog row.
4. nx-tenants: `gamesettings` in the `/connect` settings map.
5. Host: its `GameSettingsProvider` on the new adapter version.

## Related

- [`038-app-offline-sessions`](038-app-offline-sessions.md): contents of `AppSettings`.
- [`009-commands/item-modification.md`](009-commands/item-modification.md): the item-modification UI reads
  `gamesettings.enchant` and `gamesettings.attribute`.
