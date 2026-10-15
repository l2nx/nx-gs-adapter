# Events — Online snapshot

> Owner: @n1rmata

## Problem

Operators and platform-side dashboards need to chart server population over time —
both raw "how many players are online" and the breakdown by activity bucket
(offline-trade vs. real play vs. fishing vs. phantoms). Today a host may render
this overlay only inside the game-client character-list window: a custom UI
iterates the online players on demand, applies inline predicates
(offline-mode, fishing, fake-player), and draws the counts. Nothing
leaves the JVM. The platform sees nothing.

The premium-purchase rail (`events.premiumpurchase` family) shipped in Phase 3 already
proves the wire-level pattern for discrete in-game facts: per-family Kafka topic,
abstract base class, `Nx-Message-Type` header dispatch, UUIDv7 `eventId`,
host-pushed via `NxEvents.publishX(...)`. Online stats are the next family to
ride that rail — but as periodic snapshots rather than per-fact deltas. Stat
cardinality (counts per bucket) is build-agnostic, so the buckets are a closed vocabulary of
generic L2 concepts in the api (`ServerOnlineBucket`); a host publishes the ones that apply to its build, and a new
concept is an api release first, like every other closed enum of the contract.

Audience: platform-side consumers (dashboards / TSDB ingest); host-side authors
plugging in a snapshot-builder.

## Requirements

> Sibling feature carrying the wire dispatch plumbing:
>
> - [`messaging`](008-messaging.md) — `MessagingTopics.events.<family>` > topic addressing, `Nx-Server-Id` connection-scoped header, > `Nx-Message-Type` per-record header, UUIDv7 idempotency. UNCHANGED by > this slice.

**Must:**

- [todo] R1. `nx-gs-adapter-api.kafka.events.serveronline.ServerOnlineSnapshotEvent` MUST ship as
  the abstract base for the `online` family — empty body, `protected` no-arg
  constructor, type-bound for `NxEvents.publishServerOnlineSnapshot(ServerOnlineSnapshotEvent)`. Mirrors
  `events.premiumpurchase.PremiumPurchaseEvent` exactly.

- [todo] R2. `nx-gs-adapter-api.kafka.events.serveronline.ServerOnlineSnapshotEvent` MUST
  ship as the Phase-1 concrete subtype with the following fields:
  - `UUID eventId` — REQUIRED. UUIDv7; the upper 48 bits encode the snapshot
    occurrence timestamp. Platform consumers dedupe on this id.
  - `@Nullable Map<ServerOnlineBucket, Long> buckets` — bucket → count map; the wire key is the enum constant name
    (`UPPER_SNAKE_CASE`). Every snapshot MUST carry `TOTAL` and `UNIQUE`; hosts SHOULD additionally publish the
    other buckets whose concept applies. Hosts cannot invent bucket keys. Null at the constructor normalizes to an
    empty map; getter returns an unmodifiable view.

  No top-level `total` field — buckets are independent axes of online and may overlap (e.g. a fishing player is
  also in `UNIQUE`), so `TOTAL` cannot be derived as `sum(buckets)`.
  The host publishes `ServerOnlineBucket.TOTAL` as an explicit map entry.

  POJO + hand-written Builder + `equals`/`hashCode`/`toString` + Java-8 source.
  Constructor parameter names preserved for Gson `-parameters` deserialization.

