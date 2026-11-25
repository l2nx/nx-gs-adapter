package app.l2nx.gs.adapter.api.kafka.commands.character.revive;

import static org.junit.jupiter.api.Assertions.*;

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
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
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

    private static ReviveCharacterResult.Builder result() {
        return ReviveCharacterResult.builder()
                .restartPoint("TOWN")
                .x(82_698)
                .y(148_638)
                .z(-3_473)
                .clientDisconnected(true);
    }

    static Stream<Arguments> missingRequired() {
        return Stream.of(
                missing("command initiator", () -> command().initiator(null).build()),
                missing(
                        "command restartPoint",
                        () -> command().restartPoint((String) null).build()),
                missing("command deadline", () -> command().deadline(null).build()),
                missing(
                        "command idempotencyKey",
                        () -> command().idempotencyKey(null).build()),
                missing("result restartPoint", () -> result().restartPoint(null).build()));
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
    class Command {

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
        void equals_shouldFollowTheContract() {
            EqualsVerifier.forClass(ReviveCharacterCommand.class).verify();
        }

        @Test
        void gson_shouldRoundtripAllFields() {
            ReviveCharacterCommand expected = command().build();

            assertEquals(5, expected.getCharacterId());
            assertEquals(TestInitiators.USER, expected.getInitiator());
            assertEquals("TOWN", expected.getRestartPoint());
            assertTrue(expected.isDisconnectClient());
            assertTrue(expected.isOwnerVerified());
            assertEquals(DEADLINE, expected.getDeadline());
            assertEquals(KEY, expected.getIdempotencyKey());
            assertEquals(expected, expected.toBuilder().build());
            assertEquals(expected.hashCode(), expected.toBuilder().build().hashCode());
            assertEquals(expected, GSON.fromJson(GSON.toJson(expected), ReviveCharacterCommand.class));
        }
    }

    @Nested
    class Result {

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
