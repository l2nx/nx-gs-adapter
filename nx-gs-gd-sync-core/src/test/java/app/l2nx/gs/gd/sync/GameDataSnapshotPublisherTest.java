package app.l2nx.gs.gd.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.NxHeaders;
import app.l2nx.gs.adapter.api.kafka.sync.gd.GameDataSyncEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GameDataSnapshotPublisherTest {

    private static final String ENTITY = "itemtemplate";
    private static final String TOPIC = "kbt.gd.sync.itemtemplate";
    private static final long GRACE_MS = 1000L;
    private static final ToLongFunction<Long> PK_OF = value -> value;

    private final List<GameDataSyncEvent<?>> sent = new ArrayList<GameDataSyncEvent<?>>();
    private final List<ProducerRecord<byte[], Object>> records = new ArrayList<ProducerRecord<byte[], Object>>();
    private final AtomicLong now = new AtomicLong(0L);
    private GameDataSnapshotPublisher publisher;

    @BeforeEach
    void setUp() {
        GameDataSender sender = new GameDataSender() {
            @Override
            public void send(ProducerRecord<byte[], Object> record, Callback callback) {
                records.add(record);
                sent.add((GameDataSyncEvent<?>) record.value());
                callback.onCompletion(null, null);
            }
        };
        publisher = new GameDataSnapshotPublisher(sender, GRACE_MS, now::get);
    }

    private GameDataSnapshotPublisher.Result publishNull() {
        return publisher.publishSnapshot(ENTITY, null, PK_OF, UUID.randomUUID(), TOPIC);
    }

    private GameDataSnapshotPublisher.Result publishItems(List<Long> items) {
        return publisher.publishSnapshot(ENTITY, items, PK_OF, UUID.randomUUID(), TOPIC);
    }

    private static Stream<List<Long>> nullSnapshotTimes() {
        return Stream.of(
                Collections.singletonList(0L),
                Arrays.asList(0L, 0L),
                Arrays.asList(0L, GRACE_MS - 1L),
                Arrays.asList(0L, GRACE_MS),
                Arrays.asList(0L, GRACE_MS, GRACE_MS + 500L));
    }

    @Nested
    class NullSnapshot {

        // escalation is a log severity change only; the publish contract is the same at every stage
        @ParameterizedTest(name = "nullsAtMs={0}")
        @MethodSource("app.l2nx.gs.gd.sync.GameDataSnapshotPublisherTest#nullSnapshotTimes")
        void publishSnapshot_shouldReturnNullAndSendNothing_whenItemsNull(List<Long> nullsAtMs) {
            for (long atMs : nullsAtMs) {
                now.set(atMs);
                assertNull(publishNull());
            }

            assertTrue(sent.isEmpty(), "a null snapshot must not reach the sender — no SNAPSHOT_COMPLETE marker");
        }
    }

    @Nested
    class SuccessfulPublish {

        @Test
        void publishSnapshot_shouldPublishFullBurst_whenItemsPresent() {
            GameDataSnapshotPublisher.Result result = publishItems(Arrays.asList(1L, 2L));

            assertEquals(2, result.count());
            assertTrue(result.complete());
            assertEquals(3, sent.size(), "2 UPSERT + 1 SNAPSHOT_COMPLETE");
            assertEquals(GameDataSnapshotPublisher.OP_UPSERT, sent.get(0).getOp());
            assertEquals(GameDataSnapshotPublisher.OP_UPSERT, sent.get(1).getOp());
            assertEquals(
                    GameDataSnapshotPublisher.OP_SNAPSHOT_COMPLETE, sent.get(2).getOp());
            assertEquals(Integer.valueOf(2), sent.get(2).getCount());
        }

        @Test
        void publishSnapshot_shouldPublishOnlyTheMarker_whenItemsEmpty() {
            GameDataSnapshotPublisher.Result result = publishItems(Collections.<Long>emptyList());

            assertEquals(0, result.count());
            assertTrue(result.complete());
            assertEquals(1, sent.size(), sent.toString());
            assertEquals(
                    GameDataSnapshotPublisher.OP_SNAPSHOT_COMPLETE, sent.get(0).getOp());
            assertEquals(Integer.valueOf(0), sent.get(0).getCount());
        }

        @Test
        void publishSnapshot_shouldCarryNoPrefixedHeader() {
            publishItems(Collections.singletonList(1L));

            assertEquals(2, records.size());
            for (ProducerRecord<byte[], Object> record : records) {
                assertNotNull(record.headers().lastHeader(NxHeaders.MESSAGE_TYPE));
                for (Header header : record.headers()) {
                    assertFalse(
                            header.key().startsWith("Nx-"), "gd-sync record carries prefixed header " + header.key());
                }
            }
        }
    }

    @Nested
    class EscalationWiring {

        private static final String OTHER_ENTITY = "npctemplate";

        @Test
        void trackerFor_shouldReportRepeat_forNullInsideGraceWindow() {
            publishNull();
            now.set(GRACE_MS - 1L);

            publishNull();

            assertEquals(
                    EscalationTracker.Stage.REPEAT, publisher.trackerFor(ENTITY).lastStage());
        }

        @Test
        void trackerFor_shouldKeyPerEntity_soOneEntityEscalatingLeavesAnotherAtFirst() {
            publishNull();
            now.set(GRACE_MS);
            publishNull();
            assertEquals(
                    EscalationTracker.Stage.ESCALATED,
                    publisher.trackerFor(ENTITY).lastStage());

            publisher.publishSnapshot(OTHER_ENTITY, null, PK_OF, UUID.randomUUID(), TOPIC);

            assertEquals(
                    EscalationTracker.Stage.FIRST,
                    publisher.trackerFor(OTHER_ENTITY).lastStage());
            assertEquals(
                    EscalationTracker.Stage.ESCALATED,
                    publisher.trackerFor(ENTITY).lastStage(),
                    "another entity's first null must not disturb an already-escalated entity");
        }

        @Test
        void trackerFor_shouldResetToFirst_afterASuccessfulPublish() {
            publishNull();
            now.set(GRACE_MS);
            publishNull();
            assertEquals(
                    EscalationTracker.Stage.ESCALATED,
                    publisher.trackerFor(ENTITY).lastStage());

            GameDataSnapshotPublisher.Result published = publishItems(Collections.singletonList(1L));
            assertEquals(1, published.count());
            assertTrue(published.complete());
            assertNull(publishNull());

            assertEquals(2, sent.size(), "1 UPSERT + 1 SNAPSHOT_COMPLETE, nothing for the nulls");
            assertEquals(
                    EscalationTracker.Stage.FIRST, publisher.trackerFor(ENTITY).lastStage());
        }
    }
}