- [todo] R3. `nx-gs-adapter-api.kafka.events.serveronline.ServerOnlineBucket` MUST ship as a closed enum of the
  online axes. The wire value is the constant name (`UPPER_SNAKE_CASE`). The buckets are independent axes: a character
  may count in several of them and there is no disjointness invariant between them (no
  `total >= unique + offline_trade + app` check either).

  **Required** (host MUST publish):
  - `TOTAL` — total character presence (includes offline-trade, app-loaded characters and phantoms).
  - `UNIQUE` — distinct active human players, deduplicated by a host-defined identity tuple (e.g. HWID + IP among
    players that are neither in offline mode nor fake).

  **Optional** (host SHOULD publish when the concept applies; consumers MUST tolerate absence):
  - `OFFLINE_TRADE` — characters whose activity is `OFFLINE_TRADE`: stores left by a disconnected client and app
    private stores alike.
  - `FISHING` — characters currently fishing.
  - `APP` — every app-loaded character, i.e. every character placed in the world by an app command without a client
    ([`038-app-offline-sessions`](038-app-offline-sessions.md)). How the platform combines it with the real count (for
    example a public total of real plus app characters) is a platform decision and not part of this contract.
  - `OFFLINE_FISHING` — app-loaded fishers (the app sessions with activity `OFFLINE_FISHING`).

  Each further offline action (mail-style offline actions, enchant, attribute) gets an `OFFLINE_<ACTION>` bucket with
  the same name as its `ActivityType` value. There are no `app_fishing` / `app_private_store` style keys.

  Adding a constant is an api release; the platform enum and CHECK follow before the host publishes it. The platform
  treats an unknown key arriving from a host as a WARN plus a metric and skips that bucket row, never the partition.
  `WellKnownServerOnlineBuckets` (string constants, `lower_snake_case` wire values) is removed in the same release that
  adds the enum; the platform reads the lower_snake keys of not-yet-restarted hosts with its own private legacy
  mapping until every game server of the tenant restarted on the new host.

- [todo] R4. `nx-gs-adapter-api.spi.capability.NxEvents` MUST gain a single new method
  `void publishServerOnlineSnapshot(ServerOnlineSnapshotEvent event)` mirroring `publishPremiumPurchase` exactly:
  null event → silent no-op + WARN log, unregistered subtype → drop + WARN,
  family disabled (no topic in `MessagingTopics.events.serveronline`) → drop + DEBUG,
  game-loop-safety contract (never blocks beyond enqueue, never throws).

- [todo] R5. `nx-gs-adapter-core.events.EventTypeRegistry` MUST gain a binding
  for `ServerOnlineSnapshotEvent`: family `"serveronline"`, message-type
  `"ServerOnlineSnapshotEvent"`, partition-key extractor returning `null` (round-robin
  partitioning; consumer groups by `Nx-Server-Id` header and orders by the
  UUIDv7 `eventId` timestamp).

- [todo] R6. `nx-gs-adapter-core.events.NxEventsImpl` MUST implement
  `publishServerOnlineSnapshot(ServerOnlineSnapshotEvent)` with the same dispatch + null-check + family-disabled
  short-circuit logic as `publishPremiumPurchase`. No new internal infrastructure —
  reuses `EventsPublisher` / `EventEnvelope` / `EventTypeRegistry` as-is.

- [todo] R7. The host MUST extend its existing events module (the same module that owns
  the `events.premiumpurchase` wiring) to additionally:
  - `onConnect(ctx)` — bind the captured `ctx.events()` handle into a new
    `OnlineSnapshotBuilder` static facade alongside the existing
    `PremiumPublisher.bind(...)` call.
  - `start()` — schedule a periodic snapshot task on the host scheduler
    via fixed-delay scheduling (NOT fixed-rate scheduling — a stalled tick
    must NOT trigger catch-up bursts) at a 30-second period.
  - `stop()` — cancel the scheduled task.
  - `onDisconnect()` — also release the `OnlineSnapshotBuilder` handle.

  The snapshot-builder iterates the online players once per tick,
  walks every player, applies the host predicates (offline mode,
  fishing, fake player, HWID, IP address) and computes the
  four base buckets (plus `APP` / `OFFLINE_FISHING`, see R10):
  - `TOTAL` — every player in the iteration (including phantoms and
    offline-trade).
  - `UNIQUE` — `Set<(hwid, ip)>.size()` accumulated across players that are
    neither in offline mode nor fake, and both HWID and IP are
    meaningful (HWID != `"N/A"`, IP != `"N/A"` and != `"Disconnected"`).
  - `OFFLINE_TRADE` — player is in offline mode.
  - `FISHING` — player is fishing, regardless of offline-trade / phantom state.

  Builds `ServerOnlineSnapshotEvent` with UUIDv7 `eventId`, calls
  `nxEvents.publishServerOnlineSnapshot(event)`. Any uncaught `Throwable` is
  logged at DEBUG and swallowed — game-loop safety identical to
  `PremiumPublisher`.

  No separate `AdapterModule` registration — `events.serveronline` rides the same
  host events module entry in `META-INF/services` as `events.premiumpurchase`.

