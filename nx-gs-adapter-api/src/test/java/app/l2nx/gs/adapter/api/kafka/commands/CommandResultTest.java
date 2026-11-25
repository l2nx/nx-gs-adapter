package app.l2nx.gs.adapter.api.kafka.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CommandResultTest {

    @Test
    void ok_shouldHaveStatusOkNoPayloadNoProblem() {
        CommandResult<Void> r = CommandResult.ok();

        assertTrue(r.isOk());
        assertEquals(CommandStatus.OK, r.getStatus());
        assertEquals(CommandStatus.Tier.OK, r.getTier());
        assertNull(r.getPayload());
        assertNull(r.getProblem());
    }

    @Test
    void error_shouldFlipOkAndCarryStatusAndProblemTitle() {
        CommandResult<Void> r = CommandResult.error(CommandStatus.NOT_FOUND, "Character not found");

        assertFalse(r.isOk());
        assertEquals(CommandStatus.NOT_FOUND, r.getStatus());
        assertEquals(CommandStatus.Tier.CLIENT_ERROR, r.getTier());
        assertEquals("Character not found", r.getProblem().getTitle());
        assertNull(r.getPayload());
    }

    @Test
    void error_withPrebuiltProblem_shouldExposeProblem() {
        CommandProblem problem = CommandProblem.builder()
                .title("Mail attachment validation failed")
                .detail("3 of 5 items rejected")
                .extension("first.reason", "unknown template id")
                .build();
        CommandResult<Void> r = CommandResult.error(CommandStatus.VALIDATION_FAILED, problem);

        assertFalse(r.isOk());
        assertEquals(CommandStatus.VALIDATION_FAILED, r.getStatus());
        assertSame(problem, r.getProblem());
    }

    static Stream<Arguments> factories() {
        CommandProblem plain = CommandProblem.of("title");
        CommandProblem extended = CommandProblem.of("title", "key", 5L);
        return Stream.of(
                Arguments.of("ok()", CommandResult.ok(), CommandStatus.OK, null, null),
                Arguments.of("ok(payload)", CommandResult.ok("hello"), CommandStatus.OK, "hello", null),
                Arguments.of(
                        "error(status, problem)",
                        CommandResult.error(CommandStatus.FORBIDDEN, extended),
                        CommandStatus.FORBIDDEN,
                        null,
                        extended),
                Arguments.of(
                        "error(status, title)",
                        CommandResult.error(CommandStatus.UNAVAILABLE, "title"),
                        CommandStatus.UNAVAILABLE,
                        null,
                        plain),
                Arguments.of(
                        "error(status, title, extension)",
                        CommandResult.error(CommandStatus.RATE_LIMITED, "title", "key", 5L),
                        CommandStatus.RATE_LIMITED,
                        null,
                        extended),
                Arguments.of("notFound(title)", CommandResult.notFound("title"), CommandStatus.NOT_FOUND, null, plain),
                Arguments.of(
                        "notFound(title, extension)",
                        CommandResult.notFound("title", "key", 5L),
                        CommandStatus.NOT_FOUND,
                        null,
                        extended),
                Arguments.of(
                        "invalidState(title)",
                        CommandResult.invalidState("title"),
                        CommandStatus.INVALID_STATE,
                        null,
                        plain),
                Arguments.of(
                        "invalidState(title, extension)",
                        CommandResult.invalidState("title", "key", 5L),
                        CommandStatus.INVALID_STATE,
                        null,
                        extended),
                Arguments.of(
                        "forbidden(title)", CommandResult.forbidden("title"), CommandStatus.FORBIDDEN, null, plain),
                Arguments.of(
                        "validationFailed(title)",
                        CommandResult.validationFailed("title"),
                        CommandStatus.VALIDATION_FAILED,
                        null,
                        plain),
                Arguments.of(
                        "validationFailed(title, field)",
                        CommandResult.validationFailed("title", "count"),
                        CommandStatus.VALIDATION_FAILED,
                        null,
                        CommandProblem.of("title", "field", "count")),
                Arguments.of(
                        "rateLimited(title)",
                        CommandResult.rateLimited("title"),
                        CommandStatus.RATE_LIMITED,
                        null,
                        plain),
                Arguments.of(
                        "commandExpired(title)",
                        CommandResult.commandExpired("title"),
                        CommandStatus.COMMAND_EXPIRED,
                        null,
                        plain),
                Arguments.of(
                        "unavailable(title)",
                        CommandResult.unavailable("title"),
                        CommandStatus.UNAVAILABLE,
                        null,
                        plain),
                Arguments.of(
                        "internalError(title)",
                        CommandResult.internalError("title"),
                        CommandStatus.INTERNAL_ERROR,
                        null,
                        plain));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("factories")
    void factory_shouldSetStatusPayloadAndProblem(
            String name, CommandResult<?> result, CommandStatus status, String payload, CommandProblem problem) {
        assertEquals(status, result.getStatus());
        assertEquals(status == CommandStatus.OK, result.isOk());
        assertEquals(payload, result.getPayload());
        assertEquals(problem, result.getProblem());
    }

    static Stream<Arguments> invalidArguments() {
        return Stream.of(
                Arguments.of("OK with problem", CommandStatus.OK, null, CommandProblem.of("oops")),
                Arguments.of("non-OK without problem", CommandStatus.NOT_FOUND, null, null),
                Arguments.of("non-OK with payload", CommandStatus.NOT_FOUND, "payload", CommandProblem.of("nope")),
                Arguments.of("null status", null, null, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidArguments")
    void constructor_shouldRejectInvalidArguments(
            String name, CommandStatus status, String payload, CommandProblem problem) {
        assertThrows(IllegalArgumentException.class, () -> new CommandResult<String>(status, payload, problem));
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(CommandResult.class).verify();
    }
}
