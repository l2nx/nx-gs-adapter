package app.l2nx.gs.gd.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.l2nx.gs.adapter.api.kafka.sync.gd.GameDataSyncEvent;
import app.l2nx.gs.adapter.api.kafka.sync.gd.soulcrystal.SoulCrystal;
import app.l2nx.gs.adapter.api.rest.SyncTopics;
import app.l2nx.gs.adapter.api.spi.ConnectContext;
import app.l2nx.gs.gd.sync.provider.TestSoulCrystalProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SoulCrystalDescriptorTest {

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
        TestSoulCrystalProvider.snapshot = Collections.emptyList();
    }

    private void connectAndSnapshot() {
        SyncTopics topics = SyncTopics.builder()
                .gd(Collections.singletonMap("soulcrystal", TOPIC))
                .build();
        ConnectContext ctx = ConnectContext.builder()
                .tenantId(UUID.randomUUID())
                .tenantSlug("kbt")
                .serverId(UUID.randomUUID())
                .serverSlug("kbt-x1")
                .serverName("x1")
                .adapterVersion("test")
                .syncTopics(topics)
                .build();
        module.onConnect(ctx);
        module.start();
    }

    private List<GameDataSyncEvent<?>> soulCrystalEvents() {
        List<GameDataSyncEvent<?>> out = new ArrayList<GameDataSyncEvent<?>>();
        for (GameDataSyncEvent<?> e : recorded) {
            if ("soulcrystal".equals(e.getEntityName())) {
                out.add(e);
            }
        }
        return out;
    }

    @Nested
    class PublishSnapshot {

        @Test
        void start_shouldPublishRowPerCrystalKeyedByItemTemplateId_thenSnapshotComplete() {
            SoulCrystal stage1 = SoulCrystal.builder()
                    .itemTemplateId(4629)
                    .level(1)
                    .nextItemTemplateId(4630)
                    .build();
            SoulCrystal top = SoulCrystal.builder().itemTemplateId(5577).build();
            TestSoulCrystalProvider.snapshot = Arrays.asList(stage1, top);

            connectAndSnapshot();

            List<GameDataSyncEvent<?>> events = soulCrystalEvents();
            assertEquals(3, events.size(), "2 UPSERT + 1 SNAPSHOT_COMPLETE");
            assertEquals(GameDataSnapshotPublisher.OP_UPSERT, events.get(0).getOp());
            assertEquals(Long.valueOf(4629L), events.get(0).getPk());
            assertEquals(stage1, events.get(0).getPayload());
            assertEquals(Long.valueOf(5577L), events.get(1).getPk());
            assertEquals(top, events.get(1).getPayload());
            GameDataSyncEvent<?> complete = events.get(2);
            assertEquals(GameDataSnapshotPublisher.OP_SNAPSHOT_COMPLETE, complete.getOp());
            assertEquals(Integer.valueOf(2), complete.getCount());
        }
    }
}