- [todo] R10. `ServerOnlineBucket` carries `APP` and `OFFLINE_FISHING` (R3). The host counts every character of an
  active app session into `APP`. An activity bucket counts every character with that activity, whatever started it:
  an app fisher (activity `OFFLINE_FISHING`) is in `OFFLINE_FISHING`, not in `FISHING` (live fishing only); an app
  private store (activity `OFFLINE_TRADE`) is in `OFFLINE_TRADE` together with the stores of disconnected clients. Every
  such character also counts in `TOTAL`; axes overlap by design (R3). A public online number is not designed (no such entity
  exists): the contract is the axes themselves, the platform decides how to combine them.

**Should:**

- [todo] R8. Cadence is host-managed and currently hardcoded to 30 seconds.
  Promoting to a `Config` property is a follow-up if operators ask for it.

**Could:**

- [todo] R9. Per-character online-presence delta events
  (`OnlineLoginEvent` / `OnlineLogoutEvent` / `OnlineStateChangeEvent`) on the
  same `online` family — out of scope for this slice. Snapshot-only ground
  truth is sufficient for Phase 1 dashboarding.

**Non-goals:**

- **Adapter-side scheduler / pull-SPI.** Host owns cadence, identical to
  `events.premiumpurchase` shape. Adapter-core stays mechanism-only.
- **Per-family heartbeat counters.** `EventsStats` aggregates queue/published/
  dropped/failed across all families today; per-family breakdown is a separate
  enhancement that lands when there are 3+ families.
- **Typed fields per bucket.** A `Map<ServerOnlineBucket, Long>` keyed by a closed enum is the deliberate choice —
  a new bucket is one enum constant, not a new DTO field.
- **Host-invented bucket keys.** The vocabulary is closed; a host-specific concept first becomes a generic
  `ServerOnlineBucket` value.

### Edge cases

- **Empty player set on startup tick.** Builder produces a snapshot with
  every required bucket = 0; consumer sees `TOTAL=0` and a flat dashboard
  curve. No special-case suppression — observability prefers explicit zeros.
- **Snapshot publish during `onDisconnect`.** Module's `stop()` cancels the
  scheduled task before `onDisconnect()` releases the handle. Race window:
  a tick already in `run()` may call `publishServerOnlineSnapshot` after handle release;
  `NoOpEvents` swallows it (per `ConnectContext` normalization).
- **Reconnect mid-tick.** The `NxEvents` façade handed to the module via
  `ConnectContext.events()` has stable identity across reconnect cycles —
  the adapter swaps an internal `AtomicReference` to the live publisher on
  every reconnect, so a snapshot built before reconnect and published after
  lands on the freshly-reconnected producer without the module re-acquiring
  the handle.
- **`publishServerOnlineSnapshot` thrown from inside the host (e.g. snapshot-builder bug).**
  Tick logs at DEBUG, skips the publish, schedules the next tick normally.
  No backoff — transient bug fixes itself on next tick.
- **Family disabled (platform did not configure `MessagingTopics.events.serveronline`).**
  Adapter logs DEBUG once per call, increments nothing, surfaces via the
  `events.disabled-families` heartbeat slot (existing mechanism).
- **Buckets overlap.** A fishing active player counts toward both `FISHING`
  and `UNIQUE` and `TOTAL`; an app fisher toward `TOTAL`, `APP`, `OFFLINE_FISHING` and `FISHING`. `TOTAL` is published as a separate map entry —
  the consumer never tries to derive it from `sum(buckets)`.
