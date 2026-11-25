package app.l2nx.gs.adapter.api.kafka.commands.sync;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import java.util.*;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ResyncRowsCommandTest {

    private static final UUID RESYNC_ID = UUID.fromString("018f0000-0000-7000-8000-000000000002");

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        ResyncRowsCommand cmd = ResyncRowsCommand.builder()
                .resyncId(RESYNC_ID)
                .entityName("character")
                .pks(Arrays.asList(1L, 2L, 3L))
                .cascade(true)
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(RESYNC_ID, cmd.getResyncId());
        assertEquals("character", cmd.getEntityName());
        assertEquals(Arrays.asList(1L, 2L, 3L), cmd.getPks());
        assertTrue(cmd.isCascade());
        assertEquals(cmd, cmd.toBuilder().build());
        assertEquals(TestInitiators.USER, cmd.getInitiator());
    }

    @Test
    void builder_shouldDefaultCascadeToFalse() {
        ResyncRowsCommand cmd = ResyncRowsCommand.builder()
                .resyncId(RESYNC_ID)
                .entityName("character")
                .pks(Collections.singletonList(1L))
                .initiator(TestInitiators.USER)
                .build();

        assertFalse(cmd.isCascade());
    }

    static Stream<Arguments> invalidCommands() {
        List<Long> pk = Collections.singletonList(1L);
        List<Long> oversized = new ArrayList<Long>(ResyncRowsCommand.MAX_PKS + 1);
        for (long i = 0; i <= ResyncRowsCommand.MAX_PKS; i++) {
            oversized.add(i);
        }
        return Stream.of(
                Arguments.of("resyncId null", null, "character", pk, "resyncId"),
                Arguments.of("entityName null", RESYNC_ID, null, pk, "entityName"),
                Arguments.of("pks null", RESYNC_ID, "character", null, "pks"),
                Arguments.of("pks empty", RESYNC_ID, "character", Collections.<Long>emptyList(), "pks"),
                Arguments.of(
                        "pks over the cap",
                        RESYNC_ID,
                        "character",
                        oversized,
                        String.valueOf(ResyncRowsCommand.MAX_PKS)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCommands")
    void constructor_shouldReject_whenCommandInvalid(
            String name, UUID resyncId, String entityName, List<Long> pks, String messagePart) {
        IllegalArgumentException actual = assertThrows(
                IllegalArgumentException.class,
                () -> new ResyncRowsCommand(resyncId, entityName, pks, false, TestInitiators.USER));
        assertTrue(actual.getMessage().contains(messagePart), actual.getMessage());
    }

    @Test
    void constructor_shouldAcceptExactlyMaxPks() {
        List<Long> atCap = new ArrayList<Long>(ResyncRowsCommand.MAX_PKS);
        for (long i = 0; i < ResyncRowsCommand.MAX_PKS; i++) {
            atCap.add(i);
        }

        assertEquals(
                ResyncRowsCommand.MAX_PKS,
                new ResyncRowsCommand(RESYNC_ID, "character", atCap, false, TestInitiators.USER)
                        .getPks()
                        .size());
    }

    @Test
    void getPks_shouldBeUnmodifiable() {
        ResyncRowsCommand cmd = new ResyncRowsCommand(
                RESYNC_ID, "character", Collections.singletonList(1L), false, TestInitiators.USER);

        assertThrows(UnsupportedOperationException.class, () -> cmd.getPks().add(2L));
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(ResyncRowsCommand.class)
                .withNonnullFields("resyncId", "entityName", "pks")
                .verify();
    }
}
