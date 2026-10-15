# Events - Online snapshot

> Owner: @n1rmata

## Problem

Operators and platform dashboards chart server population over time: the raw number of characters in the world and
its breakdown by activity (offline private stores, fishing, app-loaded characters). A game server knows these numbers
only inside its own process, so the platform sees nothing unless the host publishes them. The online family
(`serveronline`) carries them as a periodic snapshot, plus the server-started and server-stopping facts that share its
topic.

Counts are build-agnostic, so the buckets are a closed vocabulary of generic concepts in the api
(`ServerOnlineBucket`); a host publishes the ones that apply to it, and a new concept is an api release first, like every
other closed enum of the contract.

Audience: platform consumers (dashboards, time-series ingest) and host authors writing the snapshot builder.

## Requirements

> Siblings: [`008-messaging`](008-messaging.md) (`MessagingTopics.events.<family>` addressing, `Nx-Server-Id` and
> `Nx-Message-Type` headers, UUIDv7 idempotency), [`038-app-offline-sessions`](038-app-offline-sessions.md) (fills the
> `APP` and `OFFLINE_FISHING` buckets).

**Must:**

- [done] R1. `ServerOnlineSnapshotEvent` (`kafka.events.server.online`) is a `final` event class published through
  `NxEvents.publish(Object)` on the family `serveronline`, dispatched by `Nx-Message-Type`. Together with
  `ServerStartedEvent` and `ServerStoppingEvent` it is registered in `EventTypeRegistry` with a `null` partition key
  (round-robin; consumers group by the `Nx-Server-Id` header and order by the UUIDv7 `eventId`).
- [done] R2. Fields of `ServerOnlineSnapshotEvent`:
  - `UUID eventId` - UUIDv7; the upper 48 bits are the occurrence time. Consumers dedupe on it.
  - `Map<ServerOnlineBucket, Long> buckets` - non-negative counts; the wire key is the enum constant name. The
    constructor requires `TOTAL` and `UNIQUE`; a null map normalizes to empty (and then fails that check). The getter is
    unmodifiable. Gson bypasses the constructor, so a consumer re-checks the required keys.
  - `@Nullable Map<String, String> metadata` - open map of snapshot attributes (no counts); hosts may add keys without
    an api release, consumers ignore unknown ones.

  There is no top-level `total`: buckets are independent axes and overlap, so `TOTAL` cannot be derived as
  `sum(buckets)`; the host publishes it as an explicit entry. Java 8 source, hand-written builder, `equals` /
  `hashCode` / `toString`.

- [done] R3. `ServerOnlineBucket` (`kafka.events.server.online.model`) is a closed enum; the wire value is the constant
  name (`UPPER_SNAKE_CASE`). Buckets are independent axes with no disjointness invariant.

  Required:
  - `TOTAL` - every character in the world (offline private stores, app-loaded characters and phantoms included).
  - `UNIQUE` - distinct active human players, deduplicated by a host-defined identity tuple (for example HWID + IP among
    players that are neither in offline mode nor fake).

  Optional (a host publishes them when the concept applies; consumers tolerate absence):
  - `OFFLINE_PRIVATE_STORE` - characters whose activity is `OFFLINE_PRIVATE_STORE`: stores left by a disconnected client
    and app private stores alike.
  - `FISHING` - characters fishing at a client (live fishing only).
  - `APP` - every character placed in the world by an app command without a client
    ([`038`](038-app-offline-sessions.md)). How the platform combines it with the real count is a platform decision.
  - `OFFLINE_FISHING` - app-loaded fishers (app sessions with activity `OFFLINE_FISHING`).

  Each further offline action gets an `OFFLINE_<ACTION>` bucket named like its `ActivityType` value. Adding a constant is
  an api release; the platform enum and CHECK follow before a host publishes it. The platform treats an unknown key as
  a WARN plus a metric and skips that bucket row, never the partition.

