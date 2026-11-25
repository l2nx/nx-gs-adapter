package app.l2nx.gs.adapter.api.kafka.commands.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.kafka.commands.session.model.AlreadyActiveScope;
import app.l2nx.gs.adapter.api.kafka.commands.session.model.AppSessionRefusalReason;
import app.l2nx.gs.adapter.api.kafka.sync.runtime.character.activity.usage.model.ActivityUsage;
import app.l2nx.gs.adapter.api.kafka.sync.runtime.character.activity.usage.model.UsageStep;
import app.l2nx.gs.adapter.api.kafka.sync.runtime.character.activity.usage.model.UsageUnit;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.time.Instant;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AppSessionCheckResultTest {

    private static final Instant RETRY_AT = Instant.parse("2026-10-15T12:15:00Z");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    private static ActivityUsage usage() {
        return ActivityUsage.builder()
                .type(ActivityType.FISHING)
                .unit(UsageUnit.MS)
                .used(60_000L)
                .limit(32_400_000L)
                .remaining(32_340_000L)
                .resetSchedule("DTSTART:20260101T030000Z\nRRULE:FREQ=DAILY")
                .steps(Collections.singletonList(new UsageStep(14_400_000L, 0.5)))
                .updatedAt(RETRY_AT)
                .build();
    }

    private static AppSessionCheckResult.Builder blocked(AlreadyActiveScope scope, int blockedByCharacterId) {
        return AppSessionCheckResult.builder()
                .reason(AppSessionRefusalReason.ALREADY_ACTIVE)
                .scope(scope)
                .blockedByCharacterId(blockedByCharacterId);
    }

    static Stream<Arguments> validResults() {
        return Stream.of(
                Arguments.of("allowed with duration", AppSessionCheckResult.allowed(1_800_000L)),
                Arguments.of("allowed without duration", AppSessionCheckResult.allowed(null)),
                Arguments.of(
                        "allowed with the usage row",
                        AppSessionCheckResult.allowed(1_800_000L).toBuilder()
                                .usage(usage())
                                .build()),
                Arguments.of(
                        "refused with the usage row",
                        AppSessionCheckResult.refused(AppSessionRefusalReason.NO_BAIT).toBuilder()
                                .usage(usage())
                                .build()),
                Arguments.of("refused", AppSessionCheckResult.refused(AppSessionRefusalReason.NO_FISHING_SHOT)),
                Arguments.of(
                        "already active",
                        AppSessionCheckResult.builder()
                                .reason(AppSessionRefusalReason.ALREADY_ACTIVE)
                                .scope(AlreadyActiveScope.IP)
                                .build()),
                Arguments.of(
                        "already active with the blocking character",
                        blocked(AlreadyActiveScope.HWID, 268_500_001).build()),
                Arguments.of(
                        "cooldown",
                        AppSessionCheckResult.builder()
                                .reason(AppSessionRefusalReason.COOLDOWN)
                                .retryAt(RETRY_AT)
                                .build()),
                Arguments.of(
                        "daily limit with its reset",
                        AppSessionCheckResult.builder()
                                .reason(AppSessionRefusalReason.DAILY_LIMIT)
                                .retryAt(RETRY_AT)
                                .build()),
                Arguments.of(
                        "daily limit without a known reset",
                        AppSessionCheckResult.refused(AppSessionRefusalReason.DAILY_LIMIT)),
                Arguments.of(
                        "inventory full with its numbers",
                        AppSessionCheckResult.builder()
                                .reason(AppSessionRefusalReason.INVENTORY_FULL)
                                .requiredSlots(2)
                                .freeSlots(0)
                                .build()),
                Arguments.of(
                        "overweight with its numbers",
                        AppSessionCheckResult.builder()
                                .reason(AppSessionRefusalReason.OVERWEIGHT)
                                .requiredWeight(120L)
                                .freeWeight(0L)
                                .build()));
    }

    static Stream<Arguments> invalidResults() {
        return Stream.of(
                Arguments.of(
                        "allowed with a reason",
                        AppSessionCheckResult.builder().allowed(true).reason(AppSessionRefusalReason.DEAD)),
                Arguments.of("refused without a reason", AppSessionCheckResult.builder()),
                Arguments.of(
                        "already active without scope",
                        AppSessionCheckResult.builder().reason(AppSessionRefusalReason.ALREADY_ACTIVE)),
                Arguments.of(
                        "scope on another reason",
                        AppSessionCheckResult.builder()
                                .reason(AppSessionRefusalReason.DEAD)
                                .scope(AlreadyActiveScope.HWID)),
                Arguments.of(
                        "cooldown without retryAt",
                        AppSessionCheckResult.builder().reason(AppSessionRefusalReason.COOLDOWN)),
                Arguments.of(
                        "recently logged out without retryAt",
                        AppSessionCheckResult.builder().reason(AppSessionRefusalReason.RECENTLY_LOGGED_OUT)),
                Arguments.of(
                        "retryAt on another reason",
                        AppSessionCheckResult.builder()
                                .reason(AppSessionRefusalReason.DEAD)
                                .retryAt(RETRY_AT)),
                Arguments.of(
                        "retryAt on an allowed check",
                        AppSessionCheckResult.builder().allowed(true).retryAt(RETRY_AT)),
                Arguments.of(
                        "duration on a refusal",
                        AppSessionCheckResult.builder()
                                .reason(AppSessionRefusalReason.DEAD)
                                .effectiveDurationMs(1_000L)),
                Arguments.of(
                        "non-positive duration",
                        AppSessionCheckResult.builder().allowed(true).effectiveDurationMs(0L)),
                Arguments.of(
                        "INVENTORY_FULL without numbers",
                        AppSessionCheckResult.builder().reason(AppSessionRefusalReason.INVENTORY_FULL)),
                Arguments.of(
                        "INVENTORY_FULL without freeSlots",
                        AppSessionCheckResult.builder()
                                .reason(AppSessionRefusalReason.INVENTORY_FULL)
                                .requiredSlots(1)),
                Arguments.of(
                        "INVENTORY_FULL with a negative free side",
                        AppSessionCheckResult.builder()
                                .reason(AppSessionRefusalReason.INVENTORY_FULL)
                                .requiredSlots(1)
                                .freeSlots(-1)),
                Arguments.of(
                        "INVENTORY_FULL with nothing required",
                        AppSessionCheckResult.builder()
                                .reason(AppSessionRefusalReason.INVENTORY_FULL)
                                .requiredSlots(0)
                                .freeSlots(0)),
                Arguments.of(
                        "OVERWEIGHT without numbers",
                        AppSessionCheckResult.builder().reason(AppSessionRefusalReason.OVERWEIGHT)),
                Arguments.of(
                        "OVERWEIGHT with a negative free side",
                        AppSessionCheckResult.builder()
                                .reason(AppSessionRefusalReason.OVERWEIGHT)
                                .requiredWeight(10L)
                                .freeWeight(-1L)),
                Arguments.of(
                        "slot numbers with OVERWEIGHT",
                        AppSessionCheckResult.builder()
                                .reason(AppSessionRefusalReason.OVERWEIGHT)
                                .requiredWeight(10L)
                                .freeWeight(0L)
                                .requiredSlots(1)
                                .freeSlots(0)),
                Arguments.of(
                        "numbers on an allowed check",
                        AppSessionCheckResult.builder()
                                .allowed(true)
                                .requiredSlots(1)
                                .freeSlots(0)));
    }

    static Stream<Arguments> invalidBlockingCharacters() {
        return Stream.of(
                Arguments.of("CHARACTER scope", blocked(AlreadyActiveScope.CHARACTER, 268_500_001)),
                Arguments.of("zero", blocked(AlreadyActiveScope.ACCOUNT, 0)),
                Arguments.of("negative", blocked(AlreadyActiveScope.ACCOUNT, -1)),
                Arguments.of(
                        "another reason",
                        AppSessionCheckResult.builder()
                                .reason(AppSessionRefusalReason.DEAD)
                                .blockedByCharacterId(268_500_001)),
                Arguments.of(
                        "allowed check",
                        AppSessionCheckResult.builder().allowed(true).blockedByCharacterId(268_500_001)));
    }

    @Nested
    class Build {

        @Test
        void allowed_shouldCarryOnlyTheDuration() {
            AppSessionCheckResult actual = AppSessionCheckResult.allowed(1_800_000L);

            assertTrue(actual.isAllowed());
            assertNull(actual.getReason());
            assertNull(actual.getScope());
            assertNull(actual.getRetryAt());
            assertEquals(Long.valueOf(1_800_000L), actual.getEffectiveDurationMs());
        }

        @Test
        void refused_shouldCarryOnlyTheReason() {
            AppSessionCheckResult actual = AppSessionCheckResult.refused(AppSessionRefusalReason.NO_ROD);

            assertFalse(actual.isAllowed());
            assertEquals(AppSessionRefusalReason.NO_ROD, actual.getReason());
            assertNull(actual.getEffectiveDurationMs());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.session.AppSessionCheckResultTest#invalidResults")
        void build_shouldReject(String name, AppSessionCheckResult.Builder builder) {
            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(
                "app.l2nx.gs.adapter.api.kafka.commands.session.AppSessionCheckResultTest#invalidBlockingCharacters")
        void build_shouldDropTheBlockingCharacter_whenItIsInvalid(String name, AppSessionCheckResult.Builder builder) {
            assertNull(builder.build().getBlockedByCharacterId());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.session.AppSessionCheckResultTest#validResults")
        void toBuilder_shouldRoundtrip(String name, AppSessionCheckResult expected) {
            assertEquals(expected, expected.toBuilder().build());
        }
    }

    @Nested
    class Wire {

        @Test
        void gson_shouldWriteTheBlockingCharacterUnderTheRefusalKey() {
            AppSessionCheckResult result =
                    blocked(AlreadyActiveScope.HWID, 268_500_001).build();

            assertEquals(
                    268_500_001,
                    GSON.toJsonTree(result)
                            .getAsJsonObject()
                            .get(AppSessionRefusal.BLOCKED_BY_CHARACTER_ID)
                            .getAsInt());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.commands.session.AppSessionCheckResultTest#validResults")
        void gson_shouldRoundtrip(String name, AppSessionCheckResult expected) {
            assertEquals(expected, GSON.fromJson(GSON.toJson(expected), AppSessionCheckResult.class));
        }

        @Test
        void gson_shouldWriteEnumsAndInstantAsWireStrings() {
            AppSessionCheckResult result = AppSessionCheckResult.builder()
                    .reason(AppSessionRefusalReason.COOLDOWN)
                    .retryAt(RETRY_AT)
                    .build();

            assertEquals(
                    "{\"allowed\":false,\"reason\":\"COOLDOWN\",\"retryAt\":\"2026-10-15T12:15:00Z\"}",
                    GSON.toJson(result));
        }
    }
}
