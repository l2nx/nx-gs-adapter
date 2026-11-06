# Item snapshot

> Owner: @n1rmata
> Status: adapter side implemented 2026-11-04, not yet released.
> Counterparts: `nx-gameservers/docs/specs/093-item-snapshot-migration.md` (legacy mapping, table moves, rollout),
> `nx-gameservers/docs/specs/043-sessions/results.md` (session results storage).

## Problem

Events and command results describe a concrete item in eleven different ways. The same fact (which item, with what
enchant, elements and augmentation) has different field sets, different types (`int` or `long` ids, `int`, `Integer` or
`Long` enchant) and different names (`itemObjectId`, `itemId`), and most forms lose the augmentation, so the platform
cannot tell what actually moved. A market view, a mail history and a session result each need the same answer, and each
has to map another shape.

One value object replaces those shapes: it says what an item was at the moment of the event, and the event around it
says where the item went and how much of it moved.

## Requirements

> Siblings: [`043-session-results`](043-session-results.md) (`uniques` carry the snapshot),
> [`025-chat-events`](025-chat-events.md), [`014-events-raid`](014-events-raid.md),
> [`026-item-augmentation-sync`](026-item-augmentation-sync.md),
> [`039-private-store-state-event`](039-private-store-state-event.md),
> [`009-commands/catalog.md`](009-commands/catalog.md).

**Must:**

