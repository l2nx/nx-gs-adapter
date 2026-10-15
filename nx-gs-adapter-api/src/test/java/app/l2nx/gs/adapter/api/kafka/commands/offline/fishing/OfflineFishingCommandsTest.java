package app.l2nx.gs.adapter.api.kafka.commands.offline.fishing;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.CommandProblem;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import app.l2nx.gs.adapter.api.kafka.commands.session.AppSessionRefusal;
import app.l2nx.gs.adapter.api.kafka.commands.session.model.AlreadyActiveScope;
import app.l2nx.gs.adapter.api.kafka.commands.session.model.AppSessionRefusalReason;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OfflineFishingCommandsTest {

    private static final Instant DEADLINE = Instant.parse("2026-10-15T12:00:00Z");
    private static final UUID KEY = UUID.fromString("5b1f3c2e-0000-4000-8000-000000000001");
    private static final UUID MASTER = UUID.fromString("5b1f3c2e-0000-4000-8000-000000000002");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    private static StartOfflineFishingCommand.Builder start() {
        return StartOfflineFishingCommand.builder()
                .characterId(5)
                .masterAccountId(MASTER)
                .initiatorIp("1.2.3.4")
                .durationMs(3_600_000L)
                .rodItemId(100)
                .baitItemId(200)
                .ownerVerified(true)
                .deadline(DEADLINE)
                .idempotencyKey(KEY);
    }

    private static StopOfflineFishingCommand.Builder stop() {
        return StopOfflineFishingCommand.builder()
                .characterId(5)
                .masterAccountId(MASTER)
                .initiatorIp("1.2.3.4")
                .ownerVerified(true)
                .deadline(DEADLINE)
                .idempotencyKey(KEY);
    }

    @Nested
    class Start {

        @Test
        void build_shouldRoundtripFields() {
            StartOfflineFishingCommand cmd = start().build();

            assertEquals(5, cmd.getCharacterId());
            assertEquals(MASTER, cmd.getMasterAccountId());
            assertEquals("1.2.3.4", cmd.getInitiatorIp());
            assertEquals(3_600_000L, cmd.getDurationMs());
            assertEquals(100, cmd.getRodItemId());
            assertEquals(200, cmd.getBaitItemId());
            assertTrue(cmd.isOwnerVerified());
            assertEquals(DEADLINE, cmd.getDeadline());
            assertEquals(KEY, cmd.getIdempotencyKey());
            assertEquals(cmd, cmd.toBuilder().build());
        }

        @ParameterizedTest(name = "durationMs={0}")
        @ValueSource(longs = {0L, -1L})
        void build_shouldRejectNonPositiveDuration(long durationMs) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> start().durationMs(durationMs).build());
        }

        @Test
        void build_shouldRejectNullMasterAccountDeadlineAndKey() {
            assertThrows(
                    NullPointerException.class,
                    () -> start().masterAccountId(null).build());
            assertThrows(
                    NullPointerException.class, () -> start().deadline(null).build());
            assertThrows(
                    NullPointerException.class,
                    () -> start().idempotencyKey(null).build());
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"", "  "})
        void build_shouldNormalizeBlankInitiatorIpToNull(String blank) {
            assertNull(start().initiatorIp(blank).build().getInitiatorIp());
        }

        @Test
        void equals_shouldDistinguishRodAndBait() {
            assertNotEquals(start().build(), start().rodItemId(101).build());
            assertNotEquals(start().build(), start().baitItemId(201).build());
        }

        @Test
        void command_shouldBeOwnerVerified() {
            assertTrue(start().ownerVerified(true).build() instanceof OwnerVerified);
        }

        @Test
        void gson_shouldRoundtrip() {
            StartOfflineFishingCommand original = start().build();

            assertEquals(original, GSON.fromJson(GSON.toJson(original), StartOfflineFishingCommand.class));
        }

        @Test
        void result_shouldRoundtripAndRejectNulls() {
            StartOfflineFishingResult result = StartOfflineFishingResult.builder()
                    .sessionId(KEY)
                    .spotPool("lake")
                    .spotId(3)
                    .effectiveDurationMs(1_800_000L)
                    .build();

            assertEquals(result, result.toBuilder().build());
            assertEquals(result, GSON.fromJson(GSON.toJson(result), StartOfflineFishingResult.class));
            assertThrows(
                    NullPointerException.class,
                    () -> result.toBuilder().sessionId(null).build());
            assertThrows(
                    NullPointerException.class,
                    () -> result.toBuilder().spotPool(null).build());
        }
    }

    @Nested
    class Stop {

        @Test
        void build_shouldRoundtripFields() {
            StopOfflineFishingCommand cmd = stop().build();

            assertEquals(5, cmd.getCharacterId());
            assertEquals(MASTER, cmd.getMasterAccountId());
            assertEquals(KEY, cmd.getIdempotencyKey());
            assertEquals(cmd, cmd.toBuilder().build());
            assertEquals(cmd, GSON.fromJson(GSON.toJson(cmd), StopOfflineFishingCommand.class));
        }

        @Test
        void build_shouldRejectNullMasterAccountDeadlineAndKey() {
            assertThrows(
                    NullPointerException.class,
                    () -> stop().masterAccountId(null).build());
            assertThrows(NullPointerException.class, () -> stop().deadline(null).build());
            assertThrows(
                    NullPointerException.class,
                    () -> stop().idempotencyKey(null).build());
        }

        @Test
        void result_shouldAllowNothingRan() {
            StopOfflineFishingResult nothing =
                    StopOfflineFishingResult.builder().build();

            assertNull(nothing.getSessionId());
            assertFalse(nothing.isStopped());
            assertEquals(nothing, GSON.fromJson(GSON.toJson(nothing), StopOfflineFishingResult.class));
        }

        @Test
        void result_shouldRoundtripStopped() {
            StopOfflineFishingResult stopped = new StopOfflineFishingResult(KEY, true);

            assertEquals(stopped, stopped.toBuilder().build());
            assertNotEquals(stopped, new StopOfflineFishingResult(KEY, false));
        }
    }

    @Nested
    class Refusal {

        @Test
        void reasons_shouldMatchTheContractedSet() {
            Set<String> expected = new HashSet<String>(Arrays.asList(
                    "RECENTLY_LOGGED_OUT",
                    "COOLDOWN",
                    "CHARACTER_ONLINE",
                    "DEAD",
                    "KARMA",
                    "CHARACTER_JAILED",
                    "CHARACTER_BANNED",
                    "UNSAFE_LOGOUT_LOCATION",
                    "IN_PRIVATE_STORE",
                    "NO_ROD",
                    "NO_BAIT",
                    "INVALID_ROD",
                    "INVALID_BAIT",
                    "DAILY_LIMIT",
                    "RESTART_SOON",
                    "INVALID_DURATION",
                    "FEATURE_DISABLED",
                    "NO_FREE_SPOT",
                    "ALREADY_ACTIVE"));
            Set<String> actual = new HashSet<String>();
            for (AppSessionRefusalReason reason : AppSessionRefusalReason.values()) {
                actual.add(reason.name());
            }

            assertEquals(expected, actual);
        }

        @Test
        void extensionKeys_shouldBeTheWireNames() {
            assertEquals("reason", AppSessionRefusal.REASON);
            assertEquals("retryAt", AppSessionRefusal.RETRY_AT);
            assertEquals("scope", AppSessionRefusal.SCOPE);
        }

        @Test
        void scopes_shouldCoverEveryLimit() {
            assertEquals(4, AlreadyActiveScope.values().length);
            assertNotNull(AlreadyActiveScope.valueOf("MASTER_ACCOUNT"));
            assertNotNull(AlreadyActiveScope.valueOf("ACCOUNT"));
            assertNotNull(AlreadyActiveScope.valueOf("HWID"));
            assertNotNull(AlreadyActiveScope.valueOf("CHARACTER"));
        }

        @Test
        void problem_shouldCarryReasonRetryAtAndScopeAsStrings() {
            Map<String, Object> extensions = new LinkedHashMap<String, Object>();
            extensions.put(AppSessionRefusal.REASON, AppSessionRefusalReason.COOLDOWN.name());
            extensions.put(
                    AppSessionRefusal.RETRY_AT,
                    Instant.parse("2026-10-15T12:02:00Z").toString());
            extensions.put(AppSessionRefusal.SCOPE, AlreadyActiveScope.HWID.name());

            CommandProblem problem = new CommandProblem("Cannot start fishing", null, extensions);

            assertEquals("COOLDOWN", problem.getExtensions().get("reason"));
            assertEquals("2026-10-15T12:02:00Z", problem.getExtensions().get("retryAt"));
            assertEquals("HWID", problem.getExtensions().get("scope"));
        }
    }
}
