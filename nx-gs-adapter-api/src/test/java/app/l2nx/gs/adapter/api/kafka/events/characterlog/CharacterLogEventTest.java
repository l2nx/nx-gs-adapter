package app.l2nx.gs.adapter.api.kafka.events.characterlog;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CharacterLogEventTest {

    private static final UUID EVENT_ID = UUID.fromString("018f5fa3-1e3d-7000-8000-000000000000");

    private static CharacterLogEvent.Builder valid() {
        return CharacterLogEvent.builder()
                .eventId(EVENT_ID)
                .characterId(268476304L)
                .type(WellKnownCharacterLogTypes.SECOND_CLASS);
    }

    static Stream<Arguments> missingRequiredFields() {
        return Stream.of(
                Arguments.of(
                        "eventId",
                        CharacterLogEvent.builder().characterId(1L).type(WellKnownCharacterLogTypes.NOBLESSE)),
                Arguments.of(
                        "type",
                        CharacterLogEvent.builder().eventId(UUID.randomUUID()).characterId(1L)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingRequiredFields")
    void constructor_shouldReject_whenRequiredFieldNull(String name, CharacterLogEvent.Builder builder) {
        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void getMetadata_shouldReturnNull_whenBuilderOmits() {
        assertNull(valid().build().getMetadata());
    }

    @Test
    void getMetadata_shouldBeAnUnmodifiableCopyOfTheSourceMap() {
        Map<String, String> source = new LinkedHashMap<String, String>();
        source.put(WellKnownCharacterLogMetadata.CLASS_LEVEL, "2");
        CharacterLogEvent event = valid().metadata(source).build();

        source.put(WellKnownCharacterLogMetadata.CLASS_INDEX, "0");

        assertEquals(1, event.getMetadata().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> event.getMetadata().put(WellKnownCharacterLogMetadata.CHAR_LEVEL, "40"));
    }

    @Test
    void toBuilder_shouldRoundTrip() {
        CharacterLogEvent event = valid().metadata(
                        Collections.singletonMap(WellKnownCharacterLogMetadata.CLASS_ID, "89"))
                .build();

        assertEquals(event, event.toBuilder().build());
        assertEquals(event.hashCode(), event.toBuilder().build().hashCode());
        assertEquals(268476304L, event.getCharacterId());
        assertEquals(WellKnownCharacterLogTypes.SECOND_CLASS, event.getType());
        assertEquals(EVENT_ID, event.getEventId());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(CharacterLogEvent.class)
                .withNonnullFields("eventId", "type")
                .verify();
    }
}
