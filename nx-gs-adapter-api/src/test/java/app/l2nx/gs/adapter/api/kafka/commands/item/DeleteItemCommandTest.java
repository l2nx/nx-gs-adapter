package app.l2nx.gs.adapter.api.kafka.commands.item;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.TestInitiators;
import org.junit.jupiter.api.Test;

class DeleteItemCommandTest {

    @Test
    void builder_shouldRoundtripFields() {
        DeleteItemCommand cmd = DeleteItemCommand.builder()
                .charId(42L)
                .itemId(1234567L)
                .count(7L)
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(42L, cmd.getCharId().longValue());
        assertEquals(1234567L, cmd.getItemId().longValue());
        assertEquals(7L, cmd.getCount().longValue());
    }

    @Test
    void builder_shouldDefaultCountToOne_whenNotSet() {
        DeleteItemCommand cmd = DeleteItemCommand.builder()
                .charId(42L)
                .itemId(1234567L)
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(1L, cmd.getCount().longValue());
    }

    @Test
    void constructor_shouldRejectNullCharId() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> new DeleteItemCommand(null, 1L, 1L, TestInitiators.USER));
        assertTrue(ex.getMessage().contains("charId"));
    }

    @Test
    void constructor_shouldRejectNullItemId() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> new DeleteItemCommand(1L, null, 1L, TestInitiators.USER));
        assertTrue(ex.getMessage().contains("itemId"));
    }

    @Test
    void constructor_shouldRejectNullCount() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> new DeleteItemCommand(1L, 1L, null, TestInitiators.USER));
        assertTrue(ex.getMessage().contains("count"));
    }

    @Test
    void constructor_shouldRejectZeroCount() {
        assertThrows(IllegalArgumentException.class, () -> new DeleteItemCommand(1L, 1L, 0L, TestInitiators.USER));
    }

    @Test
    void constructor_shouldRejectNegativeCount() {
        assertThrows(IllegalArgumentException.class, () -> new DeleteItemCommand(1L, 1L, -5L, TestInitiators.USER));
    }

    @Test
    void builder_buildWithoutRequiredField_shouldThrow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DeleteItemCommand.builder()
                        .itemId(1L)
                        .initiator(TestInitiators.USER)
                        .build());
    }

    @Test
    void toBuilder_shouldRoundtrip() {
        DeleteItemCommand original = DeleteItemCommand.builder()
                .charId(42L)
                .itemId(1234567L)
                .count(3L)
                .initiator(TestInitiators.USER)
                .build();

        DeleteItemCommand copy = original.toBuilder().build();

        assertEquals(original, copy);
    }

    @Test
    void equals_shouldDistinguishOnCharId() {
        DeleteItemCommand a = DeleteItemCommand.builder()
                .charId(1L)
                .itemId(99L)
                .initiator(TestInitiators.USER)
                .build();
        DeleteItemCommand b = DeleteItemCommand.builder()
                .charId(2L)
                .itemId(99L)
                .initiator(TestInitiators.USER)
                .build();

        assertNotEquals(a, b);
    }

    @Test
    void equals_shouldDistinguishOnItemId() {
        DeleteItemCommand a = DeleteItemCommand.builder()
                .charId(1L)
                .itemId(99L)
                .initiator(TestInitiators.USER)
                .build();
        DeleteItemCommand b = DeleteItemCommand.builder()
                .charId(1L)
                .itemId(100L)
                .initiator(TestInitiators.USER)
                .build();

        assertNotEquals(a, b);
    }

    @Test
    void equals_shouldDistinguishOnCount() {
        DeleteItemCommand a = DeleteItemCommand.builder()
                .charId(1L)
                .itemId(99L)
                .count(1L)
                .initiator(TestInitiators.USER)
                .build();
        DeleteItemCommand b = DeleteItemCommand.builder()
                .charId(1L)
                .itemId(99L)
                .count(5L)
                .initiator(TestInitiators.USER)
                .build();

        assertNotEquals(a, b);
    }

    @Test
    void hashCode_shouldMatchEquals() {
        DeleteItemCommand a = DeleteItemCommand.builder()
                .charId(1L)
                .itemId(99L)
                .count(3L)
                .initiator(TestInitiators.USER)
                .build();
        DeleteItemCommand b = DeleteItemCommand.builder()
                .charId(1L)
                .itemId(99L)
                .count(3L)
                .initiator(TestInitiators.USER)
                .build();

        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toString_shouldExposeAllFields() {
        DeleteItemCommand cmd = DeleteItemCommand.builder()
                .charId(42L)
                .itemId(1234567L)
                .count(7L)
                .initiator(TestInitiators.USER)
                .build();

        String s = cmd.toString();

        assertTrue(s.contains("42"));
        assertTrue(s.contains("1234567"));
        assertTrue(s.contains("7"));
    }

    @Test
    void implementsNxCommandMarker() {
        DeleteItemCommand cmd = DeleteItemCommand.builder()
                .charId(1L)
                .itemId(2L)
                .initiator(TestInitiators.USER)
                .build();

        assertInstanceOf(app.l2nx.gs.adapter.api.kafka.commands.NxCommand.class, cmd);
    }
}
