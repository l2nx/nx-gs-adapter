# Private store state event

> Owner: @n1rmata
> Status: design, not released. First carried by api `0.105.0`.

## Problem

The former `PrivateStoreSnapshotEvent` described one `(itemTemplateId, side)` order book, rebuilt by the host from every open
store. That shape cannot carry what a store is as a whole: its title, whether the trader stands in the world without a
client, the trader's other lines, or when the store opened. A consumer that wants "the stores of the traders on this
server" has to reassemble them from per-item books, and it cannot tell a closed store from a store that merely stopped
listing one item.

The platform needs the store itself as the unit of state, keyed by the trader.

Audience: platform market and trader-list authors; host authors wiring the publisher.

## Requirements

> Siblings: [`008-messaging`](008-messaging.md) (event envelope, partition keys),
> [`009-commands/spec.md`](009-commands/spec.md) (commands that open and close stores).

**Must:**

- [todo] R1. `nx-gs-adapter-api.kafka.events.privatestore.PrivateStoreStateEvent` MUST ship next to
  `PrivateStorePurchaseEvent`, on the `privatestore` family topic, routed by the simple name
  `PrivateStoreStateEvent`, with the trader's `charId` as the Kafka key (8-byte big-endian). Events of one trader are
  therefore ordered.
- [todo] R2. The event is the **full state** of one trader's store, never a delta. The consumer replaces everything it
  holds for `charId` with `store.lines`.
- [todo] R3. `store == null` is a **tombstone**: the trader has no market store any more (closed, sold out, logged out,
  switched to a non-market state). The consumer drops everything it holds for `charId`. A tombstone is sent once per
  close and MAY be repeated on the next full resend (R6); consumers treat a repeated tombstone as a no-op.
- [todo] R4. Payload:

  | Field      | Type                  | Notes                                                                |
  | ---------- | --------------------- | -------------------------------------------------------------------- |
  | `eventId`  | `UUID`                | UUIDv7; the occurrence time derives from it; consumers dedupe on it  |
  | `charId`   | `long`                | Trader character id; the partition key                               |
  | `store`    | `StoreState?`         | `null` = tombstone                                                   |
  | `metadata` | `Map<String,String>?` | Open, like the other privatestore events; unknown keys are tolerated |

  `StoreState`:

  | Field     | Type                    | Notes                                                                                    |
  | --------- | ----------------------- | ---------------------------------------------------------------------------------------- |
  | `type`    | `CharacterPrivateStore` | Closed: `SELL`, `PACKAGE_SELL` or `BUY`. `CRAFT` is not a market store and is never sent |
  | `title`   | `String?`               | The store sign; `null` when none is set, never blank                                     |
  | `offline` | `boolean`               | `true` while the trader stands in the world without a game client                        |
  | `lines`   | `List<StoreLine>`       | Never null; every line has `count > 0`; order is not significant                         |

  `StoreLine`:

  | Field                    | Type                     | Notes                                                                             |
  | ------------------------ | ------------------------ | --------------------------------------------------------------------------------- |
  | `itemId`                 | `Long?`                  | Item instance for `SELL` / `PACKAGE_SELL`; `null` for `BUY`                       |
  | `itemTemplateId`         | `long`                   | Item template                                                                     |
  | `enchantLevel`           | `Integer?`               | `null` = no enchant concept, `0` = unenchanted                                    |
  | `attributes`             | `Map<Attribute,Integer>` | Element attributes; absent / empty when none                                      |
  | `count`                  | `long`                   | Units still on offer (`SELL` / `PACKAGE_SELL`) or still wanted (`BUY`)            |
  | `unitPrice`              | `long`                   | Per unit; nominal in a `PACKAGE_SELL` store, whose real price is the bundle total |
  | `currencyItemTemplateId` | `long`                   | Currency item template                                                            |

- [todo] R5. The host publishes on **every change** of a store (open, line sold, price or title edit, going offline,
  close), within about a second of the change, so the platform sees a store as it is now.
- [todo] R6. The host MUST re-send every open store in full at least every 10 minutes, so a consumer that lost state or
  joined late converges.
- [todo] R6a. Staleness: a tombstone can be lost or arrive after the consumer's state was dropped (retention of the
  topic is 3 hours, a consumer may join later). A consumer MAY therefore treat a trader whose store was not refreshed
  within 25 minutes (two missed resends plus margin) as closed and drop it, as if a tombstone had arrived.
- [todo] R7. The consumer MUST be idempotent: the same state arrives repeatedly (R6) and Kafka delivery is
  at-least-once. It MAY ignore an event older than the state it already holds for the trader (`eventId` is a UUIDv7).
- [todo] R8. `PrivateStoreSnapshotEvent` and its `Offer` line are **removed in this release** (api `0.105.0`), with no
  `@Deprecated` copy and no `EventTypeRegistry` binding left behind (root `CLAUDE.md` -> breaking wire changes). The
  host publishes `PrivateStoreStateEvent` only. `PrivateStorePurchaseEvent` and `PrivateStoreSide` are unchanged.

## Notes

- The hot ASK / BID price books of the platform are derived from trader states; they are no longer a wire concept.
- A `BUY` line has no `itemId`: it targets a template. A `SELL` line carries the instance id so the platform can tell
  two stacks of the same template apart.
- Unknown `type` constants from a newer host are data the consumer cannot place: skip with a metric, never stop the
  partition (closed-vocabulary rule, `nx-gs-adapter-api/CLAUDE.md`).

## Migration

The snapshot event is removed in api `0.105.0`; the straddle lives on the platform, not in the adapter.

1. The platform releases a reader that accepts the new trader states **and** reads the old book snapshots of a
   not-yet-restarted host with its own private legacy DTO or raw-JSON mapping, never with adapter classes. The legacy
   piece is marked `TODO: drop once ...` with a backlog item naming the gate below.
2. api `0.105.0` ships to Maven Central.
3. The host switches in one release: it publishes only `PrivateStoreStateEvent`.
4. **Gate:** every game server of the tenant restarted on the new host. After it the platform deletes its legacy
   book-snapshot mapping.

## Related

- Command side of read mail shipped in the same api release:
  [`009-commands/read-mail.md`](009-commands/read-mail.md).
