package app.l2nx.gs.adapter.api.kafka.commands.offline.fishing;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.CommandProblem;
import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
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

    @Nested
    class Start {

        @Test
        void build_shouldRoundtripFields() {
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
        }

        @ParameterizedTest(name = "durationMs={0}")
        @ValueSource(longs = {0L, -1L})
        void build_shouldRejectNonPositiveDuration(long durationMs) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> start().durationMs(durationMs).build());
        }

        @Test
        void build_shouldRejectNullInitiatorDeadlineAndKey() {
            assertThrows(
                    NullPointerException.class, () -> start().initiator(null).build());
            assertThrows(
                    NullPointerException.class, () -> start().deadline(null).build());
            assertThrows(
                    NullPointerException.class,
                    () -> start().idempotencyKey(null).build());
        }

        @Test
        void equals_shouldDistinguishRodBaitAndFishingShot() {
            assertNotEquals(start().build(), start().rodItemId(101).build());
            assertNotEquals(start().build(), start().baitItemId(201).build());
            assertNotEquals(start().build(), start().fishingShotItemId(null).build());
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
    class Check {

        @Test
        void build_shouldRoundtripFields() {
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
        }

        @Test
        void build_shouldAcceptEveryPlayerChoiceAbsent() {
            CheckOfflineFishingCommand cmd = check().durationMs(null)
                    .rodItemId(null)
                    .baitItemId(null)
                    .fishingShotItemId(null)
                    .build();

            assertNull(cmd.getDurationMs());
            assertNull(cmd.getRodItemId());
            assertNull(cmd.getBaitItemId());
            assertNull(cmd.getFishingShotItemId());
        }

        @ParameterizedTest(name = "durationMs={0}")
        @ValueSource(longs = {0L, -1L})
        void build_shouldRejectNonPositiveDuration(long durationMs) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> check().durationMs(durationMs).build());
        }

        @Test
        void build_shouldRejectNullInitiatorAndDeadline() {
            assertThrows(
                    NullPointerException.class, () -> check().initiator(null).build());
            assertThrows(
                    NullPointerException.class, () -> check().deadline(null).build());
        }

        @Test
        void equals_shouldDistinguishEveryChoice() {
            assertNotEquals(check().build(), check().durationMs(null).build());
            assertNotEquals(check().build(), check().rodItemId(101).build());
            assertNotEquals(check().build(), check().baitItemId(201).build());
            assertNotEquals(check().build(), check().fishingShotItemId(301).build());
        }

        @Test
        void command_shouldBeOwnerVerified() {
            assertTrue(check().build() instanceof OwnerVerified);
        }

        @Test
        void gson_shouldRoundtrip() {
            CheckOfflineFishingCommand original = check().build();

            assertEquals(original, GSON.fromJson(GSON.toJson(original), CheckOfflineFishingCommand.class));
        }

        @Test
        void gson_shouldOmitAbsentChoicesAndReadThemBackAsNull() {
            CheckOfflineFishingCommand original = check().durationMs(null)
                    .rodItemId(null)
                    .baitItemId(null)
                    .fishingShotItemId(null)
                    .build();

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
        }

        @Test
        void build_shouldRejectNullInitiatorDeadlineAndKey() {
            assertThrows(
                    NullPointerException.class, () -> stop().initiator(null).build());
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
                    "NO_FISHING_SHOT",
                    "INVALID_FISHING_SHOT",
                    "FISHING_SHOT_GRADE_MISMATCH",
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
            assertEquals(5, AlreadyActiveScope.values().length);
            assertNotNull(AlreadyActiveScope.valueOf("MASTER_ACCOUNT"));
            assertNotNull(AlreadyActiveScope.valueOf("ACCOUNT"));
            assertNotNull(AlreadyActiveScope.valueOf("HWID"));
            assertNotNull(AlreadyActiveScope.valueOf("CHARACTER"));
            assertNotNull(AlreadyActiveScope.valueOf("IP"));
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
