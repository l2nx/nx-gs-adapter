package app.l2nx.gs.adapter.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.gs.adapter.api.kafka.sync.settings.game.GameSettings;
import app.l2nx.gs.adapter.api.spi.provider.GameSettingsProvider;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GameSettingsSourceTest {

    private static final Instant BUILT_AT = Instant.parse("2026-10-25T12:00:00Z");

    private final RecordingLog log = new RecordingLog();
    private final GameSettingsProvider provider = mock(GameSettingsProvider.class);

    @Nested
    class Discovery {

        @Test
        void of_shouldBeAbsentAndLogNothing_whenNoProviderIsRegistered() {
            GameSettingsSource source = GameSettingsSource.of(Collections.<GameSettingsProvider>emptyList(), log);

            assertFalse(source.present());
            assertNull(source.snapshot(BUILT_AT));
            assertTrue(log.errors().isEmpty());
            assertTrue(log.warnings().isEmpty());
        }

        @Test
        void of_shouldUseNoneAndLogAnError_whenSeveralProvidersAreRegistered() {
            GameSettingsProvider other = mock(GameSettingsProvider.class);

            GameSettingsSource source = GameSettingsSource.of(Arrays.asList(provider, other), log);

            assertFalse(source.present());
            assertNull(source.snapshot(BUILT_AT));
            assertEquals(1, log.errors().size());
            verifyNoInteractions(provider, other);
        }
    }

    @Nested
    class Snapshot {

        @Test
        void snapshot_shouldPassTheBuildMomentToTheProvider() {
            GameSettings built = GameSettings.builder().updatedAt(BUILT_AT).build();
            when(provider.gameSettings(BUILT_AT)).thenReturn(built);
            GameSettingsSource source = GameSettingsSource.of(Collections.singletonList(provider), log);

            assertSame(built, source.snapshot(BUILT_AT));
            verify(provider).gameSettings(BUILT_AT);
        }

        @Test
        void snapshot_shouldStampTheBuildMoment_whenTheProviderSetAnotherUpdatedAt() {
            when(provider.gameSettings(BUILT_AT))
                    .thenReturn(GameSettings.builder()
                            .updatedAt(BUILT_AT.minusSeconds(3600))
                            .build());
            GameSettingsSource source = GameSettingsSource.of(Collections.singletonList(provider), log);

            GameSettings actual = source.snapshot(BUILT_AT);

            assertEquals(BUILT_AT, actual.getUpdatedAt());
        }

        @Test
        void snapshot_shouldReturnNullAndWarnOncePerStreak_whenTheProviderKeepsThrowing() {
            when(provider.gameSettings(any(Instant.class))).thenThrow(new IllegalStateException("config not loaded"));
            GameSettingsSource source = GameSettingsSource.of(Collections.singletonList(provider), log);

            assertNull(source.snapshot(BUILT_AT));
            assertNull(source.snapshot(BUILT_AT.plusSeconds(300)));

            assertEquals(1, log.warnings().size());
        }

        @Test
        void snapshot_shouldWarnAgain_whenANewStreakStartsAfterASuccess() {
            when(provider.gameSettings(any(Instant.class)))
                    .thenThrow(new IllegalStateException("first"))
                    .thenReturn(GameSettings.builder().updatedAt(BUILT_AT).build())
                    .thenThrow(new IllegalStateException("second"));
            GameSettingsSource source = GameSettingsSource.of(Collections.singletonList(provider), log);

            source.snapshot(BUILT_AT);
            source.snapshot(BUILT_AT);
            source.snapshot(BUILT_AT);

            assertEquals(2, log.warnings().size());
        }

        @Test
        void snapshot_shouldReturnNullWithoutLogging_whenTheHostIsNotReady() {
            when(provider.gameSettings(BUILT_AT)).thenReturn(null);
            GameSettingsSource source = GameSettingsSource.of(Collections.singletonList(provider), log);

            assertNull(source.snapshot(BUILT_AT));
            assertTrue(log.warnings().isEmpty());
        }
    }
}
