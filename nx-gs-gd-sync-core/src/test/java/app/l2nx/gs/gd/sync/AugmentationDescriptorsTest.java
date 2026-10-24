package app.l2nx.gs.gd.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.ops.model.EntityStats;
import app.l2nx.gs.adapter.api.kafka.sync.gd.GameDataSyncEvent;
import app.l2nx.gs.adapter.api.kafka.sync.gd.augmentation.lifestone.AugmentationOutcome;
import app.l2nx.gs.adapter.api.kafka.sync.gd.augmentation.lifestone.LifeStone;
import app.l2nx.gs.adapter.api.kafka.sync.gd.augmentation.targetgroup.AugmentationTargetGroup;
import app.l2nx.gs.adapter.api.kafka.sync.gd.itemoption.ItemOption;
import app.l2nx.gs.adapter.api.rest.SyncTopics;
import app.l2nx.gs.adapter.api.spi.ConnectContext;
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

class AugmentationDescriptorsTest {

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
        TestItemOptionProvider.snapshot = Collections.emptyList();
        TestLifeStoneProvider.snapshot = Collections.emptyList();
        TestAugmentationTargetGroupProvider.snapshot = Collections.emptyList();
    }

    private void connectAndSnapshot() {
        Map<String, String> gd = new HashMap<String, String>();
        gd.put("itemoption", TOPIC);
        gd.put("lifestone", TOPIC);
        gd.put("augmentationtargetgroup", TOPIC);
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
        void onConnect_shouldIncludeAugmentationEntities_inActiveEntities() {
            connectAndSnapshot();

            List<String> names = new ArrayList<String>();
            for (EntityStats s : module.currentStatus().getStats().getEntities().orElseGet(Collections::emptyList)) {
                names.add(s.getName());
            }
            assertTrue(
                    names.containsAll(Arrays.asList("itemoption", "lifestone", "augmentationtargetgroup")),
                    names.toString());
        }
    }

    @Nested
    class PublishSnapshot {

        @Test
        void start_shouldKeyItemOptionByOptionId() {
            ItemOption option = ItemOption.builder().id(16001).build();
            TestItemOptionProvider.snapshot = Collections.singletonList(option);

            connectAndSnapshot();

            assertKeyedBurst(eventsOf("itemoption"), 16001L, option);
        }

        @Test
        void start_shouldKeyLifeStoneByItemTemplateId() {
            LifeStone stone = LifeStone.builder()
                    .itemTemplateId(8762)
                    .augmentationTargetGroupIds(Collections.singletonList(7))
                    .outcomes(Collections.singletonList(AugmentationOutcome.builder()
                            .augmentationTargetGroupId(7)
                            .slot(1)
                            .itemOptionId(16001)
                            .chancePercent(12.5)
                            .build()))
                    .build();
            TestLifeStoneProvider.snapshot = Collections.singletonList(stone);

            connectAndSnapshot();

            assertKeyedBurst(eventsOf("lifestone"), 8762L, stone);
        }

        @Test
        void start_shouldKeyTargetGroupByGroupId() {
            AugmentationTargetGroup group = AugmentationTargetGroup.builder()
                    .id(7)
                    .itemTemplateIds(Arrays.asList(150, 151))
                    .build();
            TestAugmentationTargetGroupProvider.snapshot = Collections.singletonList(group);

            connectAndSnapshot();

            assertKeyedBurst(eventsOf("augmentationtargetgroup"), 7L, group);
        }
    }
}