- **Unidentifiable client.** Active non-phantom player whose `_client` is
  transiently null (login / logout boundary) reports HWID=`"N/A"` and
  IP=`"N/A"`. The bucket-builder skips that row from the unique-set entirely
  rather than collapsing it into a `("N/A","N/A")` sentinel — accuracy of
  `unique` is more important than reflecting transient login-window state.

## Open questions

- [resolved: snapshot-only, no per-character deltas in Phase 1 — user confirmed
  during design that per-character stats land later as a separate slice.]
- [resolved: host-pushed (no SPI / no engine in adapter-core) — symmetric with
  `events.premiumpurchase`. Adapter does not own the cadence.]
- [resolved: `Map<ServerOnlineBucket, Long>` keyed by a closed `UPPER_SNAKE` enum (replaces the open
  `Map<String, Long>` + `WellKnownServerOnlineBuckets` string constants; rejected typed-fields-per-bucket
  alternative). A new bucket is an api release, then the platform enum and CHECK, then the host.]
- [resolved: base set `TOTAL` / `UNIQUE` / `OFFLINE_TRADE` / `FISHING` (api/v0.26.0), `APP` and `OFFLINE_FISHING`
  added with the app sessions. `online` / `real` / `phantoms` were removed — `UNIQUE` (distinct active humans by
  host-defined identity tuple) carries the operator-facing audience number.]
- [resolved: partition-key = `null` (round-robin). Snapshot cadence is low
  (~one per 30 sec × N servers); ordering preserved per-server via the UUIDv7
  `eventId` timestamp. Consumer groups by `Nx-Server-Id` header.]
- [resolved: buckets are independent axes; overlap is allowed and there is no disjointness invariant (R3, R10).]
- [assumed: 30-second default tick interval. Matches the typical pre-existing
  `OnlinePlayers` announce cadence and gives ~2 ppm baseline traffic per
  server. Promoting to a Config knob is R8.]

## Links

- App sessions that fill the `APP` and `OFFLINE_FISHING` buckets: [`038-app-offline-sessions`](038-app-offline-sessions.md)
- Platform: `nx-gameservers` online-snapshot spec (public total and the staff series are decided there)
- Sibling feature (events runtime + per-family fanout):
  [`docs/specs/008-messaging.md`](008-messaging.md)
- Sibling reference (premium-family wire DTOs + publisher pattern):
  `nx-gs-adapter-api/src/main/java/app/l2nx/gs/adapter/api/kafka/events/premium/`
- Technical design: see the Technical design section below — wire layout,
  binding registration, and the reference snapshot-builder walkthrough

---

## Technical design

### Overview

`events.serveronline` is the second event family on the per-family Kafka rail
introduced by `events.premiumpurchase`. It carries periodic snapshots of game-server
population broken down by activity bucket. Wire shape: a single concrete DTO
`ServerOnlineSnapshotEvent` with a UUIDv7 `eventId` and a
`Map<ServerOnlineBucket, Long>` of bucket → count entries (the closed enum
`ServerOnlineBucket`, `UPPER_SNAKE_CASE` constant names on the wire): `TOTAL` and
`UNIQUE` are required on every snapshot, `OFFLINE_TRADE`, `FISHING`, `APP` and
`OFFLINE_FISHING` are optional (host SHOULD publish when the concept applies).
Hosts publish via a single
`NxEvents.publishServerOnlineSnapshot(ServerOnlineSnapshotEvent)` SPI method.
Adapter-core registers the binding in `EventTypeRegistry`; the existing
`EventsPublisher` / `EventEnvelope` machinery handles fanout, headers, and
disabled-family short-circuiting unchanged. the host extends its existing
events module (the module that already owns `events.premiumpurchase` wiring)
to schedule a 30-second snapshot tick (fixed delay) on
its scheduler, walking the online players, computing
the buckets, and publishing.

### Structure

- `nx-gs-adapter-api/src/main/java/app/l2nx/gs/adapter/api/kafka/events/serveronline/`
  - `ServerOnlineSnapshotEvent.java` — abstract family base (empty body, type-bound)
  - `ServerOnlineSnapshotEvent.java` — Phase-1 concrete DTO + Builder
  - `ServerOnlineBucket.java` — the closed bucket enum
