package app.l2nx.gs.adapter.core.events;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.NxHeaders;
import app.l2nx.gs.adapter.api.kafka.events.character.AppSessionsSnapshotEvent;
import app.l2nx.gs.commons.UUIDv7;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EventsBootstrapTest {

    private static final UUID SERVER_ID = UUID.fromString("0192f000-0000-7000-8000-00000000000a");
    private static final UUID NEXT_SERVER_ID = UUID.fromString("0192f000-0000-7000-8000-00000000000b");

    private EventsPublisher publisher;

    @AfterEach
    void tearDown() {
        if (publisher != null) {
            publisher.stop();
        }
    }

    @Test
    void start_shouldKeyTheSnapshotByTheServerId() throws InterruptedException {
        AtomicReference<ProducerRecord<byte[], Object>> sent = new AtomicReference<ProducerRecord<byte[], Object>>();
        CountDownLatch latch = new CountDownLatch(1);

        EventsBootstrap.Started started = EventsBootstrap.start(
                SERVER_ID, topics(), recordingSender(sent, latch), () -> {}, EventsConfig.defaults());
        publisher = started.publisher();
        started.events().publish(snapshot());

        assertTrue(latch.await(2, TimeUnit.SECONDS), "snapshot did not reach sender");
        assertArrayEquals(NxHeaders.encodeUuid(SERVER_ID), sent.get().key());
    }

    @Test
    void swap_shouldKeyTheSnapshotByTheNewServerId() throws InterruptedException {
        EventsBootstrap.Started started =
                EventsBootstrap.start(SERVER_ID, topics(), (record, callback) -> {}, () -> {}, EventsConfig.defaults());
        started.publisher().stop();
        AtomicReference<ProducerRecord<byte[], Object>> sent = new AtomicReference<ProducerRecord<byte[], Object>>();
        CountDownLatch latch = new CountDownLatch(1);

        publisher = EventsBootstrap.swap(
                started.events(),
                NEXT_SERVER_ID,
                topics(),
                recordingSender(sent, latch),
                () -> {},
                EventsConfig.defaults());
        started.events().publish(snapshot());

        assertTrue(latch.await(2, TimeUnit.SECONDS), "snapshot did not reach sender");
        assertArrayEquals(NxHeaders.encodeUuid(NEXT_SERVER_ID), sent.get().key());
    }

    private static Map<String, String> topics() {
        return Collections.singletonMap("character", "acme.gs.events.character");
    }

    private static EventsPublisher.Sender recordingSender(
            AtomicReference<ProducerRecord<byte[], Object>> sent, CountDownLatch latch) {
        return (record, callback) -> {
            sent.set(record);
            callback.onCompletion(null, null);
            latch.countDown();
        };
    }

    private static AppSessionsSnapshotEvent snapshot() {
        return AppSessionsSnapshotEvent.builder()
                .eventId(UUIDv7.generate())
                .takenAt(Instant.parse("2026-10-26T10:00:00Z"))
                .build();
    }
}
