package app.l2nx.gs.adapter.api.rest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MessagingTopicsTest {

    @Test
    void getEvents_shouldReturnEmptyMap_whenConstructorReceivesNull() {
        MessagingTopics topics = new MessagingTopics(null, null, null);

        assertTrue(topics.getEvents().isEmpty());
    }

    @ParameterizedTest(name = "[{0}], [{1}]")
    @CsvSource({",", "'  ',''"})
    void getCommandsTopic_shouldReturnNull_whenConstructorReceivesNullOrBlank(
            String commandsTopic, String commandsRepliesTopic) {
        MessagingTopics topics = new MessagingTopics(null, commandsTopic, commandsRepliesTopic);

        assertNull(topics.getCommandsTopic());
        assertNull(topics.getCommandsRepliesTopic());
    }

    @Test
    void getEvents_shouldBeAFrozenCopyOfTheSource() {
        Map<String, String> source = new HashMap<String, String>();
        source.put("premiumpurchase", "acme.gs.events.premiumpurchase");

        MessagingTopics topics = new MessagingTopics(source, null, null);
        source.put("character", "acme.gs.events.character");

        assertEquals(1, topics.getEvents().size());
        assertThrows(
                UnsupportedOperationException.class, () -> topics.getEvents().put("character", "x"));
    }

    @Test
    void toBuilder_shouldRoundtripEveryField() {
        Map<String, String> events = new HashMap<String, String>();
        events.put("premiumpurchase", "acme.gs.events.premiumpurchase");
        events.put("character", "acme.gs.events.character");
        MessagingTopics original = MessagingTopics.builder()
                .events(events)
                .commandsTopic("acme.gs.commands")
                .commandsRepliesTopic("acme.gs.commands.replies")
                .build();

        MessagingTopics actual = original.toBuilder().build();

        assertEquals(original, actual);
        assertEquals("acme.gs.events.premiumpurchase", actual.getEvents().get("premiumpurchase"));
        assertEquals("acme.gs.events.character", actual.getEvents().get("character"));
        assertEquals("acme.gs.commands", actual.getCommandsTopic());
        assertEquals("acme.gs.commands.replies", actual.getCommandsRepliesTopic());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(MessagingTopics.class).verify();
    }
}
