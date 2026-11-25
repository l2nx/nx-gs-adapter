package app.l2nx.gs.adapter.api.kafka.commands.captcha;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.captcha.model.CaptchaRoundResult;
import app.l2nx.gs.adapter.api.kafka.commands.captcha.model.WellKnownCaptchaOutcomes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SendCaptchaResultTest {

    private static final Instant STARTED = Instant.parse("2026-10-02T14:03:11Z");
    private static final Instant FINISHED = Instant.parse("2026-10-02T14:05:41Z");

    private static SendCaptchaResult.Builder valid() {
        return SendCaptchaResult.builder()
                .characterId(7L)
                .outcome(WellKnownCaptchaOutcomes.PASSED)
                .startedAt(STARTED)
                .finishedAt(FINISHED)
                .durationMs(150_000L);
    }

    static Stream<Arguments> missingRequired() {
        return Stream.of(
                missing("outcome", () -> valid().outcome(null).build()),
                missing("startedAt", () -> valid().startedAt(null).build()),
                missing("finishedAt", () -> valid().finishedAt(null).build()),
                missing(
                        "round questionType",
                        () -> CaptchaRoundResult.builder().index(1).build()));
    }

    private static Arguments missing(String field, Executable build) {
        return Arguments.of(field, build);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingRequired")
    void constructor_shouldReject_whenRequiredFieldIsMissing(String field, Executable build) {
        assertThrows(IllegalArgumentException.class, build);
    }

    @Test
    void build_shouldDefaultRoundsAndMetadataToEmpty_whenNull() {
        SendCaptchaResult result = valid().build();

        assertTrue(result.getRounds().isEmpty());
        assertTrue(result.getMetadata().isEmpty());
    }

    @Test
    void build_shouldCopyRoundsAndMetadata() {
        List<CaptchaRoundResult> rounds = new ArrayList<>();
        rounds.add(CaptchaRoundResult.builder()
                .index(1)
                .questionType("ODD_COLOR")
                .pickedSlot(3)
                .correct(true)
                .answerTimeMs(6210L)
                .build());
        Map<String, String> metadata = new HashMap<>();
        metadata.put("kick", "true");

        SendCaptchaResult result = valid().rounds(rounds).metadata(metadata).build();
        rounds.clear();
        metadata.clear();

        assertEquals(1, result.getRounds().size());
        assertEquals("true", result.getMetadata().get("kick"));
        assertThrows(
                UnsupportedOperationException.class, () -> result.getRounds().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> result.getMetadata().put("x", "y"));
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(SendCaptchaResult.class)
                .withNonnullFields("characterId", "outcome", "startedAt", "finishedAt", "rounds", "metadata")
                .verify();
    }
}
