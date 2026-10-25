package app.l2nx.gs.adapter.core.settings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.kafka.NxHeaders;
import app.l2nx.gs.adapter.api.kafka.sync.settings.SettingsSyncEvent;
import app.l2nx.gs.adapter.api.kafka.sync.settings.game.GameSettings;
import app.l2nx.gs.adapter.api.kafka.sync.settings.game.MailSettings;
import app.l2nx.gs.adapter.api.rest.SyncTopics;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SettingsPublisherTest {

    private static final UUID SERVER_ID = UUID.fromString("0192f000-0000-7000-8000-0000000000aa");
    private static final String TOPIC = "tenant.gs.settings.sync";
    private static final SyncTopics TOPICS =
            new SyncTopics(null, null, null, Collections.singletonMap(GameSettings.ENTITY_NAME, TOPIC));
    private static final SyncTopics NO_TOPICS = new SyncTopics(null, null, null, null);
    private static final Instant T0 = Instant.parse("2026-10-15T10:00:00Z");

    private final List<ProducerRecord<byte[], Object>> sent = new ArrayList<ProducerRecord<byte[], Object>>();
    private final List<Callback> callbacks = new ArrayList<Callback>();
    private final RecordingLog log = new RecordingLog();
    private final MutableClock clock = new MutableClock(T0);
    private SettingsPublisher<GameSettings> publisher;

    @BeforeEach
    void setUp() {
        publisher = publisherWith((record, callback) -> {
            sent.add(record);
            callbacks.add(callback);
        });
    }

    private SettingsPublisher<GameSettings> publisherWith(SettingsPublisher.Sender sender) {
        return new SettingsPublisher<GameSettings>(
                GameSettings.ENTITY_NAME,
                s -> s.toBuilder().updatedAt(Instant.EPOCH).build(),
                sender,
                clock,
                log);
    }

    private void connect(SettingsPublisher<GameSettings> target, SyncTopics topics) {
        target.connect(topics, SERVER_ID, Runnable::run);
    }

    private static GameSettings settings(long feeAdena, Instant updatedAt) {
        return GameSettings.builder()
                .mail(MailSettings.builder()
                        .feeAdena(feeAdena)
                        .feeAdenaPerAttachment(1_000L)
                        .build())
                .updatedAt(updatedAt)
                .build();
    }

    private void resend(long feeAdena) {
        SettingsPublisher.Build build = publisher.startBuild();
        publisher.resend(build, settings(feeAdena, build.builtAt));
    }

    private void publish(long feeAdena) {
        publisher.publish(builtAt -> settings(feeAdena, builtAt));
    }

    private GameSettings payload(int index) {
        return (GameSettings) ((SettingsSyncEvent<?>) sent.get(index).value()).getPayload();
    }

    @Nested
    class Publish {

        @Test
        void resend_shouldSendEnvelopeKeyedByServerId_whenConnected() {
            connect(publisher, TOPICS);
            SettingsPublisher.Build build = publisher.startBuild();
            GameSettings snapshot = settings(100L, build.builtAt);

            publisher.resend(build, snapshot);

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
            assertEquals(GameSettings.ENTITY_NAME, event.getEntityName());
            assertSame(snapshot, event.getPayload());
            assertEquals(T0.toEpochMilli(), event.getTimestampEpochMs());
        }

        @Test
        void publish_shouldStampTheBuildMoment() {
            connect(publisher, TOPICS);

            publish(100L);

            assertEquals(T0, payload(0).getUpdatedAt());
        }

        @Test
        void publish_shouldNotResend_whenOnlyUpdatedAtChanged() {
            connect(publisher, TOPICS);
            publish(100L);
            clock.now = T0.plusSeconds(60);

            publish(100L);

            assertEquals(1, sent.size());
        }

        @Test
        void publish_shouldSend_whenAValueChanged() {
            connect(publisher, TOPICS);
            publish(100L);

            publish(200L);

            assertEquals(2, sent.size());
        }

        @Test
        void publish_shouldResendTheSameSnapshot_whenThePreviousSendFailed() {
            connect(publisher, TOPICS);
            publish(100L);
            callbacks.get(0).onCompletion(null, new RuntimeException("broker down"));

            publish(100L);

            assertEquals(2, sent.size());
        }

        @Test
        void publish_shouldResendTheSameSnapshot_whenTheSenderThrew() {
            SettingsPublisher<GameSettings> throwing = publisherWith((record, callback) -> {
                sent.add(record);
                if (sent.size() == 1) {
                    throw new IllegalStateException("producer closed");
                }
            });
            connect(throwing, TOPICS);
            throwing.publish(builtAt -> settings(100L, builtAt));

            throwing.publish(builtAt -> settings(100L, builtAt));

            assertEquals(2, sent.size());
            assertEquals(1, log.warnings().size());
        }

        @Test
        void publish_shouldSendOutsideTheStateMonitor() {
            List<Boolean> heldDuringSend = new ArrayList<Boolean>();
            AtomicReference<SettingsPublisher<GameSettings>> self =
                    new AtomicReference<SettingsPublisher<GameSettings>>();
            self.set(publisherWith((record, callback) -> heldDuringSend.add(Thread.holdsLock(self.get()))));
            connect(self.get(), TOPICS);

            self.get().publish(builtAt -> settings(100L, builtAt));
            self.get().resend(builtAt -> settings(200L, builtAt));
            SettingsPublisher.Build build = self.get().startBuild();
            self.get().resend(build, settings(300L, build.builtAt));

            assertEquals(3, heldDuringSend.size());
            assertFalse(heldDuringSend.contains(Boolean.TRUE));
        }

        @Test
        void publish_shouldSendNothing_whenDisconnected() {
            connect(publisher, TOPICS);
            publisher.disconnect();

            publish(100L);

            assertTrue(sent.isEmpty());
        }
    }

    @Nested
    class Ordering {

        @Test
        void resend_shouldDropASnapshot_whenItsBuildStartedBeforeTheLatestOne() {
            connect(publisher, TOPICS);
            SettingsPublisher.Build earlier = publisher.startBuild();
            SettingsPublisher.Build later = publisher.startBuild();
            publisher.resend(later, settings(200L, later.builtAt));

            publisher.resend(earlier, settings(100L, earlier.builtAt));

            assertEquals(1, sent.size());
            assertEquals(200L, payload(0).getMail().getFeeAdena());
        }

        @Test
        void resend_shouldSend_whenTheClockSteppedBackBetweenBuilds() {
            connect(publisher, TOPICS);
            clock.now = T0.plusSeconds(600);
            resend(100L);
            clock.now = T0;

            resend(200L);

            assertEquals(2, sent.size());
            assertEquals(T0, payload(1).getUpdatedAt());
        }

        @Test
        void resend_shouldSendAnEqualSnapshotAgain() {
            connect(publisher, TOPICS);
            publish(100L);

            resend(100L);
            publisher.resend(builtAt -> settings(100L, builtAt));

            assertEquals(3, sent.size());
        }
    }

    @Nested
    class FailedSend {

        @Test
        void onFailed_shouldResendTheSkippedSnapshot_whenTheEqualOneInFlightFailed() {
            connect(publisher, TOPICS);
            publish(100L);
            clock.now = T0.plusSeconds(60);
            publish(100L);
            assertEquals(1, sent.size());

            callbacks.get(0).onCompletion(null, new RuntimeException("broker down"));

            assertEquals(2, sent.size());
            assertEquals(T0.plusSeconds(60), payload(1).getUpdatedAt());
        }

        @Test
        void onFailed_shouldResendNothing_whenNoNewerSnapshotWasSkipped() {
            connect(publisher, TOPICS);
            publish(100L);

            callbacks.get(0).onCompletion(null, new RuntimeException("broker down"));

            assertEquals(1, sent.size());
        }

        @Test
        void onFailed_shouldResendNothing_whenANewerSnapshotWasAlreadySent() {
            connect(publisher, TOPICS);
            publish(100L);
            publish(200L);

            callbacks.get(0).onCompletion(null, new RuntimeException("broker down"));

            assertEquals(2, sent.size());
        }
    }

    @Nested
    class MissingTopic {

        @Test
        void publish_shouldSkipAndLogOneError_whenTheEntityIsMissingFromTheSettingsMap() {
            connect(publisher, NO_TOPICS);

            publish(100L);
            resend(200L);

            assertTrue(sent.isEmpty());
            assertEquals(1, log.errors().size());
        }

        @Test
        void connect_shouldLogTheMissingEntityAgain_whenAHandshakeFollows() {
            connect(publisher, NO_TOPICS);
            publish(100L);

            connect(publisher, NO_TOPICS);
            resend(100L);

            assertEquals(2, log.errors().size());
        }

        @Test
        void resend_shouldSend_whenTheEntityAppearedInTheSettingsMapOnTheNextHandshake() {
            connect(publisher, NO_TOPICS);
            publish(100L);

            connect(publisher, TOPICS);
            resend(100L);

            assertEquals(1, sent.size());
        }

        @Test
        void connect_shouldLogNothing_whenNothingIsSent() {
            connect(publisher, NO_TOPICS);

            assertTrue(log.errors().isEmpty());
        }
    }

    @Nested
    class Connect {

        @Test
        void connect_shouldSendNothingItself_whenASnapshotIsKept() {
            publish(100L);

            connect(publisher, TOPICS);

            assertTrue(sent.isEmpty());
        }

        @Test
        void publish_shouldSendAnEqualSnapshotAgain_whenAHandshakeFollows() {
            connect(publisher, TOPICS);
            publish(100L);
            publisher.disconnect();

            connect(publisher, TOPICS);
            publish(100L);

            assertEquals(2, sent.size());
        }
    }

    private static final class MutableClock extends Clock {
        volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
