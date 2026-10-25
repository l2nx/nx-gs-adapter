package app.l2nx.gs.adapter.core.appsettings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.NxHeaders;
import app.l2nx.gs.adapter.api.kafka.sync.settings.SettingsSyncEvent;
import app.l2nx.gs.adapter.api.kafka.sync.settings.app.AppSessionTypeSettings;
import app.l2nx.gs.adapter.api.kafka.sync.settings.app.AppSettings;
import app.l2nx.gs.adapter.api.rest.SyncTopics;
import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AppSettingsPublisherTest {

    private static final UUID SERVER_ID = UUID.fromString("0192f000-0000-7000-8000-0000000000aa");
    private static final String TOPIC = "tenant.gs.settings.sync";
    private static final SyncTopics TOPICS =
            new SyncTopics(null, null, null, Collections.singletonMap(AppSettings.ENTITY_NAME, TOPIC));

    private final List<ProducerRecord<byte[], Object>> sent = new ArrayList<ProducerRecord<byte[], Object>>();
    private final List<Callback> callbacks = new ArrayList<Callback>();
    private AppSettingsPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AppSettingsPublisher(
                (record, callback) -> {
                    sent.add(record);
                    callbacks.add(callback);
                },
                () -> 1_000L);
    }

    private static AppSettings settings(int returnThresholdMinutes, String updatedAt) {
        return AppSettings.builder()
                .sessionTypes(Collections.singletonList(new AppSessionTypeSettings(ActivityType.OFFLINE_FISHING, true)))
                .recentlyLeftGameMinutes(2)
                .fishingStartCooldownMinutes(15)
                .privateStoreStartCooldownMinutes(5)
                .returnThresholdMinutes(returnThresholdMinutes)
                .restartWindowMinutes(10)
                .fishingMinDurationMs(600_000L)
                .fishingMaxDurationMs(14_400_000L)
                .updatedAt(Instant.parse(updatedAt))
                .build();
    }

    @Nested
    class Publish {

        @Test
        void publish_shouldSendEnvelopeKeyedByServerId_whenConnected() {
            publisher.connect(TOPICS, SERVER_ID);
            AppSettings snapshot = settings(30, "2026-10-15T10:00:00Z");

            publisher.publish(snapshot);

            assertEquals(1, sent.size());
            ProducerRecord<byte[], Object> record = sent.get(0);
            assertEquals(TOPIC, record.topic());
            assertArrayEquals(NxHeaders.encodeUuid(SERVER_ID), record.key());
            assertEquals(
                    "SettingsSyncEvent",
                    new String(
                            record.headers()
                                    .lastHeader(NxHeaders.NX_MESSAGE_TYPE)
                                    .value(),
                            StandardCharsets.UTF_8));
            SettingsSyncEvent<?> event = (SettingsSyncEvent<?>) record.value();
            assertEquals(AppSettings.ENTITY_NAME, event.getEntityName());
            assertSame(snapshot, event.getPayload());
            assertEquals(1_000L, event.getTimestampEpochMs());
        }

        @Test
        void publish_shouldNotResend_whenOnlyUpdatedAtChanged() {
            publisher.connect(TOPICS, SERVER_ID);
            publisher.publish(settings(30, "2026-10-15T10:00:00Z"));

            publisher.publish(settings(30, "2026-10-15T11:00:00Z"));

            assertEquals(1, sent.size());
        }

        @Test
        void publish_shouldSend_whenAValueChanged() {
            publisher.connect(TOPICS, SERVER_ID);
            publisher.publish(settings(30, "2026-10-15T10:00:00Z"));

            publisher.publish(settings(45, "2026-10-15T11:00:00Z"));

            assertEquals(2, sent.size());
        }

        @Test
        void publish_shouldResendTheSameSnapshot_whenThePreviousSendFailed() {
            publisher.connect(TOPICS, SERVER_ID);
            publisher.publish(settings(30, "2026-10-15T10:00:00Z"));
            callbacks.get(0).onCompletion(null, new RuntimeException("broker down"));

            publisher.publish(settings(30, "2026-10-15T10:00:00Z"));

            assertEquals(2, sent.size());
        }

        @Test
        void publish_shouldSkip_whenSettingsTopicIsMissing() {
            publisher.connect(new SyncTopics(null, null, null, null), SERVER_ID);

            publisher.publish(settings(30, "2026-10-15T10:00:00Z"));
            publisher.publish(settings(45, "2026-10-15T10:00:00Z"));

            assertTrue(sent.isEmpty());
        }
    }

    @Nested
    class PublishRules {

        @Test
        void toSettings_shouldCarryEverySessionTypeAndTheSnapshotKeys() {
            AppRules rules = AppRules.builder()
                    .sessionTypeEnabled(ActivityType.OFFLINE_FISHING, true)
                    .fishingSpotPool("harbor")
                    .recentLogoutMinutes(3)
                    .fishingStartCooldownMinutes(20)
                    .privateStoreStartCooldownMinutes(8)
                    .returnThresholdMinutes(40)
                    .restartWindowMinutes(25)
                    .fishingMinDurationMinutes(10)
                    .fishingMaxDurationMinutes(240)
                    .build();

            AppSettings actual = AppSettingsPublisher.toSettings(rules, Instant.parse("2026-10-23T10:00:00Z"));

            AppSettings expected = AppSettings.builder()
                    .sessionTypes(Arrays.asList(
                            new AppSessionTypeSettings(ActivityType.OFFLINE_PRIVATE_STORE, false),
                            new AppSessionTypeSettings(ActivityType.OFFLINE_FISHING, true)))
                    .recentlyLeftGameMinutes(3)
                    .fishingStartCooldownMinutes(20)
                    .privateStoreStartCooldownMinutes(8)
                    .returnThresholdMinutes(40)
                    .restartWindowMinutes(25)
                    .fishingMinDurationMs(600_000L)
                    .fishingMaxDurationMs(14_400_000L)
                    .updatedAt(Instant.parse("2026-10-23T10:00:00Z"))
                    .build();
            assertEquals(expected, actual);
        }

        @Test
        void publish_shouldNotResend_whenOnlyValuesOutsideTheSnapshotChanged() {
            publisher.connect(TOPICS, SERVER_ID);
            publisher.publish(AppRules.defaults());

            publisher.publish(AppRules.builder()
                    .fishingFloatDistance(200)
                    .restoreBatchSize(2)
                    .build());

            assertEquals(1, sent.size());
        }
    }

    @Nested
    class Connect {

        @Test
        void connect_shouldSendTheSnapshotKept_whenPublishedBeforeTheConnection() {
            AppSettings snapshot = settings(30, "2026-10-15T10:00:00Z");
            publisher.publish(snapshot);
            assertTrue(sent.isEmpty());

            publisher.connect(TOPICS, SERVER_ID);

            assertEquals(1, sent.size());
            assertSame(snapshot, ((SettingsSyncEvent<?>) sent.get(0).value()).getPayload());
        }

        @Test
        void connect_shouldSendTheLastSnapshotAgain_whenReconnected() {
            publisher.connect(TOPICS, SERVER_ID);
            publisher.publish(settings(30, "2026-10-15T10:00:00Z"));
            publisher.disconnect();

            publisher.connect(TOPICS, SERVER_ID);

            assertEquals(2, sent.size());
        }

        @Test
        void connect_shouldSendNothing_whenNoSnapshotIsKnown() {
            publisher.connect(TOPICS, SERVER_ID);

            assertTrue(sent.isEmpty());
        }
    }
}
