package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.adapter.api.kafka.NxHeaders;
import app.l2nx.gs.adapter.api.kafka.sync.settings.SettingsSyncEvent;
import app.l2nx.gs.adapter.api.kafka.sync.settings.app.AppSettings;
import app.l2nx.gs.adapter.api.rest.SyncTopics;
import app.l2nx.gs.kafka.KafkaException;
import app.l2nx.gs.kafka.NxKafka;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jspecify.annotations.Nullable;

/**
 * Keyed by the raw 16-byte {@code serverId} like the gd stream so one server's snapshots stay ordered. The latest
 * snapshot is resent after every reconnect; one equal to the last sent apart from {@code updatedAt} is skipped.
 */
final class AppSettingsPublisher {

    private static final NxLog log = NxLogFactory.getLogger(AppSettingsPublisher.class);

    static final String MESSAGE_TYPE = SettingsSyncEvent.class.getSimpleName();

    interface Sender {
        void send(ProducerRecord<byte[], Object> record, Callback callback);
    }

    private final Sender sender;
    private final LongSupplier clockMs;

    private boolean connected;
    private @Nullable String topic;
    private byte @Nullable [] key;
    private boolean warnedNoTopic;
    private @Nullable AppSettings latest;
    private @Nullable AppSettings sent;

    AppSettingsPublisher(Sender sender, LongSupplier clockMs) {
        this.sender = sender;
        this.clockMs = clockMs;
    }

    static AppSettingsPublisher viaNxKafka() {
        return new AppSettingsPublisher(AppSettingsPublisher::sendViaNxKafka, System::currentTimeMillis);
    }

    synchronized void connect(SyncTopics topics, @Nullable UUID serverId) {
        String resolved = topics.getSettings().get(AppSettings.ENTITY_NAME);
        topic = resolved == null || resolved.isEmpty() ? null : resolved;
        key = serverId == null ? null : NxHeaders.encodeUuid(serverId);
        connected = true;
        sent = null;
        AppSettings snapshot = latest;
        if (snapshot != null) {
            sendLocked(snapshot);
        }
    }

    synchronized void disconnect() {
        connected = false;
    }

    synchronized void publish(AppSettings settings) {
        latest = settings;
        if (!connected) {
            return;
        }
        AppSettings previous = sent;
        if (previous != null && sameIgnoringUpdatedAt(previous, settings)) {
            log.debug("App settings unchanged since the last snapshot, not republished");
            return;
        }
        sendLocked(settings);
    }

    private void sendLocked(AppSettings settings) {
        String target = topic;
        if (target == null) {
            if (!warnedNoTopic) {
                warnedNoTopic = true;
                log.warn(
                        "No settings topic for '{}' in syncTopics.settings, app settings are not published",
                        AppSettings.ENTITY_NAME);
            }
            return;
        }
        SettingsSyncEvent<AppSettings> event =
                new SettingsSyncEvent<AppSettings>(AppSettings.ENTITY_NAME, settings, clockMs.getAsLong());
        ProducerRecord<byte[], Object> record = new ProducerRecord<byte[], Object>(target, key, event);
        record.headers().add(NxHeaders.NX_MESSAGE_TYPE, MESSAGE_TYPE.getBytes(StandardCharsets.UTF_8));
        sent = settings;
        try {
            sender.send(record, (metadata, exception) -> {
                if (exception != null) {
                    onFailed(settings, exception);
                }
            });
        } catch (Throwable t) {
            onFailed(settings, t);
        }
    }

    /** A lost send must not make the next identical publish look redundant. */
    private void onFailed(AppSettings settings, Throwable cause) {
        log.warn("Publishing app settings failed, sent again with the next publish: {}", cause.getMessage());
        synchronized (this) {
            if (sent == settings) {
                sent = null;
            }
        }
    }

    private static boolean sameIgnoringUpdatedAt(AppSettings a, AppSettings b) {
        return a.toBuilder()
                .updatedAt(Instant.EPOCH)
                .build()
                .equals(b.toBuilder().updatedAt(Instant.EPOCH).build());
    }

    private static void sendViaNxKafka(ProducerRecord<byte[], Object> record, Callback callback) {
        try {
            NxKafka.instance().sendBytesKeyRecord(record, callback);
        } catch (KafkaException notConfigured) {
            callback.onCompletion(null, notConfigured);
        }
    }
}