- [done] R1. The api MUST ship `domain.item.ItemSnapshot`: immutable, Java 8, with a builder, carrying `itemId`,
  `itemTemplateId`, `count`, `enchantLevel`, `attributes` and `augmentation` ([ItemSnapshot](#itemsnapshot)).
- [done] R2. `ItemAugmentationDbDto` MUST become `domain.item.ItemAugmentation` with the same wire shape.
- [done] R3. Location, owner and the quantity an event moved MUST stay in the event's own context (the movement, the
  line, the result), never in the snapshot.
- [done] R4. Every form under [Migrated forms](#migrated-forms) MUST carry a snapshot in field `item` in place of its
  loose item fields, in one api release that removes the old shapes.
- [done] R5. `ChatItemSnapshot` and `ItemAttributeDbDto` MUST be removed from the api.
- [done] R6. `StoreLine` MUST split into one class per side: a sell line carries a snapshot, a buy line carries a
  template with no instance.
- [done] R7. `RaidDropItem` MUST stay a template reference and name it `itemTemplateId`: the raid rolled a template,
  and no instance is known when the drop is recorded.
- [done] R8. `ItemDbDto.attributes` MUST become `Map<Attribute, Integer>`, the form the other item DTOs already use.
- [done] R9. `charId` MUST become `characterId` on every item command and result
  ([Item commands](#item-commands)).
- [done] R10. An instance id MUST be `long` and an enchant level `Integer` wherever a snapshot replaces them.

**Non-goals:**

- A shared base for references to a template: `MailItem`, `WantedLine`, `DeleteItemCommand` and the like describe an
  intent, not a fact about an instance ([What stays untouched](#what-stays-untouched)).
- A snapshot table on the platform: it stores the snapshot inside the row of its event.
- gd-sync: the catalog describes templates.

### Edge cases

- **A stack merged at the receiver:** `newItemId` of a movement is the id of the stack that absorbed the item; the
  snapshot still describes the moved item as it was.
- **A template with no enchant concept:** `enchantLevel` is `null`; `0` is unenchanted.
- **Elements not reported** (a build or path that does not carry them): `attributes` is `null`; empty means the item has
  none. Gson omits `null`, so the wire tells the two apart.
- **Stack size unknown at the moment of the event:** `count` is `null`. A consumer MUST NOT backfill it from a later
  inventory read.

## Technical design

### Overview

`ItemSnapshot` and `ItemAugmentation` live in `domain.item` beside the other item vocabulary, so any wire family may
use them without importing the sync package. Each migrated form keeps its context fields and replaces its item fields
with `item`.

### ItemSnapshot

| Field            | Type                       | Required | Notes                                                                                                               |
| ---------------- | -------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------- |
| `itemId`         | `long`                     | yes      | Instance object id, positive                                                                                        |
| `itemTemplateId` | `long`                     | yes      | Catalog template id, positive                                                                                       |
| `count`          | `Long?`                    | no       | Stack size at the moment of the snapshot, not the quantity the event moved; `null` when the host does not report it |
| `enchantLevel`   | `Integer?`                 | no       | `null` = no enchant concept, `0` = unenchanted                                                                      |
| `attributes`     | `Map<Attribute, Integer>?` | no       | Element to power; `null` = not reported, empty = none                                                               |
| `augmentation`   | `ItemAugmentation?`        | no       | `null` = not augmented                                                                                              |

`ItemAugmentation`: `option1Id` (`int`), `option2Id` (`Integer?`); the ids resolve against the gd option catalog, as
before.

Conventions as for every wire POJO: final class, final fields, constructor validating only the positive ids, builder,
`toBuilder`, value `equals` / `hashCode` / `toString`, `attributes` copied into an unmodifiable `LinkedHashMap`. Gson
bypasses the constructor, so a consumer re-checks what it depends on.

### Migrated forms

Field lists are the current DTOs. `item` is an `ItemSnapshot`.

**Events (core to platform)**

| Form                                     | Before                                                                                                                                                               | After                                                                                                                                                |
| ---------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ChatItemSnapshot` (removed)             | `long itemObjectId`, `int itemTemplateId`, `int enchantLevel`, `Map<String, Integer> attributes`, `ItemAugmentationDbDto? augmentation`, `Long? count`               | `ChatMessageEvent.items` becomes `List<ItemSnapshot>`; `itemObjectId` is `itemId`, the element keys are `Attribute`                                  |
| `MailItemMovement`                       | `long itemTemplateId`, `long itemId`, `long newItemId`, `long count`, `Integer? enchantLevel`, `Map<Attribute, Integer>? attributes`                                 | `ItemSnapshot item`, `long newItemId`, `long count` (moved)                                                                                          |
| `TradeItemMovement`                      | `long itemTemplateId`, `long itemId`, `long newItemId`, `long count`, `Integer? enchantLevel`, `Map<Attribute, Integer>? attributes`                                 | `ItemSnapshot item`, `long newItemId`, `long count` (moved)                                                                                          |
| `StoreLine`, sell side (`SellStoreLine`) | `Long? itemId`, `long itemTemplateId`, `Integer? enchantLevel`, `Map<Attribute, Integer>? attributes`, `long count`, `long unitPrice`, `long currencyItemTemplateId` | `ItemSnapshot item`, `long count`, `long unitPrice`, `long currencyItemTemplateId`                                                                   |
| `StoreLine`, buy side (`BuyStoreLine`)   | as above, `itemId` always `null`                                                                                                                                     | `long itemTemplateId`, `Integer? enchantLevel`, `Map<Attribute, Integer>? attributes`, `long count`, `long unitPrice`, `long currencyItemTemplateId` |
| `StoreState`                             | `CharacterPrivateStore type`, `String? title`, `boolean offline`, `List<StoreLine> lines`                                                                            | `type`, `title`, `offline`, `List<SellStoreLine> sellLines`, `List<BuyStoreLine> buyLines`; the side of `type` is filled, the other is empty         |
| `TradeLine`                              | `long itemTemplateId`, `Integer? enchantLevel`, `Map<Attribute, Integer>? attributes`, `long count`, `long unitPrice`, `long currencyItemTemplateId`                 | `ItemSnapshot item`, `long count`, `long unitPrice`, `long currencyItemTemplateId`                                                                   |
| `RaidDropItem`                           | `int itemId` (a template id), `long count`, `Integer? enchantLevel`                                                                                                  | `long itemTemplateId`, `long count`, `Integer? enchantLevel`; no snapshot (R7)                                                                       |

`TradeLine` gains an instance id it never had; a host fills it from the item that moved.

**Command lines and results**

| Form                            | Before                                                                                                                                  | After                                                                                      |
| ------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `SoldLine`                      | `int itemId`, `long itemTemplateId`, `Integer? enchantLevel`, `long count`, `long unitPriceAdena`                                       | `ItemSnapshot item`, `long count`, `long unitPriceAdena`                                   |
| `BoughtLine`                    | `int itemId`, `long itemTemplateId`, `Integer? enchantLevel`, `long count`, `long unitPriceAdena`                                       | `ItemSnapshot item`, `long count`, `long unitPriceAdena`                                   |
| `BuyLine`                       | `int itemId`, `long itemTemplateId`, `Integer? enchantLevel`, `Map<Attribute, Integer> attributes`, `long count`, `long unitPriceAdena` | `ItemSnapshot item`, `long count`, `long unitPriceAdena`                                   |
| `CreateItemResult`              | `Long itemId`, `Long countCreated`, `Long enchantLevel`, `ItemLocation location`                                                        | `ItemSnapshot item`, `Long countCreated`, `ItemLocation location`                          |
| `DeleteItemResult`              | `Long itemId`, `Long countDeleted`, `boolean fullyDeleted`                                                                              | `ItemSnapshot item`, `Long countDeleted`, `boolean fullyDeleted`                           |
| `TransferItemToCharacterResult` | `Long itemId`, `Long countTransferred`, `Long fromCharId`, `Long toCharId`                                                              | `ItemSnapshot item`, `Long countTransferred`, `Long fromCharacterId`, `Long toCharacterId` |

- `CreateItemResult.item` is the stack after the grant: when an existing stack absorbed the grant, that stack.
- `DeleteItemResult.item` and `TransferItemToCharacterResult.item` are the item before the operation.
- `BuyLine` is a command input and an optimistic lock: `item.itemId` finds the instance, `item.itemTemplateId`,
  `item.enchantLevel` and `item.attributes` with `unitPriceAdena` are re-verified, `null` keeps its meaning of "the
  offer carried none", an empty `item.attributes` map counts as `null` (the host compares
  `BuyLine.attributesForMatch()`), and any mismatch still fails with `OFFER_CHANGED`. `item.count` is not part of the match; the
  lot size is `count`. The line keeps its own checks (positive `count`, non-negative price, no overflow of their
  product, enchant in `0..127`); `BuyFromPrivateStoreCommand` compares `item.itemId` for repeats.

**Sync**

| Form        | Before                                                                                                                                                                                              | After                                                                                                                                                |
| ----------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ItemDbDto` | `long id`, `Long? itemTemplateId`, `Long? ownerId`, `Long? count`, `Integer? enchantLevel`, `ItemLocation? location`, `List<ItemAttributeDbDto>? attributes`, `ItemAugmentationDbDto? augmentation` | Same fields, `Map<Attribute, Integer>? attributes` and `ItemAugmentation? augmentation`. Not a snapshot: it is the live row, with owner and location |

`ItemDbDto.attributes` keeps its null rule: `null` when the tenant does not sync elements, empty when synced and none.

### Item commands

Only the names change; these commands keep their shape and take no snapshot.

| Command                          | Before                   | After                              |
| -------------------------------- | ------------------------ | ---------------------------------- |
| `CreateItemCommand`              | `charId`                 | `characterId`                      |
| `DeleteItemCommand`              | `charId`                 | `characterId`                      |
| `TransferItemToCharacterCommand` | `charIdFrom`, `charIdTo` | `fromCharacterId`, `toCharacterId` |
| every other api class            | `charId`, `*CharId`      | `characterId`, `*CharacterId`      |

`TransferItemToCharacterCommand` stays routed by the source character. `CharacterPresenceEvent.charId` becomes
`characterId` in the same release ([`043`](043-session-results.md)). Every other `charId` and `*CharId` of the api becomes `characterId` and `*CharacterId` in the same release: commands, results,
events and `RatingDbDto`. `charName` and `targetCharName` become `characterName` and `targetCharacterName`, and
`TransferCharToAccount*` and `TelegramCharLink*` become `TransferCharacterToAccount*` and `TelegramCharacterLink*`. The
class rename changes `Nx-Message-Type`, which is the simple class name.

A reader of the old form maps the old key onto the new property before it binds: the classes bind by constructor parameter
name.

### Wire compatibility

Every change above except `ItemAugmentation` is a breaking wire change. The adapter classes cannot read the old form:
their constructors throw on a required field the old form does not have. A reader of the old form needs its own parsing,
as `nx-gameservers/docs/specs/093-item-snapshot-migration.md` describes.

| Direction                 | Forms                                                                                                   | Who straddles                                                                                                                                                                                              |
| ------------------------- | ------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| core to platform, events  | chat, mail, private trade, store state, store purchase, raid kill, `CharacterPresenceEvent.characterId` | The platform, and nx-social for presence, read old and new form with their own private legacy mapping                                                                                                      |
| core to platform, sync    | `ItemDbDto.attributes` (array of `{type, value}`, then an object keyed by element)                      | The platform reads both forms                                                                                                                                                                              |
| core to platform, replies | `SoldLine`, `BoughtLine`, `CreateItemResult`, `DeleteItemResult`, `TransferItemToCharacterResult`       | The platform reads both forms                                                                                                                                                                              |
| platform to core          | item commands, every command with a character field, `BuyLine`                                                                                | The platform sends both forms from a private wire DTO: `charId` with `characterId`, the source / target pair likewise, the flat `BuyLine` fields with `item`. Each core reads its own and ignores the rest |

- The adapter keeps no `@Deprecated` copy and no alias: the host switches in one release.
- `Nx-Message-Type` of `TransferCharacterToAccountCommand` stays the old name on the platform side until every host restarted;
  a new host keeps a private class of the old simple name, so routing needs no alias in the adapter. `TelegramCharacterLinkCommand`
  has no sender on the platform.
- The numeric changes (`int` to `long`) are invisible on the wire.
- The element keys of chat do not change: they were the `Attribute` names as strings.
- `ItemAugmentation` keeps `option1Id` / `option2Id`, so only the Java package moves. The move is its own commit.
- db-sync computes its checksum in SQL over the raw columns, so the new `ItemDbDto.attributes` form resends no row by
  itself; a row in the old form simply stops arriving once its host restarted.

### What stays untouched

| Form                                                                       | Why                                                                             |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| `CreateItemCommand`, `DeleteItemCommand`, `TransferItemToCharacterCommand` | Intents; only the renames above. `CreateItemCommand.enchantLevel` stays `Long?` |
| `SellLine`                                                                 | Intent: an instance to list and a price, with `itemId` `int`                    |
| `MailItem`, `WantedLine`                                                   | References to a template                                                        |
| `ItemDeliveryError`                                                        | Best-effort reference to a failed attachment line, template and count           |
| `PurchaseItem`                                                             | A grant line of a purchase, with its own host-specific `params`                 |
| gd-sync DTOs                                                               | Templates, not instances                                                        |

### Structure

- `nx-gs-adapter-api` `domain.item`: `ItemSnapshot`, `ItemAugmentation`.
- `nx-gs-adapter-api` `kafka.events.privatestore.model`: `SellStoreLine`, `BuyStoreLine` replace `StoreLine`.
- Removed: `kafka.events.chat.ChatItemSnapshot`, `kafka.sync.db.item.ItemAttributeDbDto`,
  `kafka.sync.db.item.ItemAugmentationDbDto`, `kafka.events.privatestore.model.StoreLine`.

Living specs of the migrated forms change in the same commit as the code: `009-commands/catalog.md`,
`009-commands/accept-mail.md`, `014-events-raid.md`, `025-chat-events.md` (R18), `026-item-augmentation-sync.md`,
`039-private-store-state-event.md`, plus `nx-gs-adapter-api/CLAUDE.md`.

### Decisions

- **The snapshot holds no location, owner or moved quantity.** The same item in the same state appears in a movement, a
  line and a session result; those facts differ per event, and a field on the snapshot would be read as the item's own.
  `count` is the stack at that moment, kept because chat links and uniques need it.
- **`RaidDropItem` is not a snapshot.** Its `itemId` was a template all along, and a roll has no instance yet. A
  snapshot would force a fake `itemId`; the field takes the name the canon gives a template reference instead.
- **Two store line classes, not an optional `itemId`.** A buy line never has an instance, so one class would keep a
  nullable id and could not require a snapshot on the sell side. Two classes cannot share one Gson-decoded list, hence
  `sellLines` and `buyLines`.
- **`ItemDbDto` is not a snapshot.** It is the live row of an item and owns its owner and location.
- **`Map<Attribute, Integer>`, not a list of `{type, value}`.** An item has one power per element, and every event form
  already used the map.

## Rollout

One api release carries this spec, [`043`](043-session-results.md) and the runtime-sync start gate
([`006`](006-runtime-sync.md) R14). Development runs on the local module; the release reaches Maven Central before the
platform deploys to prod, because prod builds resolve the api from Central.

1. **Adapter:** one release, old shapes removed.
2. **Platform and nx-social:** the private legacy mapping and the two-form sends above, each marked `TODO: drop once`.
3. **Host:** switches in one release, at the morning restart.
4. **Gate:** every game server of the tenant restarted on the new host. The platform and nx-social then delete the
   legacy mapping and send one form.

The list of legacy mapping points, the table moves and their order are in
`nx-gameservers/docs/specs/093-item-snapshot-migration.md`.

## Open questions

- [ ] [assumed: `item.augmentation` of a `BuyLine` is not part of the optimistic lock in this release, because offers
      start carrying augmentation only with it. It travels and the host ignores it for the match.]

## Links

- Session results: [`043-session-results`](043-session-results.md)
- Platform: `nx-gameservers/docs/specs/093-item-snapshot-migration.md`, `nx-gameservers/docs/specs/043-sessions/results.md`
- Host counterpart: not linked here, this repository does not name tenant hosts.
- Epic: [l2nx/nx-platform#23](https://github.com/l2nx/nx-platform/issues/23)
