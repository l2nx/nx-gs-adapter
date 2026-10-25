package app.l2nx.gs.adapter.core.settings;

import app.l2nx.gs.adapter.api.kafka.NxHeaders;
import app.l2nx.gs.adapter.api.kafka.sync.settings.SettingsSyncEvent;
import app.l2nx.gs.adapter.api.rest.SyncTopics;
import app.l2nx.gs.kafka.KafkaException;
import app.l2nx.gs.kafka.NxKafka;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jspecify.annotations.Nullable;

/**
 * One settings entity on {@code syncTopics.settings[entityName]}, keyed by the raw {@code serverId} so a server's
 * snapshots stay ordered. A snapshot built before the latest accepted one is dropped; build order is a sequence, not
 * the wall clock, which may step back.
 */
final class SettingsPublisher<T> {

    private static final NxLog defaultLog = NxLogFactory.getLogger(SettingsPublisher.class);

    static final String MESSAGE_TYPE = SettingsSyncEvent.class.getSimpleName();

    interface Sender {
        void send(ProducerRecord<byte[], Object> record, Callback callback);
    }

    static final class Build {
        final long sequence;
        final Instant builtAt;

        Build(long sequence, Instant builtAt) {
            this.sequence = sequence;
            this.builtAt = builtAt;
        }
    }

    private final String entityName;
    private final UnaryOperator<T> withoutUpdatedAt;
    private final Sender sender;
    private final Clock clock;
    private final NxLog log;
    private final AtomicLong builds = new AtomicLong();

    /** Records leave in acceptance order. {@code this} is never held across a send: the producer may block up to
     * {@code max.block.ms} and the failure callback on the producer I/O thread needs it. */
    private final Object sendOrder = new Object();

    private boolean connected;
    private @Nullable String topic;
    private byte @Nullable [] key;
    private boolean reportedNoTopic;
    private Executor retries = Runnable::run;
    private @Nullable T latest;
    private @Nullable Build latestBuild;
    private @Nullable T sent;

    SettingsPublisher(String entityName, UnaryOperator<T> withoutUpdatedAt, Sender sender, Clock clock, NxLog log) {
        this.entityName = entityName;
        this.withoutUpdatedAt = withoutUpdatedAt;
        this.sender = sender;
        this.clock = clock;
        this.log = log;
    }

    static <T> SettingsPublisher<T> viaNxKafka(String entityName, UnaryOperator<T> withoutUpdatedAt) {
        return new SettingsPublisher<T>(
                entityName, withoutUpdatedAt, SettingsPublisher::sendViaNxKafka, Clock.systemUTC(), defaultLog);
    }

    /**
     * Sends nothing: the module sends the current snapshot once started.
     *
     * @param retries runs a resend after a failed send, off the producer I/O thread
     */
    synchronized void connect(SyncTopics topics, @Nullable UUID serverId, Executor retries) {
        String resolved = topics.getSettings().get(entityName);
        topic = resolved == null || resolved.isEmpty() ? null : resolved;
        key = serverId == null ? null : NxHeaders.encodeUuid(serverId);
        this.retries = retries;
        connected = true;
        reportedNoTopic = false;
        sent = null;
    }

    synchronized void disconnect() {
        connected = false;
    }

    Build startBuild() {
        return new Build(builds.incrementAndGet(), clock.instant());
    }

    void resend(Build build, T snapshot) {
        send(() -> acceptLocked(build, snapshot, false));
    }

    void publish(Function<Instant, T> rebuild) {
        send(() -> buildLocked(rebuild, true));
    }

    void resend(Function<Instant, T> rebuild) {
        send(() -> buildLocked(rebuild, false));
    }

    private @Nullable Pending<T> buildLocked(Function<Instant, T> rebuild, boolean skipUnchanged) {
        Build build = startBuild();
        return acceptLocked(build, rebuild.apply(build.builtAt), skipUnchanged);
    }

    private void send(Supplier<@Nullable Pending<T>> acceptUnderMonitor) {
        synchronized (sendOrder) {
            Pending<T> pending;
            synchronized (this) {
                pending = acceptUnderMonitor.get();
            }
            dispatch(pending);
        }
    }

    private @Nullable Pending<T> acceptLocked(Build build, T snapshot, boolean skipUnchanged) {
        Build newest = latestBuild;
        if (newest != null && build.sequence < newest.sequence) {
            log.debug(
                    "Settings '{}' build {} started before build {}, dropped",
                    entityName,
                    build.sequence,
                    newest.sequence);
            return null;
        }
        latest = snapshot;
        latestBuild = build;
        if (!connected) {
            return null;
        }
        T previous = sent;
        if (skipUnchanged && previous != null && sameIgnoringUpdatedAt(previous, snapshot)) {
            log.debug("Settings '{}' unchanged since the last snapshot, not republished", entityName);
            return null;
        }
        String target = topic;
        if (target == null) {
            if (!reportedNoTopic) {
                reportedNoTopic = true;
                log.error(
                        "No settings topic for '{}' in syncTopics.settings of the tenant directory, these settings are"
                                + " not published until the next connect",
                        entityName);
            }
            return null;
        }
        SettingsSyncEvent<T> event = new SettingsSyncEvent<T>(entityName, snapshot, clock.millis());
        ProducerRecord<byte[], Object> record = new ProducerRecord<byte[], Object>(target, key, event);
        record.headers().add(NxHeaders.NX_MESSAGE_TYPE, MESSAGE_TYPE.getBytes(StandardCharsets.UTF_8));
        sent = snapshot;
        return new Pending<T>(record, snapshot, build);
    }

    private boolean sameIgnoringUpdatedAt(T a, T b) {
        return withoutUpdatedAt.apply(a).equals(withoutUpdatedAt.apply(b));
    }

    private void dispatch(@Nullable Pending<T> pending) {
        if (pending == null) {
            return;
        }
        try {
            sender.send(pending.record, (metadata, exception) -> {
                if (exception != null) {
                    onFailed(pending, exception);
                }
            });
        } catch (Throwable t) {
            onFailed(pending, t);
        }
    }

    private void onFailed(Pending<T> failed, Throwable cause) {
        log.warn(
                "Publishing settings '{}' failed, sent again with the next tick or handshake: {}",
                entityName,
                cause.getMessage());
        T skipped;
        Build skippedBuild;
        Executor executor;
        synchronized (this) {
            if (sent != failed.snapshot) {
                return;
            }
            sent = null;
            skipped = latest;
            skippedBuild = latestBuild;
            executor = retries;
            if (!connected
                    || skipped == null
                    || skipped == failed.snapshot
                    || skippedBuild == null
                    || skippedBuild.sequence <= failed.build.sequence) {
                return;
            }
        }
        T retry = skipped;
        Build retryBuild = skippedBuild;
        try {
            executor.execute(() -> resend(retryBuild, retry));
        } catch (Throwable t) {
            log.debug("Settings '{}' resend after a failed send not scheduled: {}", entityName, t.getMessage());
        }
    }

    private static void sendViaNxKafka(ProducerRecord<byte[], Object> record, Callback callback) {
        try {
            NxKafka.instance().sendBytesKeyRecord(record, callback);
        } catch (KafkaException notConfigured) {
            callback.onCompletion(null, notConfigured);
        }
    }

    private static final class Pending<T> {
        final ProducerRecord<byte[], Object> record;
        final T snapshot;
        final Build build;

        Pending(ProducerRecord<byte[], Object> record, T snapshot, Build build) {
            this.record = record;
            this.snapshot = snapshot;
            this.build = build;
        }
    }
}
