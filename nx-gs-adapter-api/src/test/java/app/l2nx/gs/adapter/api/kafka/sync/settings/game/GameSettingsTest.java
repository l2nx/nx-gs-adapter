package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.Attribute;
import app.l2nx.gs.adapter.api.domain.item.AttributeItemType;
import app.l2nx.gs.adapter.api.domain.item.AttributeTarget;
import app.l2nx.gs.adapter.api.domain.item.ItemGrade;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GameSettingsTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    @Test
    void build_shouldRejectNullUpdatedAt() {
        assertThrows(NullPointerException.class, () -> GameSettings.builder().build());
    }

    @Test
    void build_shouldKeepGroupsNull_whenOmitted() {
        GameSettings actual = GameSettings.builder().updatedAt(NOW).build();

        assertNull(actual.getEnchant());
        assertNull(actual.getAttribute());
        assertNull(actual.getMail());
        assertNull(actual.getPrivateStore());
        assertEquals(NOW, actual.getUpdatedAt());
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        GameSettings original = GameSettings.builder()
                .enchant(new EnchantSettings(16, null, 12))
                .attribute(AttributeSettings.builder().maxWeaponAttributes(1).build())
                .mail(new MailSettings(100, 10))
                .privateStore(new PrivateStoreSettings(57, 1000L, 24))
                .updatedAt(NOW)
                .build();

        assertEquals(original, original.toBuilder().build());
        assertNotEquals(original, original.toBuilder().mail(null).build());
    }

    @Test
    void entityName_shouldBeTheWireName() {
        assertEquals("gamesettings", GameSettings.ENTITY_NAME);
    }

    @Nested
    class Json {

        /** ISO-8601 instants, as the platform's mapper writes them; jackson-datatype-jsr310 is not on this classpath. */
        private ObjectMapper mapper() {
            SimpleModule instants = new SimpleModule()
                    .addSerializer(Instant.class, new JsonSerializer<Instant>() {
                        @Override
                        public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers)
                                throws IOException {
                            gen.writeString(value.toString());
                        }
                    })
                    .addDeserializer(Instant.class, new JsonDeserializer<Instant>() {
                        @Override
                        public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                            return Instant.parse(p.getValueAsString());
                        }
                    });
            return new ObjectMapper().registerModule(new ParameterNamesModule()).registerModule(instants);
        }

        @Test
        void writeValue_shouldWriteEveryOmittedGroupAsNull() throws IOException {
            GameSettings settings = GameSettings.builder().updatedAt(NOW).build();

            JsonNode actual = mapper().readTree(mapper().writeValueAsString(settings));

            for (String group : Arrays.asList("enchant", "attribute", "mail", "privateStore")) {
                assertTrue(actual.has(group), group);
                assertTrue(actual.get(group).isNull(), group);
            }
            assertEquals("2026-10-25T12:00:00Z", actual.get("updatedAt").asText());
        }

        @Test
        void readValue_shouldRoundTrip_whenGroupsAreNull() throws IOException {
            GameSettings expected = GameSettings.builder().updatedAt(NOW).build();

            GameSettings actual = mapper().readValue(mapper().writeValueAsString(expected), GameSettings.class);

            assertEquals(expected, actual);
        }

        @Test
        void readValue_shouldRoundTripEveryGroup() throws IOException {
            GameSettings expected = GameSettings.builder()
                    .enchant(new EnchantSettings(16, null, 12))
                    .attribute(AttributeSettings.builder()
                            .maxWeaponAttributes(1)
                            .maxArmorAttributes(3)
                            .oppositePairs(Collections.singletonList(
                                    new AttributeOppositePair(Attribute.FIRE, Attribute.WATER)))
                            .chances(Collections.singletonList(
                                    new AttributeChance(null, AttributeItemType.STONE, null, 50.0)))
                            .limits(Collections.singletonList(new AttributeLimit(
                                    ItemGrade.C, AttributeItemType.STONE, AttributeTarget.WEAPON, 150)))
                            .build())
                    .mail(new MailSettings(100, 10))
                    .privateStore(new PrivateStoreSettings(57, 1000L, 24))
                    .updatedAt(NOW)
                    .build();

            GameSettings actual = mapper().readValue(mapper().writeValueAsString(expected), GameSettings.class);

            assertEquals(expected, actual);
        }
    }
}
