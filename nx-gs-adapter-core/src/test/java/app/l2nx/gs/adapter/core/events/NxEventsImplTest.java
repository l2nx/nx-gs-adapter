package app.l2nx.gs.adapter.core.events;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.events.character.CharacterPresenceEvent;
import app.l2nx.gs.adapter.api.kafka.events.character.model.SessionSource;
import app.l2nx.gs.adapter.api.kafka.events.premiumpurchase.PremiumPurchaseEvent;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.PrivateStorePurchaseEvent;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.PrivateStoreStateEvent;
import app.l2nx.gs.adapter.api.kafka.events.privatestore.model.PrivateStoreSide;
import app.l2nx.gs.adapter.api.kafka.events.raid.kill.RaidKillEvent;
import app.l2nx.gs.adapter.api.kafka.events.server.online.ServerOnlineSnapshotEvent;
import app.l2nx.gs.adapter.api.kafka.events.server.online.model.ServerOnlineBucket;
import app.l2nx.gs.commons.UUIDv7;
import app.l2nx.gs.commons.bytes.LongBytes;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NxEventsImplTest {

    private static Map<ServerOnlineBucket, Long> buckets(long total) {
        Map<ServerOnlineBucket, Long> buckets = new EnumMap<ServerOnlineBucket, Long>(ServerOnlineBucket.class);
        buckets.put(ServerOnlineBucket.TOTAL, total);
        buckets.put(ServerOnlineBucket.UNIQUE, total);
        return buckets;
    }

    private EventsPublisher publisher;

    @AfterEach
    void tearDown() {
        if (publisher != null) {
            publisher.stop();
        }
    }

    @Test
    void publish_shouldEnqueuePremiumPurchaseIntoPublisher() throws InterruptedException {
        ConcurrentLinkedQueue<Object> sent = new ConcurrentLinkedQueue<Object>();
        CountDownLatch latch = new CountDownLatch(1);
        EventsPublisher.Sender sender = (record, callback) -> {
            sent.add(record.value());
            callback.onCompletion(null, null);
            latch.countDown();
        };
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.singletonMap("premiumpurchase", "acme.gs.events.premiumpurchase"),
                sender,
                cfg(50, 500L),
                registry);
        publisher.start();

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        PremiumPurchaseEvent event = PremiumPurchaseEvent.builder()
                .eventId(UUIDv7.generate())
                .characterId(42L)
                .build();

        events.publish(event);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "publish(PremiumPurchaseEvent) did not reach sender");
        assertEquals(1, sent.size());
        assertEquals(event, sent.peek());
    }

    @Test
    void publish_shouldSendPresenceWithPlaceholderIdentifiersAsNull() throws InterruptedException {
        ConcurrentLinkedQueue<Object> sent = new ConcurrentLinkedQueue<Object>();
        CountDownLatch latch = new CountDownLatch(1);
        EventsPublisher.Sender sender = (record, callback) -> {
            sent.add(record.value());
            callback.onCompletion(null, null);
            latch.countDown();
        };
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.singletonMap("character", "acme.gs.events.character"), sender, cfg(50, 500L), registry);
        publisher.start();

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        CharacterPresenceEvent event = CharacterPresenceEvent.builder()
                .eventId(UUIDv7.generate())
                .charId(42L)
                .online(true)
                .source(SessionSource.CLIENT)
                .ip("N/A")
                .hwid("0")
                .build();

        events.publish(event);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "publish(CharacterPresenceEvent) did not reach sender");
        assertEquals(event.toBuilder().ip(null).hwid(null).build(), sent.peek());
    }

    @Test
    void publish_shouldNoOp_forNullEvent() {
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.singletonMap("premiumpurchase", "acme.gs.events.premiumpurchase"),
                (r, c) -> {},
                cfg(5, 0L),
                registry);

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        events.publish(null);

        assertEquals(0, publisher.queueDepth());
        assertEquals(0L, publisher.droppedTotal());
    }

    @Test
    void publish_shouldShortCircuitPremiumPurchase_whenFamilyTopicMissing() {
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(Collections.emptyMap(), (r, c) -> {}, cfg(5, 0L), registry);

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        events.publish(PremiumPurchaseEvent.builder()
                .eventId(UUIDv7.generate())
                .characterId(42L)
                .build());

        assertEquals(0, publisher.queueDepth(), "disabled family must not enqueue an envelope");
        assertEquals(0L, publisher.droppedTotal(), "disabled family must not count toward dropped-total");
    }

    @Test
    void publish_shouldEnqueueServerOnlineSnapshotIntoPublisher() throws InterruptedException {
        ConcurrentLinkedQueue<Object> sentValues = new ConcurrentLinkedQueue<Object>();
        // ConcurrentLinkedQueue rejects nulls, so null partition-key is recorded via a flag
        AtomicBoolean partitionKeyWasNull = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        EventsPublisher.Sender sender = (record, callback) -> {
            sentValues.add(record.value());
            partitionKeyWasNull.set(record.key() == null);
            callback.onCompletion(null, null);
            latch.countDown();
        };
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.singletonMap("serveronline", "acme.gs.events.serveronline"),
                sender,
                cfg(50, 500L),
                registry);
        publisher.start();

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        ServerOnlineSnapshotEvent event = ServerOnlineSnapshotEvent.builder()
                .eventId(UUIDv7.generate())
                .buckets(buckets(1808L))
                .build();

        events.publish(event);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "publish(ServerOnlineSnapshotEvent) did not reach sender");
        assertEquals(1, sentValues.size());
        assertEquals(event, sentValues.peek());
        assertTrue(partitionKeyWasNull.get(), "server-online snapshot partition key must be null (round-robin)");
    }

    @Test
    void publish_shouldNoOpServerOnlineSnapshot_forNullEvent() {
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.singletonMap("serveronline", "acme.gs.events.serveronline"),
                (r, c) -> {},
                cfg(5, 0L),
                registry);

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        events.publish(null);

        assertEquals(0, publisher.queueDepth());
        assertEquals(0L, publisher.droppedTotal());
    }

    @Test
    void publish_shouldShortCircuitServerOnlineSnapshot_whenFamilyTopicMissing() {
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(Collections.emptyMap(), (r, c) -> {}, cfg(5, 0L), registry);

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        events.publish(ServerOnlineSnapshotEvent.builder()
                .eventId(UUIDv7.generate())
                .buckets(buckets(1L))
                .build());

        assertEquals(0, publisher.queueDepth(), "disabled family must not enqueue an envelope");
        assertEquals(0L, publisher.droppedTotal(), "disabled family must not count toward dropped-total");
    }

    @Test
    void publish_shouldEnqueuePrivateStorePurchaseRoundRobin() throws InterruptedException {
        ConcurrentLinkedQueue<Object> sentValues = new ConcurrentLinkedQueue<Object>();
        AtomicBoolean partitionKeyWasNull = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        EventsPublisher.Sender sender = (record, callback) -> {
            sentValues.add(record.value());
            partitionKeyWasNull.set(record.key() == null);
            callback.onCompletion(null, null);
            latch.countDown();
        };
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.singletonMap("privatestore", "acme.gs.events.privatestore"),
                sender,
                cfg(50, 500L),
                registry);
        publisher.start();

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        PrivateStorePurchaseEvent event = PrivateStorePurchaseEvent.builder()
                .eventId(UUIDv7.generate())
                .storeType(PrivateStoreSide.ASK)
                .sellerId(1L)
                .buyerId(2L)
                .build();

        events.publish(event);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "publish(PrivateStorePurchaseEvent) did not reach sender");
        assertEquals(1, sentValues.size());
        assertEquals(event, sentValues.peek());
        assertTrue(partitionKeyWasNull.get(), "private-store purchase partition key must be null (round-robin)");
    }

    @Test
    void publish_shouldEnqueuePrivateStoreStatePartitionedByCharId() throws InterruptedException {
        ConcurrentLinkedQueue<Object> sentValues = new ConcurrentLinkedQueue<Object>();
        ConcurrentLinkedQueue<byte[]> sentKeys = new ConcurrentLinkedQueue<byte[]>();
        CountDownLatch latch = new CountDownLatch(1);
        EventsPublisher.Sender sender = (record, callback) -> {
            sentValues.add(record.value());
            sentKeys.add(record.key());
            callback.onCompletion(null, null);
            latch.countDown();
        };
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.singletonMap("privatestore", "acme.gs.events.privatestore"),
                sender,
                cfg(50, 500L),
                registry);
        publisher.start();

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        PrivateStoreStateEvent event = PrivateStoreStateEvent.builder()
                .eventId(UUIDv7.generate())
                .charId(0xCAFEBABEL)
                .build();

        events.publish(event);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "publish(PrivateStoreStateEvent) did not reach sender");
        assertEquals(1, sentValues.size());
        assertEquals(event, sentValues.peek());
        assertArrayEquals(
                LongBytes.bigEndian(0xCAFEBABEL),
                sentKeys.peek(),
                "state event must be keyed by charId as 8 big-endian bytes");
    }

    @Test
    void publish_shouldNoOpPrivateStorePurchase_forNullEvent() {
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.singletonMap("privatestore", "acme.gs.events.privatestore"),
                (r, c) -> {},
                cfg(5, 0L),
                registry);

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        events.publish(null);

        assertEquals(0, publisher.queueDepth());
        assertEquals(0L, publisher.droppedTotal());
    }

    @Test
    void publish_shouldNoOpPrivateStoreState_forNullEvent() {
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.singletonMap("privatestore", "acme.gs.events.privatestore"),
                (r, c) -> {},
                cfg(5, 0L),
                registry);

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        events.publish(null);

        assertEquals(0, publisher.queueDepth());
        assertEquals(0L, publisher.droppedTotal());
    }

    @Test
    void publish_shouldShortCircuitPrivateStorePurchase_whenFamilyTopicMissing() {
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(Collections.emptyMap(), (r, c) -> {}, cfg(5, 0L), registry);

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        events.publish(PrivateStorePurchaseEvent.builder()
                .eventId(UUIDv7.generate())
                .storeType(PrivateStoreSide.ASK)
                .sellerId(1L)
                .buyerId(2L)
                .build());

        assertEquals(0, publisher.queueDepth(), "disabled family must not enqueue an envelope");
        assertEquals(0L, publisher.droppedTotal(), "disabled family must not count toward dropped-total");
    }

    @Test
    void publish_shouldEnqueueRaidKillPartitionedByBossNpcId() throws InterruptedException {
        ConcurrentLinkedQueue<Object> sentValues = new ConcurrentLinkedQueue<Object>();
        ConcurrentLinkedQueue<byte[]> sentKeys = new ConcurrentLinkedQueue<byte[]>();
        CountDownLatch latch = new CountDownLatch(1);
        EventsPublisher.Sender sender = (record, callback) -> {
            sentValues.add(record.value());
            sentKeys.add(record.key());
            callback.onCompletion(null, null);
            latch.countDown();
        };
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.singletonMap("raid", "acme.gs.events.raid"), sender, cfg(50, 500L), registry);
        publisher.start();

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        RaidKillEvent event = RaidKillEvent.builder()
                .eventId(UUIDv7.generate())
                .bossNpcId(29028)
                .build();

        events.publish(event);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "publish(RaidKillEvent) did not reach sender");
        assertEquals(1, sentValues.size());
        assertEquals(event, sentValues.peek());
        assertArrayEquals(
                LongBytes.bigEndian(29028L),
                sentKeys.peek(),
                "raid-kill event must be keyed by bossNpcId as 8 big-endian bytes");
    }

    @Test
    void publish_shouldNoOpRaidKill_forNullEvent() {
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.singletonMap("raid", "acme.gs.events.raid"), (r, c) -> {}, cfg(5, 0L), registry);

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        events.publish(null);

        assertEquals(0, publisher.queueDepth());
        assertEquals(0L, publisher.droppedTotal());
    }

    @Test
    void publish_shouldShortCircuitRaidKill_whenFamilyTopicMissing() {
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(Collections.emptyMap(), (r, c) -> {}, cfg(5, 0L), registry);

        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        events.publish(RaidKillEvent.builder()
                .eventId(UUIDv7.generate())
                .bossNpcId(29028)
                .build());

        assertEquals(0, publisher.queueDepth(), "disabled family must not enqueue an envelope");
        assertEquals(0L, publisher.droppedTotal(), "disabled family must not count toward dropped-total");
    }

    @Test
    void swap_shouldRetargetFacade_atNewPublisher() throws InterruptedException {
        ConcurrentLinkedQueue<Object> captured1 = new ConcurrentLinkedQueue<Object>();
        CountDownLatch latch1 = new CountDownLatch(1);
        EventsPublisher.Sender sender1 = (record, callback) -> {
            captured1.add(record.value());
            callback.onCompletion(null, null);
            latch1.countDown();
        };
        EventTypeRegistry registry1 = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.singletonMap("premiumpurchase", "topic-a"), sender1, cfg(50, 200L), registry1);
        publisher.start();
        NxEventsImpl events = new NxEventsImpl(publisher, registry1);

        events.publish(PremiumPurchaseEvent.builder()
                .eventId(UUIDv7.generate())
                .characterId(1L)
                .build());
        assertTrue(latch1.await(2, TimeUnit.SECONDS));
        assertEquals(1, captured1.size());

        publisher.stop();
        ConcurrentLinkedQueue<Object> captured2 = new ConcurrentLinkedQueue<Object>();
        CountDownLatch latch2 = new CountDownLatch(1);
        EventsPublisher.Sender sender2 = (record, callback) -> {
            captured2.add(record.value());
            callback.onCompletion(null, null);
            latch2.countDown();
        };
        EventTypeRegistry registry2 = new EventTypeRegistry();
        EventsPublisher next = new EventsPublisher(
                Collections.singletonMap("premiumpurchase", "topic-b"), sender2, cfg(50, 200L), registry2);
        next.start();
        events.swap(next, registry2);
        publisher = next;

        events.publish(PremiumPurchaseEvent.builder()
                .eventId(UUIDv7.generate())
                .characterId(2L)
                .build());

        assertTrue(latch2.await(2, TimeUnit.SECONDS), "swapped publisher did not receive event");
        assertEquals(1, captured2.size(), "swapped event must hit the new publisher only");
        assertEquals(1, captured1.size(), "old publisher must not see new event");
    }

    @Test
    void publishWithCallback_shouldCompleteWithNullFailureOnceKafkaAcknowledged() throws InterruptedException {
        EventsPublisher.Sender sender = (record, callback) -> callback.onCompletion(null, null);
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.singletonMap("premiumpurchase", "acme.gs.events.premiumpurchase"),
                sender,
                cfg(50, 500L),
                registry);
        publisher.start();
        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<Exception>(new Exception("not called"));

        events.publish(purchase(), f -> {
            failure.set(f);
            done.countDown();
        });

        assertTrue(done.await(2, TimeUnit.SECONDS), "callback not invoked");
        assertNull(failure.get());
    }

    @Test
    void publishWithCallback_shouldReportSendFailure() throws InterruptedException {
        RuntimeException boom = new RuntimeException("broker down");
        EventsPublisher.Sender sender = (record, callback) -> callback.onCompletion(null, boom);
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.singletonMap("premiumpurchase", "acme.gs.events.premiumpurchase"),
                sender,
                cfg(50, 500L),
                registry);
        publisher.start();
        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<Exception>();

        events.publish(purchase(), f -> {
            failure.set(f);
            done.countDown();
        });

        assertTrue(done.await(2, TimeUnit.SECONDS), "callback not invoked");
        assertNotNull(failure.get());
        assertFalse(failure.get() instanceof UndeliverableEventException, "a broker error may pass on a later send");
    }

    @Test
    void publishWithCallback_shouldFailWhenFamilyHasNoTopic() {
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.<String, String>emptyMap(), (record, callback) -> {}, cfg(50, 500L), registry);
        publisher.start();
        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        AtomicReference<Exception> failure = new AtomicReference<Exception>();

        events.publish(purchase(), failure::set);

        assertInstanceOf(UndeliverableEventException.class, failure.get());
    }

    @Test
    void publishWithCallback_shouldFailWhenEventHasNoBinding() {
        EventTypeRegistry registry = new EventTypeRegistry();
        publisher = new EventsPublisher(
                Collections.<String, String>emptyMap(), (record, callback) -> {}, cfg(50, 500L), registry);
        publisher.start();
        NxEventsImpl events = new NxEventsImpl(publisher, registry);
        AtomicReference<Exception> failure = new AtomicReference<Exception>();

        events.publish("not an event", failure::set);

        assertInstanceOf(UndeliverableEventException.class, failure.get());
    }

    @Test
    void publishWithCallback_shouldFailBeforePublisherWired() {
        NxEventsImpl events = new NxEventsImpl(null, null);
        AtomicReference<Exception> failure = new AtomicReference<Exception>();

        events.publish(purchase(), failure::set);

        assertNotNull(failure.get());
        assertFalse(failure.get() instanceof UndeliverableEventException, "a publisher may still be wired");
    }

    private static PremiumPurchaseEvent purchase() {
        return PremiumPurchaseEvent.builder()
                .eventId(UUIDv7.generate())
                .characterId(1L)
                .build();
    }

    private static EventsConfig cfg(int capacity, long shutdownDrainMs) {
        return new EventsConfig(capacity, EventsPublisher.DropPolicy.NEWEST, shutdownDrainMs);
    }
}