- `nx-gs-adapter-api/src/main/java/app/l2nx/gs/adapter/api/spi/capability/`
  - `NxEvents.java` — adds `publishServerOnlineSnapshot(ServerOnlineSnapshotEvent)`
  - `NoOpEvents.java` — adds the no-op variant
- `nx-gs-adapter-core/src/main/java/app/l2nx/gs/adapter/core/events/`
  - `EventTypeRegistry.java` — adds `online` family + `ServerOnlineSnapshotEvent`
    binding
  - `NxEventsImpl.java` — adds `publishServerOnlineSnapshot` dispatch
- Host integration, events package
  - events module — extended to also schedule the online snapshot
    tick alongside its existing premium wiring
  - `OnlineSnapshotBuilder` — pure player-iteration → bucket-counts +
    publish facade (lives next to `PremiumPublisher`)

### Key components

- **ServerOnlineSnapshotEvent** (implements R1) — abstract base for the `online` family,
  empty body. Mirrors `PremiumPurchaseEvent` exactly so future subtypes (e.g.
  `OnlineLoginEvent`, `OnlineLogoutEvent`) plug into the same
  `publishServerOnlineSnapshot(ServerOnlineSnapshotEvent)` entry-point and dispatch via `Nx-Message-Type`.

- **ServerOnlineSnapshotEvent** (implements R2) — concrete Phase-1 subtype.
  Two fields: `UUID eventId` (UUIDv7), `Map<ServerOnlineBucket, Long> buckets`. Map is
  defensively copied on construction and exposed via an unmodifiable view;
  null normalizes to `Collections.emptyMap()`. Has a hand-written `Builder`
  with `toBuilder()`. Java-8 source (no records).

- **ServerOnlineBucket** (implements R3) — closed enum split
  into required (`TOTAL`, `UNIQUE`) and optional (`OFFLINE_TRADE`, `FISHING`, `APP`,
  `OFFLINE_FISHING`). Each constant carries a Javadoc paragraph clarifying the reference
  definition; other forks may reuse the constant with their own
  bucket-builder logic so long as the operator-facing meaning is consistent
  (e.g. `UNIQUE` is "distinct active humans" — the identity tuple is
  host-defined).

- **NxEvents.publishServerOnlineSnapshot / NxEventsImpl.publishServerOnlineSnapshot** (implements R4, R6) —
  symmetric to `publishPremiumPurchase`. Null event → WARN + drop; missing registry
  binding → WARN + drop; family disabled → DEBUG + drop; otherwise enqueue
  on `EventsPublisher` via the existing `EventEnvelope` path. Drop policy on
  queue overflow defaults to `newest` (drop incoming; queue order preserved);
  `oldest` (evict head) remains opt-in but over-counts `dropped-total` under
  multi-producer contention.

- **`NxEvents` façade reconnect stability** — the façade returned by
  `ConnectContext.events()` survives reconnect cycles. An internal
  `AtomicReference` inside the façade is swapped to the live publisher on
  every reconnect, so `OnlineSnapshotBuilder` (and any other module-scoped
  caller) can cache the handle once at `onConnect` and never re-acquire.

- **EventTypeRegistry online binding** (implements R5) — `("serveronline",
"ServerOnlineSnapshotEvent", evt -> null)`. Null partition-key is intentional —
  consumer groups by `Nx-Server-Id` header (always present post-handshake)
  and orders within-server by the UUIDv7 timestamp.

- **Host events module** (implements R7) — pre-existing Tier-1 module
  extended with one `ScheduledFuture<?>` slot for the online tick.
  `onConnect` calls both `PremiumPublisher.bind(...)` and
  `OnlineSnapshotBuilder.bind(...)`; `start()` schedules
  `OnlineSnapshotBuilder::tick` via
  the host scheduler's fixed-delay scheduling (30 s — fixed-delay so a
  stalled tick never triggers a catch-up burst); `stop()` cancels;
  `onDisconnect()` unbinds both publishers.

