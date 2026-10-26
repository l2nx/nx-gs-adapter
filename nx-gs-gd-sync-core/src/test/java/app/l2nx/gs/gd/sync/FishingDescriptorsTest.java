package app.l2nx.gs.gd.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.item.ItemGrade;
import app.l2nx.gs.adapter.api.kafka.ops.model.EntityStats;
import app.l2nx.gs.adapter.api.kafka.sync.gd.GameDataSyncEvent;
import app.l2nx.gs.adapter.api.kafka.sync.gd.fishing.FishingBait;
import app.l2nx.gs.adapter.api.kafka.sync.gd.fishing.FishingRod;
import app.l2nx.gs.adapter.api.kafka.sync.gd.fishing.FishingShot;
import app.l2nx.gs.adapter.api.rest.SyncTopics;
import app.l2nx.gs.adapter.api.spi.ConnectContext;
import app.l2nx.gs.gd.sync.provider.TestFishingBaitProvider;
import app.l2nx.gs.gd.sync.provider.TestFishingRodProvider;
import app.l2nx.gs.gd.sync.provider.TestFishingShotProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FishingDescriptorsTest {

    private static final String TOPIC = "kbt.gd.sync";

    private List<GameDataSyncEvent<?>> recorded;
    private GameDataSyncModule module;

    @BeforeEach
    void setUp() {
        recorded = new ArrayList<GameDataSyncEvent<?>>();
        GameDataSender sender = new GameDataSender() {
            @Override
            public void send(ProducerRecord<byte[], Object> record, Callback callback) {
                recorded.add((GameDataSyncEvent<?>) record.value());
                callback.onCompletion(null, null);
            }
        };
        module = new GameDataSyncModule(GameDataSyncModule.defaultDescriptors(), sender);
    }

    @AfterEach
    void tearDown() {
        TestFishingBaitProvider.snapshot = Collections.emptyList();
        TestFishingRodProvider.snapshot = Collections.emptyList();
        TestFishingShotProvider.snapshot = Collections.emptyList();
    }

    private void connectAndSnapshot() {
        Map<String, String> gd = new HashMap<String, String>();
        gd.put("fishingbait", TOPIC);
        gd.put("fishingrod", TOPIC);
        gd.put("fishingshot", TOPIC);
        ConnectContext ctx = ConnectContext.builder()
                .tenantId(UUID.randomUUID())
                .tenantSlug("kbt")
                .serverId(UUID.randomUUID())
                .serverSlug("kbt-x1")
                .serverName("x1")
                .adapterVersion("test")
                .syncTopics(SyncTopics.builder().gd(gd).build())
                .build();
        module.onConnect(ctx);
        module.start();
    }

    private List<GameDataSyncEvent<?>> eventsOf(String entityName) {
        List<GameDataSyncEvent<?>> out = new ArrayList<GameDataSyncEvent<?>>();
        for (GameDataSyncEvent<?> e : recorded) {
            if (entityName.equals(e.getEntityName())) {
                out.add(e);
            }
        }
        return out;
    }

    private static void assertKeyedBurst(List<GameDataSyncEvent<?>> events, long expectedPk, Object expectedPayload) {
        assertEquals(2, events.size(), "1 UPSERT + 1 SNAPSHOT_COMPLETE");
        assertEquals(GameDataSnapshotPublisher.OP_UPSERT, events.get(0).getOp());
        assertEquals(Long.valueOf(expectedPk), events.get(0).getPk());
        assertEquals(expectedPayload, events.get(0).getPayload());
        assertEquals(
                GameDataSnapshotPublisher.OP_SNAPSHOT_COMPLETE, events.get(1).getOp());
        assertEquals(Integer.valueOf(1), events.get(1).getCount());
    }

    @Nested
    class Resolve {

        @Test
        void onConnect_shouldIncludeFishingEntities_whenProvidersRegistered() {
            connectAndSnapshot();

            List<String> names = new ArrayList<String>();
            for (EntityStats s : module.currentStatus().getStats().getEntities().orElseGet(Collections::emptyList)) {
                names.add(s.getName());
            }
            assertTrue(names.containsAll(Arrays.asList("fishingbait", "fishingrod", "fishingshot")), names.toString());
        }
    }

    @Nested
    class PublishSnapshot {

        @Test
        void start_shouldKeyBaitByItemTemplateId() {
            FishingBait bait =
                    FishingBait.builder().itemTemplateId(7807).chancePercent(40).build();
            TestFishingBaitProvider.snapshot = Collections.singletonList(bait);

            connectAndSnapshot();

            assertKeyedBurst(eventsOf("fishingbait"), 7807L, bait);
        }

        @Test
        void start_shouldKeyRodByItemTemplateId() {
            FishingRod rod = FishingRod.builder()
                    .itemTemplateId(6529)
                    .fishingTimeMs(120_000L)
                    .shotGrade(ItemGrade.S)
                    .build();
            TestFishingRodProvider.snapshot = Collections.singletonList(rod);

            connectAndSnapshot();

            assertKeyedBurst(eventsOf("fishingrod"), 6529L, rod);
        }

        @Test
        void start_shouldKeyShotByItemTemplateId() {
            FishingShot shot = FishingShot.builder()
                    .itemTemplateId(6540)
                    .grade(ItemGrade.S)
                    .build();
            TestFishingShotProvider.snapshot = Collections.singletonList(shot);

            connectAndSnapshot();

            assertKeyedBurst(eventsOf("fishingshot"), 6540L, shot);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"fishingbait", "fishingrod", "fishingshot"})
        void start_shouldPublishEmptyMarker_whenServerAcceptsNoGearOfTheKind(String entityName) {
            connectAndSnapshot();

            List<GameDataSyncEvent<?>> actual = eventsOf(entityName);
            assertEquals(1, actual.size(), actual.toString());
            assertEquals(
                    GameDataSnapshotPublisher.OP_SNAPSHOT_COMPLETE,
                    actual.get(0).getOp());
            assertEquals(Integer.valueOf(0), actual.get(0).getCount());
        }
    }
}
