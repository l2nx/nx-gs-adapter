package app.l2nx.gs.adapter.api.kafka.commands.character.revive;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.OwnerVerified;
import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import app.l2nx.gs.adapter.api.kafka.commands.character.revive.model.RestartPoint;
import app.l2nx.gs.adapter.api.kafka.commands.character.revive.model.ReviveRefusalReason;
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
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReviveCharacterCommandTest {

    private static final Instant DEADLINE = Instant.parse("2026-10-25T12:00:00Z");
    private static final UUID KEY = UUID.fromString("5b1f3c2e-0000-4000-8000-000000000011");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    private static ReviveCharacterCommand.Builder command() {
        return ReviveCharacterCommand.builder()
                .characterId(5)
                .restartPoint(RestartPoint.TOWN)
                .disconnectClient(true)
                .ownerVerified(true)
                .deadline(DEADLINE)
                .idempotencyKey(KEY)
                .initiator(TestInitiators.USER);
    }

    static Stream<Arguments> missingRequired() {
        return Stream.of(
                missing("initiator", b -> b.initiator(null)),
                missing("restartPoint", b -> b.restartPoint((String) null)),
                missing("deadline", b -> b.deadline(null)),
                missing("idempotencyKey", b -> b.idempotencyKey(null)));
    }

    private static Arguments missing(String name, UnaryOperator<ReviveCharacterCommand.Builder> change) {
        return Arguments.of(name, change);
    }

    @Nested
    class Command {

        @Test
        void build_shouldRoundtripFields() {
            ReviveCharacterCommand actual = command().build();

            assertEquals(5, actual.getCharacterId());
            assertEquals(TestInitiators.USER, actual.getInitiator());
            assertEquals("TOWN", actual.getRestartPoint());
            assertTrue(actual.isDisconnectClient());
            assertTrue(actual.isOwnerVerified());
            assertEquals(DEADLINE, actual.getDeadline());
            assertEquals(KEY, actual.getIdempotencyKey());
            assertEquals(actual, actual.toBuilder().build());
            assertEquals(actual.hashCode(), actual.toBuilder().build().hashCode());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(
                "app.l2nx.gs.adapter.api.kafka.commands.character.revive.ReviveCharacterCommandTest#missingRequired")
        void build_shouldThrow_whenARequiredFieldIsNull(
                String name, UnaryOperator<ReviveCharacterCommand.Builder> change) {
            ReviveCharacterCommand.Builder builder = change.apply(command());

            assertThrows(NullPointerException.class, builder::build);
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {"", "  "})
        void build_shouldThrow_whenRestartPointIsBlank(String blank) {
            ReviveCharacterCommand.Builder builder = command().restartPoint(blank);

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void build_shouldKeepATokenThisApiDoesNotKnow() {
            assertEquals("CASTLE", command().restartPoint("CASTLE").build().getRestartPoint());
        }

        @Test
        void equals_shouldDistinguishDisconnectClientAndRestartPoint() {
            assertNotEquals(command().build(), command().disconnectClient(false).build());
            assertNotEquals(
                    command().build(), command().restartPoint("CLAN_HALL").build());
        }

        @Test
        void command_shouldBeOwnerVerified() {
            assertTrue(command().build() instanceof OwnerVerified);
        }

        @Test
        void gson_shouldRoundtrip() {
            ReviveCharacterCommand expected = command().build();

            assertEquals(expected, GSON.fromJson(GSON.toJson(expected), ReviveCharacterCommand.class));
        }
    }

    @Nested
    class Result {

        private ReviveCharacterResult.Builder result() {
            return ReviveCharacterResult.builder()
                    .restartPoint("TOWN")
                    .x(82_698)
                    .y(148_638)
                    .z(-3_473)
                    .clientDisconnected(true);
        }

        @Test
        void build_shouldRoundtripFields() {
            ReviveCharacterResult actual = result().build();

            assertEquals("TOWN", actual.getRestartPoint());
            assertEquals(82_698, actual.getX());
            assertEquals(148_638, actual.getY());
            assertEquals(-3_473, actual.getZ());
            assertTrue(actual.isClientDisconnected());
            assertEquals(actual, actual.toBuilder().build());
            assertEquals(actual, GSON.fromJson(GSON.toJson(actual), ReviveCharacterResult.class));
        }

        @Test
        void build_shouldThrow_whenRestartPointIsNull() {
            ReviveCharacterResult.Builder builder = result().restartPoint(null);

            assertThrows(NullPointerException.class, builder::build);
        }
    }

    @Nested
    class FromToken {

        @Test
        void fromToken_shouldReturnTheConstant_whenTheTokenIsKnown() {
            assertEquals(RestartPoint.TOWN, RestartPoint.fromToken("TOWN"));
        }

        @ParameterizedTest(name = "[{0}]")
        @NullSource
        @ValueSource(strings = {"CASTLE", "town", ""})
        void fromToken_shouldReturnNull_whenTheTokenIsUnknown(String token) {
            assertNull(RestartPoint.fromToken(token));
        }
    }

    @Nested
    class Refusal {

        @Test
        void reasons_shouldMatchTheContractedSet() {
            Set<String> expected = new HashSet<String>(Arrays.asList(
                    "FEATURE_DISABLED",
                    "RESTART_POINT_UNSUPPORTED",
                    "RESTART_POINT_UNAVAILABLE",
                    "CHARACTER_BANNED",
                    "CHARACTER_JAILED",
                    "NOT_DEAD",
                    "UNSAFE_LOGOUT_LOCATION"));
            Set<String> actual = new HashSet<String>();
            for (ReviveRefusalReason reason : ReviveRefusalReason.values()) {
                actual.add(reason.name());
            }

            assertEquals(expected, actual);
        }

        @Test
        void reasonKey_shouldBeTheWireName() {
            assertEquals("reason", ReviveRefusal.REASON);
        }
    }
}
