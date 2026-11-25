package app.l2nx.gs.adapter.api.kafka.commands.offline.fishing;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import app.l2nx.gs.adapter.api.kafka.commands.mail.model.WellKnownAcceptMailReasons;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class OfflineFishingCommandsTest {

    private static final Instant DEADLINE = Instant.parse("2026-10-15T12:00:00Z");
    private static final UUID KEY = UUID.fromString("5b1f3c2e-0000-4000-8000-000000000001");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    private static StartOfflineFishingCommand.Builder start() {
        return StartOfflineFishingCommand.builder()
                .characterId(5)
                .durationMs(3_600_000L)
                .rodItemId(100)
                .baitItemId(200)
                .fishingShotItemId(300)
                .ownerVerified(true)
                .deadline(DEADLINE)
                .idempotencyKey(KEY)
                .initiator(TestInitiators.USER);
    }

    private static CheckOfflineFishingCommand.Builder check() {
        return CheckOfflineFishingCommand.builder()
                .characterId(5)
                .durationMs(3_600_000L)
                .rodItemId(100)
                .baitItemId(200)
                .fishingShotItemId(300)
                .ownerVerified(true)
                .deadline(DEADLINE)
                .initiator(TestInitiators.USER);
    }

    private static StopOfflineFishingCommand.Builder stop() {
        return StopOfflineFishingCommand.builder()
                .characterId(5)
                .ownerVerified(true)
                .deadline(DEADLINE)
                .idempotencyKey(KEY)
                .initiator(TestInitiators.USER);
    }

    private static StartOfflineFishingResult.Builder startResult() {
        return StartOfflineFishingResult.builder()
                .sessionId(KEY)
                .spotPool("lake")
                .spotId(3)
                .effectiveDurationMs(1_800_000L);
    }

    static Stream<Arguments> missingRequired() {
        return Stream.of(
                missing("start initiator", () -> start().initiator(null).build()),
                missing("start deadline", () -> start().deadline(null).build()),
                missing(
                        "start idempotencyKey",
                        () -> start().idempotencyKey(null).build()),
                missing(
                        "start result sessionId",
                        () -> startResult().sessionId(null).build()),
                missing(
                        "start result spotPool",
                        () -> startResult().spotPool(null).build()),
                missing("check initiator", () -> check().initiator(null).build()),
                missing("check deadline", () -> check().deadline(null).build()),
                missing("stop initiator", () -> stop().initiator(null).build()),
                missing("stop deadline", () -> stop().deadline(null).build()),
                missing("stop idempotencyKey", () -> stop().idempotencyKey(null).build()));
    }

    private static Arguments missing(String name, Executable build) {
        return Arguments.of(name, build);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingRequired")
    void build_shouldThrow_whenARequiredFieldIsNull(String name, Executable build) {
        assertThrows(NullPointerException.class, build);
    }

    @Nested
    class Start {

        @ParameterizedTest(name = "durationMs={0}")
        @ValueSource(longs = {0L, -1L})
        void build_shouldRejectNonPositiveDuration(long durationMs) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> start().durationMs(durationMs).build());
        }

        @Test
        void equals_shouldFollowTheContract() {
            EqualsVerifier.forClass(StartOfflineFishingCommand.class).verify();
        }

        @Test
        void gson_shouldRoundtripWithoutFishingShot() {
            StartOfflineFishingCommand original =
                    start().fishingShotItemId(null).build();

            StartOfflineFishingCommand actual = GSON.fromJson(GSON.toJson(original), StartOfflineFishingCommand.class);

            assertEquals(original, actual);
            assertNull(actual.getFishingShotItemId());
        }

        @Test
        void gson_shouldRoundtripAllFields() {
            StartOfflineFishingCommand cmd = start().build();

            assertEquals(5, cmd.getCharacterId());
            assertEquals(TestInitiators.USER, cmd.getInitiator());
            assertEquals(3_600_000L, cmd.getDurationMs());
            assertEquals(100, cmd.getRodItemId());
            assertEquals(200, cmd.getBaitItemId());
            assertEquals(Integer.valueOf(300), cmd.getFishingShotItemId());
            assertTrue(cmd.isOwnerVerified());
            assertEquals(DEADLINE, cmd.getDeadline());
            assertEquals(KEY, cmd.getIdempotencyKey());
            assertEquals(cmd, cmd.toBuilder().build());
            assertEquals(cmd, GSON.fromJson(GSON.toJson(cmd), StartOfflineFishingCommand.class));
        }

        @Test
        void result_shouldRoundtripAllFields() {
            StartOfflineFishingResult result = startResult().build();

            assertEquals(result, result.toBuilder().build());
            assertEquals(result, GSON.fromJson(GSON.toJson(result), StartOfflineFishingResult.class));
            assertEquals(KEY, result.getSessionId());
            assertEquals("lake", result.getSpotPool());
            assertEquals(3, result.getSpotId());
            assertEquals(1_800_000L, result.getEffectiveDurationMs());
        }
    }

    @Nested
    class Check {

        @ParameterizedTest(name = "durationMs={0}")
        @ValueSource(longs = {0L, -1L})
        void build_shouldRejectNonPositiveDuration(long durationMs) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> check().durationMs(durationMs).build());
        }

        @Test
        void equals_shouldFollowTheContract() {
            EqualsVerifier.forClass(CheckOfflineFishingCommand.class).verify();
        }

        @Test
        void gson_shouldRoundtripAllFields() {
            CheckOfflineFishingCommand cmd = check().build();

            assertEquals(5, cmd.getCharacterId());
            assertEquals(Long.valueOf(3_600_000L), cmd.getDurationMs());
            assertEquals(Integer.valueOf(100), cmd.getRodItemId());
            assertEquals(Integer.valueOf(200), cmd.getBaitItemId());
            assertEquals(Integer.valueOf(300), cmd.getFishingShotItemId());
            assertTrue(cmd.isOwnerVerified());
            assertEquals(DEADLINE, cmd.getDeadline());
            assertEquals(TestInitiators.USER, cmd.getInitiator());
            assertEquals(cmd, cmd.toBuilder().build());
            assertEquals(cmd, GSON.fromJson(GSON.toJson(cmd), CheckOfflineFishingCommand.class));
        }

        @Test
        void gson_shouldOmitAbsentChoicesAndReadThemBackAsNull() {
            CheckOfflineFishingCommand original = check().durationMs(null)
                    .rodItemId(null)
                    .baitItemId(null)
                    .fishingShotItemId(null)
                    .build();

            assertNull(original.getDurationMs());
            assertNull(original.getRodItemId());
            assertNull(original.getBaitItemId());
            assertNull(original.getFishingShotItemId());

            String json = GSON.toJson(original);

            assertFalse(json.contains("durationMs"), json);
            assertFalse(json.contains("idempotencyKey"), json);
            assertEquals(original, GSON.fromJson(json, CheckOfflineFishingCommand.class));
        }
    }

    @Nested
    class Stop {

        @Test
        void build_shouldRoundtripFields() {
            StopOfflineFishingCommand cmd = stop().build();

            assertEquals(5, cmd.getCharacterId());
            assertEquals(TestInitiators.USER, cmd.getInitiator());
            assertEquals(KEY, cmd.getIdempotencyKey());
            assertEquals(cmd, cmd.toBuilder().build());
            assertEquals(cmd, GSON.fromJson(GSON.toJson(cmd), StopOfflineFishingCommand.class));
            assertTrue(cmd.isOwnerVerified());
            assertEquals(DEADLINE, cmd.getDeadline());
        }

        @Test
        void result_shouldRoundtrip_whenStoppedOrNothingRan() {
            StopOfflineFishingResult nothing =
                    StopOfflineFishingResult.builder().build();
            StopOfflineFishingResult stopped = new StopOfflineFishingResult(KEY, true);

            assertNull(nothing.getSessionId());
            assertFalse(nothing.isStopped());
            assertEquals(nothing, GSON.fromJson(GSON.toJson(nothing), StopOfflineFishingResult.class));
            assertEquals(stopped, stopped.toBuilder().build());
            assertNotEquals(stopped, new StopOfflineFishingResult(KEY, false));
            assertEquals(KEY, stopped.getSessionId());
            assertTrue(stopped.isStopped());
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
                    "NO_FISHING_SHOT",
                    "INVALID_FISHING_SHOT",
                    "FISHING_SHOT_GRADE_MISMATCH",
                    "DAILY_LIMIT",
                    "RESTART_SOON",
                    "INVALID_DURATION",
                    "FEATURE_DISABLED",
                    "NO_FREE_SPOT",
                    "ALREADY_ACTIVE",
                    "NO_FISHING_EXPERTISE",
                    "BAIT_LEVEL_MISMATCH",
                    "INVENTORY_FULL",
                    "OVERWEIGHT"));
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
            assertEquals("blockedByCharacterId", AppSessionRefusal.BLOCKED_BY_CHARACTER_ID);
            assertEquals(WellKnownAcceptMailReasons.EXT_REQUIRED_SLOTS, AppSessionRefusal.REQUIRED_SLOTS);
            assertEquals(WellKnownAcceptMailReasons.EXT_FREE_SLOTS, AppSessionRefusal.FREE_SLOTS);
            assertEquals(WellKnownAcceptMailReasons.EXT_REQUIRED_WEIGHT, AppSessionRefusal.REQUIRED_WEIGHT);
            assertEquals(WellKnownAcceptMailReasons.EXT_FREE_WEIGHT, AppSessionRefusal.FREE_WEIGHT);
        }

        @Test
        void scopes_shouldCoverEveryLimit() {
            assertEquals(5, AlreadyActiveScope.values().length);
            assertNotNull(AlreadyActiveScope.valueOf("MASTER_ACCOUNT"));
            assertNotNull(AlreadyActiveScope.valueOf("ACCOUNT"));
            assertNotNull(AlreadyActiveScope.valueOf("HWID"));
            assertNotNull(AlreadyActiveScope.valueOf("CHARACTER"));
            assertNotNull(AlreadyActiveScope.valueOf("IP"));
        }
    }
}
