package app.l2nx.gs.adapter.core.appsettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppSettingsWatcherTest {

    private static final char BACKSLASH = (char) 0x5C;

    @TempDir
    Path dir;

    private Path file;
    private AppSettingsRuntime holder;
    private final List<AppRules> published = new ArrayList<AppRules>();
    private final AtomicLong modified = new AtomicLong(1_700_000_000_000L);

    @BeforeEach
    void setUp() {
        file = dir.resolve(AppSettingsFile.FILE_NAME);
        holder = new AppSettingsRuntime();
    }

    private AppSettingsWatcher watcher() {
        return new AppSettingsWatcher(file, holder, published::add);
    }

    /** Every save gets a later modification time, as an editor's save would. */
    private void save(String content) throws IOException {
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(file, FileTime.fromMillis(modified.addAndGet(1_000L)));
    }

    /** As {@code cp -p}, {@code rsync -t} or a coarse-timestamp file system leave it: the stamp does not move. */
    private void overwriteKeepingTheStamp(String content) throws IOException {
        FileTime stamp = Files.getLastModifiedTime(file);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(file, stamp);
    }

    /** Counts the reads: a poll that parses reads twice (the content, then the move check), one that skips reads once. */
    private AppSettingsWatcher countingWatcher(int[] reads) {
        return new AppSettingsWatcher(file, holder, published::add, f -> {
            reads[0]++;
            return Files.readAllBytes(f);
        });
    }

    private static AppRules threshold(int minutes) {
        return AppRules.builder().returnThresholdMinutes(minutes).build();
    }

    @Nested
    class LoadAtStart {

        @Test
        void loadAtStart_shouldApplyTheFileAndPublishIt() throws IOException {
            save("app.session.return-threshold-minutes=45\n");

            watcher().loadAtStart();

            assertEquals(threshold(45), holder.current());
            assertEquals(Collections.singletonList(threshold(45)), published);
            assertTrue(holder.fromFile());
        }

        @Test
        void loadAtStart_shouldPublishTheDefaults_whenTheFileIsMissing() {
            watcher().loadAtStart();

            assertSame(AppRules.defaults(), holder.current());
            assertEquals(Collections.singletonList(AppRules.defaults()), published);
            assertTrue(holder.fromFile());
        }

        @Test
        void loadAtStart_shouldKeepTheDefaults_whenTheFileIsInvalid() throws IOException {
            save("app.session.return-threshold-minutes=45\napp.fishing.enabled=true\n");

            watcher().loadAtStart();

            assertSame(AppRules.defaults(), holder.current());
            assertEquals(Collections.singletonList(AppRules.defaults()), published);
            assertFalse(holder.fromFile());
        }

        @Test
        void loadAtStart_shouldNotTakeTheDefaultsForTheFile_whenTheFileCannotBeRead() throws IOException {
            save("app.history.retention-days=30\n");

            new AppSettingsWatcher(file, holder, published::add, f -> {
                        throw new IOException("permission denied");
                    })
                    .loadAtStart();

            assertSame(AppRules.defaults(), holder.current());
            assertFalse(holder.fromFile());
        }

        @Test
        void loadAtStart_shouldNotTakeTheDefaultsForTheFile_whenTheFileIsNotUtf8() throws IOException {
            Files.write(file, new byte[] {'a', '=', (byte) 0xC3, (byte) 0x28});

            watcher().loadAtStart();

            assertSame(AppRules.defaults(), holder.current());
            assertFalse(holder.fromFile());
        }

        @Test
        void loadAtStart_shouldPublishTheDefaults_whenAnEscapeIsMalformed() throws IOException {
            save("app.phantom.title.text=" + BACKSLASH + "u12\n");

            watcher().loadAtStart();

            assertSame(AppRules.defaults(), holder.current());
            assertFalse(holder.fromFile());
            assertEquals(Collections.singletonList(AppRules.defaults()), published);
        }

        @Test
        void loadAtStart_shouldReadAgainAndApplyTheFile_whenAMultibyteCharacterWasCutDuringTheFirstRead()
                throws IOException {
            save("app.phantom.title.text=Рыбак\n");
            int[] reads = {0};

            new AppSettingsWatcher(file, holder, published::add, f -> {
                        byte[] full = Files.readAllBytes(f);
                        return ++reads[0] == 1 ? Arrays.copyOf(full, full.length - 2) : full;
                    })
                    .loadAtStart();

            assertEquals("Рыбак", holder.current().phantomTitleText());
            assertTrue(holder.fromFile());
        }

        @Test
        void loadAtStart_shouldReadAgainAndApplyTheFile_whenTheFileMovedDuringTheFirstRead() throws IOException {
            save("app.history.retention-days=30\n");
            int[] reads = {0};

            new AppSettingsWatcher(file, holder, published::add, f -> {
                        byte[] full = Files.readAllBytes(f);
                        return ++reads[0] == 1 ? Arrays.copyOf(full, full.length - 2) : full;
                    })
                    .loadAtStart();

            assertEquals(AppRules.builder().historyRetentionDays(30).build(), holder.current());
            assertTrue(holder.fromFile());
            assertEquals(1, published.size());
        }

        @Test
        void loadAtStart_shouldNotTakeTheDefaultsForTheFile_whenTheFileKeepsMovingOnEveryAttempt() throws IOException {
            save("app.history.retention-days=30\n");
            int[] reads = {0};

            new AppSettingsWatcher(file, holder, published::add, f -> {
                        byte[] full = Files.readAllBytes(f);
                        return Arrays.copyOf(full, full.length - (++reads[0] % 2));
                    })
                    .loadAtStart();

            assertSame(AppRules.defaults(), holder.current());
            assertFalse(holder.fromFile());
            assertEquals(Collections.singletonList(AppRules.defaults()), published);
            assertEquals(2 * AppSettingsWatcher.FIRST_LOAD_ATTEMPTS, reads[0]);
        }
    }

    @Nested
    class Poll {

        @Test
        void poll_shouldApplyAndPublishAChangedFile() throws IOException {
            save("app.session.return-threshold-minutes=45\n");
            AppSettingsWatcher watcher = watcher();
            watcher.loadAtStart();
            save("app.session.return-threshold-minutes=50\napp.private-store.enabled=true\n");

            watcher.poll();

            AppRules expected = threshold(50).toBuilder()
                    .sessionTypeEnabled(ActivityType.OFFLINE_PRIVATE_STORE, true)
                    .build();
            assertEquals(expected, holder.current());
            assertEquals(2, published.size());
            assertEquals(expected, published.get(1));
        }

        @Test
        void poll_shouldApplyTheChange_whenTheEditKeptTheSizeAndTheModificationTime() throws IOException {
            save("app.session.return-threshold-minutes=45\n");
            AppSettingsWatcher watcher = watcher();
            watcher.loadAtStart();
            overwriteKeepingTheStamp("app.session.return-threshold-minutes=50\n");

            watcher.poll();

            assertEquals(threshold(50), holder.current());
            assertEquals(2, published.size());
        }

        @Test
        void poll_shouldNeitherReplaceNorPublish_whenTheFileIsSavedAgainUnchanged() throws IOException {
            save("app.session.return-threshold-minutes=45\n");
            AppSettingsWatcher watcher = watcher();
            watcher.loadAtStart();
            AppRules loaded = holder.current();
            save("# same values\napp.session.return-threshold-minutes = 45\n");

            watcher.poll();

            assertSame(loaded, holder.current());
            assertEquals(1, published.size());
        }

        @Test
        void poll_shouldNeitherReplaceNorPublish_whenOnlyTheStampMoved() throws IOException {
            save("app.session.return-threshold-minutes=45\n");
            AppSettingsWatcher watcher = watcher();
            watcher.loadAtStart();
            AppRules loaded = holder.current();
            Files.setLastModifiedTime(file, FileTime.fromMillis(modified.addAndGet(1_000L)));

            watcher.poll();

            assertSame(loaded, holder.current());
            assertEquals(1, published.size());
        }

        @Test
        void poll_shouldKeepThePreviousValues_whenTheNewFileIsInvalid() throws IOException {
            save("app.session.return-threshold-minutes=45\n");
            AppSettingsWatcher watcher = watcher();
            watcher.loadAtStart();
            save("app.session.return-threshold-minutes=50\napp.fishing.spot-order=random\n");

            watcher.poll();

            assertEquals(threshold(45), holder.current());
            assertEquals(1, published.size());
            assertTrue(holder.fromFile());
        }

        @Test
        void poll_shouldKeepThePreviousValuesAndParseOnce_whenAnEscapeIsMalformed() throws IOException {
            save("app.session.return-threshold-minutes=45\n");
            int[] reads = {0};
            AppSettingsWatcher watcher = countingWatcher(reads);
            watcher.loadAtStart();
            save("app.session.return-threshold-minutes=50\napp.phantom.title.text=" + BACKSLASH + "u12\n");

            watcher.poll();
            int afterTheRejectingPoll = reads[0];
            watcher.poll();

            assertEquals(threshold(45), holder.current());
            assertEquals(1, published.size());
            assertEquals(afterTheRejectingPoll + 1, reads[0]);
        }

        @Test
        void poll_shouldKeepThePreviousValuesAndParseOnce_whenTheFileIsNotUtf8() throws IOException {
            save("app.session.return-threshold-minutes=45\n");
            int[] reads = {0};
            AppSettingsWatcher watcher = countingWatcher(reads);
            watcher.loadAtStart();
            Files.write(file, new byte[] {'a', '=', (byte) 0xC3, (byte) 0x28});

            watcher.poll();
            int afterTheRejectingPoll = reads[0];
            watcher.poll();

            assertEquals(threshold(45), holder.current());
            assertEquals(1, published.size());
            assertEquals(afterTheRejectingPoll + 1, reads[0]);
        }

        @Test
        void poll_shouldNeitherReplaceNorPublish_whenStoppedDuringTheRead() throws Exception {
            save("app.session.return-threshold-minutes=45\n");
            CountDownLatch reading = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            boolean[] block = {false};
            AppSettingsWatcher watcher = new AppSettingsWatcher(file, holder, published::add, f -> {
                if (block[0]) {
                    block[0] = false;
                    reading.countDown();
                    await(release);
                }
                return Files.readAllBytes(f);
            });
            watcher.loadAtStart();
            save("app.session.return-threshold-minutes=50\n");
            block[0] = true;
            CompletableFuture<Void> inFlight = CompletableFuture.runAsync(watcher::poll);
            assertTrue(reading.await(5, TimeUnit.SECONDS));

            watcher.stopPolling();
            release.countDown();
            inFlight.get(5, TimeUnit.SECONDS);

            assertEquals(threshold(45), holder.current());
            assertEquals(1, published.size());
        }

        @Test
        void poll_shouldTakeTheRulesForTheFiles_whenAValidFileFollowsARejectedOne() throws IOException {
            save("app.history.retention-days=3\n");
            AppSettingsWatcher watcher = watcher();
            watcher.loadAtStart();
            save("app.history.retention-days=90\n");

            watcher.poll();

            assertSame(AppRules.defaults(), holder.current());
            assertTrue(holder.fromFile());
        }

        @Test
        void poll_shouldKeepThePreviousValues_whenTheFileDisappears() throws IOException {
            save("app.session.return-threshold-minutes=45\n");
            AppSettingsWatcher watcher = watcher();
            watcher.loadAtStart();
            Files.delete(file);

            watcher.poll();
            watcher.poll();

            assertEquals(threshold(45), holder.current());
            assertEquals(1, published.size());
        }

        @Test
        void poll_shouldReadTheFileAgain_whenItReturnsAfterDisappearing() throws IOException {
            save("app.session.return-threshold-minutes=45\n");
            AppSettingsWatcher watcher = watcher();
            watcher.loadAtStart();
            Files.delete(file);
            watcher.poll();
            save("app.session.return-threshold-minutes=60\n");

            watcher.poll();

            assertEquals(threshold(60), holder.current());
        }

        @Test
        void poll_shouldKeepThePreviousValues_whenTheFileCannotBeRead() throws IOException {
            save("app.session.return-threshold-minutes=45\n");
            boolean[] broken = {false};
            AppSettingsWatcher watcher = new AppSettingsWatcher(file, holder, published::add, f -> {
                if (broken[0]) {
                    throw new IOException("permission denied");
                }
                return Files.readAllBytes(f);
            });
            watcher.loadAtStart();
            broken[0] = true;
            save("app.session.return-threshold-minutes=50\n");

            watcher.poll();

            assertEquals(threshold(45), holder.current());
            assertEquals(1, published.size());
        }

        @Test
        void poll_shouldWaitForTheNextTick_whenTheStampMovesDuringTheRead() throws IOException {
            save("app.session.return-threshold-minutes=45\n");
            boolean[] editorStillWriting = {false};
            AppSettingsWatcher watcher = new AppSettingsWatcher(file, holder, published::add, f -> {
                byte[] read = Files.readAllBytes(f);
                if (editorStillWriting[0]) {
                    editorStillWriting[0] = false;
                    Files.write(
                            f,
                            "\napp.private-store.enabled=true\n".getBytes(StandardCharsets.UTF_8),
                            StandardOpenOption.APPEND);
                    Files.setLastModifiedTime(f, FileTime.fromMillis(modified.addAndGet(1_000L)));
                }
                return read;
            });
            watcher.loadAtStart();
            save("app.session.return-threshold-minutes=50");
            editorStillWriting[0] = true;

            watcher.poll();
            assertEquals(threshold(45), holder.current());
            watcher.poll();

            assertEquals(
                    threshold(50).toBuilder()
                            .sessionTypeEnabled(ActivityType.OFFLINE_PRIVATE_STORE, true)
                            .build(),
                    holder.current());
            assertEquals(2, published.size());
        }

        @Test
        void poll_shouldWaitForTheNextTick_whenOnlyTheContentMovesDuringTheRead() throws IOException {
            save("app.session.return-threshold-minutes=45\n");
            boolean[] halfWritten = {false};
            AppSettingsWatcher watcher = new AppSettingsWatcher(file, holder, published::add, f -> {
                if (halfWritten[0]) {
                    halfWritten[0] = false;
                    return "app.session.return-threshold-minutes=5".getBytes(StandardCharsets.UTF_8);
                }
                return Files.readAllBytes(f);
            });
            watcher.loadAtStart();
            overwriteKeepingTheStamp("app.session.return-threshold-minutes=50\n");
            halfWritten[0] = true;

            watcher.poll();
            assertEquals(threshold(45), holder.current());
            watcher.poll();

            assertEquals(threshold(50), holder.current());
            assertEquals(2, published.size());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("not released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