- [done] R4. Publishing never blocks beyond the enqueue and never throws: a null event is a no-op with a WARN, an event
  of an unregistered type is dropped with a WARN, and a family without a topic in `MessagingTopics.events.serveronline`
  is dropped with a DEBUG (shown in the heartbeat's disabled-families slot).
- [host rule] R5. The host owns the cadence and the builder; the adapter has no scheduler and no pull SPI. The host
  schedules the snapshot with fixed delay (a stalled tick must not trigger a catch-up burst), iterates the players once
  per tick, applies its own predicates and publishes one event. An uncaught failure of a tick is logged and swallowed;
  the next tick runs normally.
- [host rule] R6. Counting rules. An activity bucket counts every character with that activity, whatever started it:
  an app fisher is in `OFFLINE_FISHING`, not in `FISHING`; an app private store is in `OFFLINE_PRIVATE_STORE` together
  with the stores of disconnected clients. Every counted character is also in `TOTAL`; every app-loaded character is in
  `APP`. `UNIQUE` skips players whose HWID or IP is unknown or a placeholder (a transient login-window client) instead
  of collapsing them into one sentinel entry: accuracy of `UNIQUE` beats reflecting the login window. A public online
  number is not part of the contract.

**Could:**

- [todo] R7. A configurable snapshot cadence ([`TODO §13`](../TODO.md#13-configurable-online-snapshot-cadence)).

**Non-goals:**

- Adapter-side scheduler or pull SPI: the host owns cadence, the adapter stays mechanism-only.
- Per-character online deltas: the presence event ([`013`](013-character-core-extension.md) R15) is the per-character
  fact.
- Typed fields per bucket: a map keyed by a closed enum means a new bucket is one constant, not a new DTO field.
- Host-invented bucket keys: a host-specific concept first becomes a generic `ServerOnlineBucket` value.
- Per-family queue counters: `EventsStats` aggregates across families.

### Edge cases

- **Empty world at the first tick.** The snapshot carries every required bucket as 0; explicit zeros, no suppression.
- **Publish after `onDisconnect`.** A tick already running may publish after the module released its handle; the
  no-op events facade of `ConnectContext` swallows it.
- **Reconnect mid-tick.** The `NxEvents` facade from `ConnectContext.events()` keeps its identity across reconnects, so a
  snapshot built before and published after lands on the new producer; the module caches the handle once.
- **Family disabled.** The platform did not configure the topic: the call is dropped with a DEBUG, nothing is enqueued.
- **Overlap.** A fishing player is in `FISHING`, `UNIQUE` and `TOTAL`; an app fisher is in `TOTAL`, `APP` and
  `OFFLINE_FISHING`, not in `FISHING`.

## Technical design

### Wire

One topic per tenant family (`<tenant>.gs.events.serveronline`), three message types: `ServerOnlineSnapshotEvent`,
`ServerStartedEvent`, `ServerStoppingEvent`. The started event is emitted once when the world is loaded and logins are
accepted; the stopping event once on graceful shutdown (a crash emits nothing and the platform's heartbeat timeout
catches it). The host suppresses both during scheduled restarts and reports `gm_only` in `metadata`
(`WellKnownServerStartMetadata`); the platform decides what to mute.

### Structure

- `nx-gs-adapter-api` `kafka.events.server.online`: `ServerOnlineSnapshotEvent`, `ServerStartedEvent`,
  `ServerStoppingEvent`; `model/`: `ServerOnlineBucket`, `WellKnownServerStartMetadata`.
- `nx-gs-adapter-core` `core.events`: `EventTypeRegistry` (three bindings, family `serveronline`), `NxEventsImpl`
  (generic dispatch, unchanged).
- Host: an events module that owns the schedule and the bucket builder; it takes the `NxEvents` handle in `onConnect`,
  starts the tick in `start()`, cancels it in `stop()`.

### Data flow

```
host scheduler (fixed delay)
  -> snapshot builder: one pass over the players, counters per bucket
  -> ServerOnlineSnapshotEvent(eventId = UUIDv7, buckets, metadata)
  -> NxEvents.publish
     -> EventTypeRegistry lookup -> family enabled? -> EventsPublisher queue
     -> Kafka record with Nx-Message-Type and Nx-Server-Id headers
```

### Decisions

- **Snapshot, not deltas.** Population stats suit a periodic snapshot; per-character login/logout deltas would force the
  platform to rebuild state and scale with login churn.
- **Host-pushed.** Symmetric with the other event families; cadence is per host rather than tunable from the platform.
- **Map keyed by a closed enum.** Open string keys let each host invent spellings (`app_fishing`) the platform could not
  aggregate or validate. Rollout order for a new bucket: adapter enum (api release), platform enum and CHECK, host.
- **Partition key `null`.** Snapshots are rare; ordering comes from the UUIDv7 `eventId` and grouping from the
  `Nx-Server-Id` header, so no key extractor is needed.

### Extension points

- **New bucket:** a constant in `ServerOnlineBucket`, then the platform enum and CHECK, then the host builder.
- **New message type on the family:** a new event class plus a binding in `EventTypeRegistry` (same family
  `serveronline`, distinct message type); the platform consumer must learn the type.
