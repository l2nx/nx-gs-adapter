package app.l2nx.gs.adapter.api.kafka.commands.sync;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResyncEntitiesCommandTest {

    private static final UUID RESYNC_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

    @Test
    void builder_shouldRoundtripFields() {
        ResyncEntitiesCommand cmd = ResyncEntitiesCommand.builder()
                .resyncId(RESYNC_ID)
                .entities(Arrays.asList("character", "item"))
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(RESYNC_ID, cmd.getResyncId());
        assertEquals(Arrays.asList("character", "item"), cmd.getEntities());
    }

    @Test
    void constructor_shouldRejectNullResyncId() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> new ResyncEntitiesCommand(null, null, TestInitiators.USER));
        assertTrue(ex.getMessage().contains("resyncId"));
    }

    @Test
    void constructor_shouldNormalizeNullEntitiesToEmpty() {
        ResyncEntitiesCommand cmd = new ResyncEntitiesCommand(RESYNC_ID, null, TestInitiators.USER);

        assertNotNull(cmd.getEntities());
        assertTrue(cmd.getEntities().isEmpty());
    }

    @Test
    void getEntities_shouldBeUnmodifiable() {
        ResyncEntitiesCommand cmd = ResyncEntitiesCommand.builder()
                .resyncId(RESYNC_ID)
                .entities(Arrays.asList("character"))
                .initiator(TestInitiators.USER)
                .build();
        List<String> entities = cmd.getEntities();

        assertThrows(UnsupportedOperationException.class, () -> entities.add("item"));
    }

    @Test
    void toBuilder_shouldRoundtrip() {
        ResyncEntitiesCommand original = ResyncEntitiesCommand.builder()
                .resyncId(RESYNC_ID)
                .entities(Arrays.asList("clan"))
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(original, original.toBuilder().build());
    }

    @Test
    void equals_shouldDistinguishOnEntities() {
        ResyncEntitiesCommand a = new ResyncEntitiesCommand(RESYNC_ID, Arrays.asList("clan"), TestInitiators.USER);
        ResyncEntitiesCommand b = new ResyncEntitiesCommand(RESYNC_ID, Arrays.asList("item"), TestInitiators.USER);

        assertNotEquals(a, b);
    }

    @Test
    void hashCode_shouldMatchEquals() {
        ResyncEntitiesCommand a = new ResyncEntitiesCommand(RESYNC_ID, Arrays.asList("clan"), TestInitiators.USER);
        ResyncEntitiesCommand b = new ResyncEntitiesCommand(RESYNC_ID, Arrays.asList("clan"), TestInitiators.USER);

        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void implementsNxCommandMarker() {
        assertInstanceOf(NxCommand.class, new ResyncEntitiesCommand(RESYNC_ID, null, TestInitiators.USER));
    }
}
