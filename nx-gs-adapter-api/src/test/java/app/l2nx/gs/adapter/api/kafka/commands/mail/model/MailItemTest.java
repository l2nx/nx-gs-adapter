package app.l2nx.gs.adapter.api.kafka.commands.mail.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MailItemTest {

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        MailItem original =
                MailItem.builder().itemTemplateId(57L).count(1_000_000L).build();

        assertEquals(57L, original.getItemTemplateId().longValue());
        assertEquals(1_000_000L, original.getCount().longValue());
        assertEquals(original, original.toBuilder().build());
    }

    @Test
    void builder_shouldDefaultCountToOne_whenNotSet() {
        MailItem item = MailItem.builder().itemTemplateId(57L).build();

        assertEquals(1L, item.getCount().longValue());
    }

    private static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("itemTemplateId", (Executable) () -> new MailItem(null, 1L)),
                Arguments.of("count", (Executable) () -> new MailItem(57L, null)),
                Arguments.of("count", (Executable) () -> new MailItem(57L, 0L)),
                Arguments.of("count", (Executable) () -> new MailItem(57L, -1L)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidFields")
    void constructor_shouldReject_whenFieldInvalid(String field, Executable construction) {
        IllegalArgumentException actual = assertThrows(IllegalArgumentException.class, construction);
        assertTrue(actual.getMessage().contains(field));
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(MailItem.class).verify();
    }
}