- **OnlineSnapshotBuilder** — public static facade in the same
  `l2nx.events` package as `PremiumPublisher`. Holds the current
  `NxEvents` handle (volatile). `tick()` iterates the player set on
  whatever thread the host scheduler provides, computes the
  counts in a single pass, builds the event, and publishes.
  Wraps the whole tick in `try { ... } catch (Throwable t) { log.debug }`
  for game-loop-safety symmetry with `PremiumPublisher`.

### Data flows

Snapshot tick (host → platform):

```
host scheduler (fixed delay)
  → OnlineSnapshotBuilder.tick()
    → online-player iteration (single pass)
      → counters: total++ always
                  offlineTrade++ if player is in offline mode
                  uniqueSet.add(hwid + "|" + ip) if !offline && !fake
                                                && hwid != "N/A"
                                                && ip ∉ {"N/A","Disconnected"}
                  fishing++ if player is fishing
      → unique = uniqueSet.size()
    → ServerOnlineSnapshotEvent (eventId = UUIDv7.generate(), buckets = Map.of(...))
    → NxEvents.publishServerOnlineSnapshot(event)
      → NxEventsImpl: registry.lookup → publisher.isFamilyEnabled →
        publisher.enqueue(EventEnvelope)
        → EventsPublisher daemon: KafkaProducer.send(record + Nx-Message-Type
          header)
```

Disabled family (no `online` topic in `MessagingTopics.events`):

```
NxEventsImpl.publishServerOnlineSnapshot → registry.lookup → publisher.isFamilyEnabled = false
  → log.debug("events.serveronline disabled — no topic configured; skipping publish")
  → return (no enqueue, no queue-depth growth, no dropped-total increment)
```

### Decisions

- **Snapshot, not deltas, for Phase 1.** Snapshot-cadence is naturally
  appropriate for population stats; per-fact login/logout deltas would
  require platform-side state reconstruction and add traffic proportional
  to login churn. Delta-based per-character events are deferred.

- **Host-pushed (no SPI on the host side).** Symmetric with `events.premiumpurchase`.
  Adapter has no `OnlineStatsProvider` SPI and no scheduled engine; the host
  owns the cadence and the bucket-builder. Trade-off: cadence is per-host
  rather than centrally tunable from the platform — acceptable since hosts
  already own cadence in many other places (e.g. existing `OnlinePlayers`
  announce schedule).

- **Map keyed by a closed enum (vs. typed bucket fields, vs. open string keys).** A new bucket is one constant in
  `ServerOnlineBucket` (an api release, then the platform enum and CHECK, then the host), not a new DTO field. Open
  string keys were dropped: they let each host invent spellings (`app_fishing`) that the platform could not aggregate
  or validate.

- **Partition-key = null (vs. server-id-as-key).** Snapshots are infrequent;
  the cost of round-robin partitioning (no within-server ordering at the
  partition level) is offset by the UUIDv7 `eventId` carrying the
  occurrence timestamp. The consumer groups by `Nx-Server-Id` (already a
  static header) and orders by `eventId`. Plumbing server-id into the
  binding's `partitionKeyExtractor` would require expanding the binding
  shape — overkill for one family.

### Extension points

- **Add a new bucket.** Append a constant to `ServerOnlineBucket` (api release), then the platform enum and CHECK,
  then the host's bucket-builder. A per-action offline bucket is `OFFLINE_<ACTION>`, named like its `ActivityType`
  value.

- **Add a new concrete subtype to `events.serveronline`.** Define a new class
  extending `ServerOnlineSnapshotEvent`, append a binding entry to `EventTypeRegistry`
  (same family `"serveronline"`, distinct message-type string). The platform
  consumer must learn to dispatch the new `Nx-Message-Type`.

- **Add a new event family.** Define `<family>Event` abstract base under
  `app.l2nx.gs.adapter.api.kafka.events.<family>`, ship one or more concrete
  subtypes, add a `publishX(<family>Event)` method on `NxEvents`, append
  bindings to `EventTypeRegistry`. Platform must populate
  `MessagingTopics.events.<family>` with a topic name. Mirrors what this
  feature does for `online`.
