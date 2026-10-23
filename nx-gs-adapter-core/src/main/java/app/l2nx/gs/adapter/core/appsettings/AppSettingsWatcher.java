package app.l2nx.gs.adapter.core.appsettings;

import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import app.l2nx.gs.commons.concurrent.DaemonThreadFactory;
import app.l2nx.gs.commons.concurrent.SafeRunnable;
import app.l2nx.gs.log.NxLog;
import app.l2nx.gs.log.NxLogFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Every tick reads the file whole and compares bytes: modification time and size can survive an edit (coarse
 * timestamps, {@code cp -p}, {@code rsync -t}, a same-length change). {@link #loadAtStart()} and {@link #poll()} never
 * run concurrently. Once {@link #stopPolling()} returns, a poll in flight neither replaces the rules nor publishes.
 */
final class AppSettingsWatcher {

    private static final NxLog log = NxLogFactory.getLogger(AppSettingsWatcher.class);

    static final String THREAD_NAME = "nx-app-settings-watcher";
    static final long POLL_PERIOD_MS = 5_000L;
    static final int FIRST_LOAD_ATTEMPTS = 3;
    static final long FIRST_LOAD_RETRY_PAUSE_MS = 50L;
    static final long STOP_TIMEOUT_MS = 2_000L;

    private static final String NOT_APPLIED = "app settings not applied, previous values stay: {}";

    interface ContentSource {
        byte[] read(Path file) throws IOException;
    }

    private final Path file;
    private final AppSettingsRuntime holder;
    private final Consumer<AppRules> onApplied;
    private final ContentSource source;

    private @Nullable FileState seen;
    private boolean missingWarned;
    private boolean unreadableLogged;
    private @Nullable ScheduledExecutorService scheduler;
    /** Written under the monitor, so a change checked there cannot land after {@link #stopPolling()}. */
    private volatile boolean stopped;

    AppSettingsWatcher(Path file, AppSettingsRuntime holder, Consumer<AppRules> onApplied) {
        this(file, holder, onApplied, Files::readAllBytes);
    }

    AppSettingsWatcher(Path file, AppSettingsRuntime holder, Consumer<AppRules> onApplied, ContentSource source) {
        this.file = file;
        this.holder = holder;
        this.onApplied = onApplied;
        this.source = source;
    }

    /**
     * A file that changes while read is read again, up to {@value #FIRST_LOAD_ATTEMPTS} times, so a boot racing an
     * editor does not run on the defaults. The snapshot goes out either way.
     */
    void loadAtStart() {
        try {
            for (int attempt = 1; attempt <= FIRST_LOAD_ATTEMPTS; attempt++) {
                FileState state = readState();
                if (state == null) {
                    matchesFile();
                    log.info(
                            "App settings file {} not found, the defaults apply (every session type off)",
                            file.toAbsolutePath());
                    break;
                }
                if (read(state, true)) {
                    break;
                }
                if (attempt == FIRST_LOAD_ATTEMPTS) {
                    log.warn(
                            "App settings file {} kept changing during the first load, the defaults apply until the"
                                    + " next poll",
                            file.toAbsolutePath());
                } else {
                    pauseBeforeRetry();
                }
            }
        } catch (IOException e) {
            unreadable(e);
        }
        publishCurrent();
    }

    private synchronized void publishCurrent() {
        if (!stopped) {
            onApplied.accept(holder.current());
        }
    }

    private synchronized void matchesFile() {
        if (!stopped) {
            holder.matchesFile();
        }
    }

    private static void pauseBeforeRetry() {
        try {
            Thread.sleep(FIRST_LOAD_RETRY_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void poll() {
        FileState state;
        try {
            state = readState();
        } catch (IOException e) {
            unreadable(e);
            return;
        }
        unreadableLogged = false;
        if (state == null) {
            if (seen != null && !missingWarned) {
                log.warn("App settings file {} disappeared, the previous values stay", file.toAbsolutePath());
                missingWarned = true;
            }
            seen = null;
            matchesFile();
            return;
        }
        missingWarned = false;
        if (seen == null || !seen.sameContent(state)) {
            read(state, false);
        }
    }

    synchronized void startPolling() {
        if (scheduler != null || stopped) {
            return;
        }
        ScheduledExecutorService started =
                Executors.newSingleThreadScheduledExecutor(DaemonThreadFactory.named(THREAD_NAME, log));
        // SafeRunnable: a throw would cancel the periodic task and silently end every later reload
        started.scheduleWithFixedDelay(
                SafeRunnable.wrap(this::poll, log), POLL_PERIOD_MS, POLL_PERIOD_MS, TimeUnit.MILLISECONDS);
        scheduler = started;
    }

    /** For good: a stopped watcher never starts again. Waits up to {@value #STOP_TIMEOUT_MS} ms for a poll in flight. */
    void stopPolling() {
        ScheduledExecutorService running;
        synchronized (this) {
            stopped = true;
            running = scheduler;
            scheduler = null;
        }
        if (running == null) {
            return;
        }
        running.shutdownNow();
        try {
            if (!running.awaitTermination(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                log.warn("App settings watcher still busy after {} ms, its poll is discarded", STOP_TIMEOUT_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * {@code false}: the file moved during the read, {@code seen} stays. Otherwise {@code seen} moves whatever the
     * content, so a broken file is logged once, not every tick.
     */
    private boolean read(FileState first, boolean firstLoad) {
        AppSettingsFile.@Nullable Result result;
        @Nullable String undecodable = null;
        try {
            result = AppSettingsFile.parse(utf8(first.content));
        } catch (IOException e) {
            // also a multibyte character cut by an editor still writing, so the move check below runs first
            result = null;
            undecodable = e.getMessage();
        }
        if (!first.equals(readStateQuietly())) {
            return false;
        }
        seen = first;
        if (result == null) {
            log.error(NOT_APPLIED, "file " + file.toAbsolutePath() + " is not valid UTF-8: " + undecodable);
            return true;
        }
        AppRules rules = result.rules();
        if (rules == null) {
            for (String error : result.errors()) {
                log.error(NOT_APPLIED, error);
            }
            return true;
        }
        apply(rules, firstLoad);
        return true;
    }

    private synchronized void apply(AppRules rules, boolean firstLoad) {
        if (stopped) {
            return;
        }
        List<String> changes = AppSettingsCatalog.diff(holder.current(), rules);
        if (firstLoad) {
            holder.applied(rules);
            log.info(
                    "App settings loaded from {}: {}",
                    file.toAbsolutePath(),
                    changes.isEmpty() ? "all defaults" : "differ from the defaults: " + String.join("; ", changes));
            return;
        }
        if (changes.isEmpty()) {
            holder.matchesFile();
            return;
        }
        holder.applied(rules);
        log.info("app settings updated: {}", String.join("; ", changes));
        onApplied.accept(rules);
    }

    private void unreadable(IOException e) {
        // a read interrupted by stopPolling is no file error
        if (!unreadableLogged && !stopped) {
            unreadableLogged = true;
            log.error(NOT_APPLIED, "file " + file.toAbsolutePath() + " cannot be read: " + e.getMessage(), e);
        }
    }

    /** {@code null}: no such file. */
    private @Nullable FileState readState() throws IOException {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(file, BasicFileAttributes.class);
        } catch (NoSuchFileException e) {
            return null;
        }
        byte[] content;
        try {
            content = source.read(file);
        } catch (NoSuchFileException e) {
            return null;
        }
        return new FileState(attributes.lastModifiedTime().toMillis(), attributes.size(), content);
    }

    private @Nullable FileState readStateQuietly() {
        try {
            return readState();
        } catch (IOException e) {
            return null;
        }
    }

    /** Strict: malformed bytes throw instead of turning into replacement characters. */
    private static Reader utf8(byte[] content) {
        return new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8.newDecoder());
    }

    private static final class FileState {
        private final long modifiedMs;
        private final long size;
        private final byte[] content;

        FileState(long modifiedMs, long size, byte[] content) {
            this.modifiedMs = modifiedMs;
            this.size = size;
            this.content = content;
        }

        boolean sameContent(FileState other) {
            return Arrays.equals(content, other.content);
        }

        /** The stamp and the content: a read is trusted only when both held still across it. */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FileState)) return false;
            FileState that = (FileState) o;
            return modifiedMs == that.modifiedMs && size == that.size && sameContent(that);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * Long.hashCode(modifiedMs) + Long.hashCode(size)) + Arrays.hashCode(content);
        }
    }
}
