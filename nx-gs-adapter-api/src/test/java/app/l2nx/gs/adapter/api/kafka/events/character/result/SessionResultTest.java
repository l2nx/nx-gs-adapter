package app.l2nx.gs.adapter.api.kafka.events.character.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.Attribute;
import app.l2nx.gs.adapter.api.domain.character.ExperienceSource;
import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import app.l2nx.gs.adapter.api.domain.item.ItemSource;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SessionResultTest {

    private static SessionResultItem.Builder item() {
        return SessionResultItem.builder()
                .itemTemplateId(6411L)
                .source(ItemSource.FISHING)
                .delta(37L)
                .events(36L);
    }

    private static SessionResultUnique.Builder unique() {
        return SessionResultUnique.builder()
                .item(ItemSnapshot.builder()
                        .itemId(1073812L)
                        .itemTemplateId(6579L)
                        .count(1L)
                        .enchantLevel(4)
                        .attributes(Collections.singletonMap(Attribute.FIRE, 120))
                        .build())
                .source(ItemSource.TRADE)
                .delta(1L)
                .sourceCharacterId(268470002L)
                .sourceRefId("01973b1f-6d02-7b8e-a4c1-77e0d2f4a903");
    }

    private static SessionResult full() {
        return SessionResult.builder()
                .partial(true)
                .overflow(2L)
                .items(Collections.singletonList(item().build()))
                .uniques(Collections.singletonList(unique().build()))
                .experience(Collections.singletonList(SessionResultExperience.builder()
                        .source(ExperienceSource.KILL)
                        .sourceNpcId(20432L)
                        .exp(18230L)
                        .sp(910L)
                        .build()))
                .kills(Collections.singletonList(SessionResultKill.builder()
                        .sourceNpcId(20432L)
                        .count(12L)
                        .build()))
                .deaths(Collections.singletonList(SessionResultDeath.builder()
                        .sourceCharacterId(268470003L)
                        .count(1L)
                        .build()))
                .untracked(Collections.singletonList(SessionResultUntracked.builder()
                        .itemTemplateId(1864L)
                        .delta(-5L)
                        .process("SomeEngineProcess")
                        .build()))
                .build();
    }

    static Stream<Arguments> invalidRows() {
        return Stream.of(
                Arguments.of(
                        "negative overflow",
                        (Executable) () -> SessionResult.builder().overflow(-1L).build(),
                        IllegalArgumentException.class),
                Arguments.of(
                        "item template 0",
                        (Executable) () -> item().itemTemplateId(0L).build(),
                        IllegalArgumentException.class),
                Arguments.of(
                        "item without events",
                        (Executable) () -> item().events(0L).build(),
                        IllegalArgumentException.class),
                Arguments.of(
                        "unique with delta 0",
                        (Executable) () -> unique().delta(0L).build(),
                        IllegalArgumentException.class),
                Arguments.of(
                        "kill with neither victim",
                        (Executable) () -> SessionResultKill.builder().count(1L).build(),
                        IllegalArgumentException.class),
                Arguments.of(
                        "kill with both victims",
                        (Executable) () -> SessionResultKill.builder()
                                .sourceNpcId(1L)
                                .sourceCharacterId(2L)
                                .count(1L)
                                .build(),
                        IllegalArgumentException.class),
                Arguments.of(
                        "kill count 0",
                        (Executable) () ->
                                SessionResultKill.builder().sourceNpcId(1L).build(),
                        IllegalArgumentException.class),
                Arguments.of(
                        "death count 0",
                        (Executable) () -> SessionResultDeath.builder().build(),
                        IllegalArgumentException.class),
                Arguments.of(
                        "untracked template 0",
                        (Executable)
                                () -> SessionResultUntracked.builder().delta(1L).build(),
                        IllegalArgumentException.class),
                Arguments.of(
                        "item without source",
                        (Executable) () -> item().source(null).build(),
                        NullPointerException.class),
                Arguments.of(
                        "unique without source",
                        (Executable) () -> unique().source(null).build(),
                        NullPointerException.class),
                Arguments.of(
                        "unique without item",
                        (Executable) () -> unique().item(null).build(),
                        NullPointerException.class),
                Arguments.of(
                        "experience without source",
                        (Executable) () -> SessionResultExperience.builder().build(),
                        NullPointerException.class));
    }

    @Nested
    class Constructor {

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResultTest#invalidRows")
        void constructor_shouldReject(String name, Executable build, Class<? extends RuntimeException> expected) {
            assertThrows(expected, build);
        }

        @Test
        void constructor_shouldAcceptANetOfZero_whenOperationsHappened() {
            assertEquals(0L, item().delta(0L).events(2L).build().getDelta());
        }

        @Test
        void constructor_shouldAcceptADeathWithoutAKnownKiller() {
            SessionResultDeath actual = SessionResultDeath.builder().count(1L).build();

            assertNull(actual.getSourceNpcId());
            assertNull(actual.getSourceCharacterId());
        }

        @ParameterizedTest(name = "value=[{0}]")
        @ValueSource(strings = {"", "   "})
        void constructor_shouldTurnBlankRefsAndProcessIntoNull(String blank) {
            assertNull(item().sourceRefId(blank).build().getSourceRefId());
            assertNull(unique().sourceRefId(blank).build().getSourceRefId());
            assertNull(SessionResultUntracked.builder()
                    .itemTemplateId(1L)
                    .process(blank)
                    .build()
                    .getProcess());
        }

        @Test
        void constructor_shouldCopyTheLists() {
            List<SessionResultItem> source = new ArrayList<SessionResultItem>();
            source.add(item().build());
            SessionResult actual = SessionResult.builder().items(source).build();

            source.clear();

            assertEquals(1, actual.getItems().size());
            List<SessionResultItem> rows = actual.getItems();
            assertThrows(UnsupportedOperationException.class, rows::clear);
        }
    }

    @Nested
    class Sections {

        @Test
        void builder_shouldLeaveEveryAbsentSectionEmpty() {
            SessionResult actual = SessionResult.builder().build();

            assertFalse(actual.isPartial());
            assertEquals(0L, actual.getOverflow());
            assertTrue(actual.getItems().isEmpty());
            assertTrue(actual.getUniques().isEmpty());
            assertTrue(actual.getExperience().isEmpty());
            assertTrue(actual.getKills().isEmpty());
            assertTrue(actual.getDeaths().isEmpty());
            assertTrue(actual.getUntracked().isEmpty());
        }

        @Test
        void gson_shouldReadAnAbsentSectionAsEmpty() {
            SessionResult actual = new Gson().fromJson("{\"partial\":true}", SessionResult.class);

            assertTrue(actual.isPartial());
            assertTrue(actual.getItems().isEmpty());
            assertTrue(actual.getUntracked().isEmpty());
            assertEquals(SessionResult.builder().partial(true).build(), actual);
        }

        @Test
        void toString_shouldNameSizesNotRows() {
            String actual = full().toString();

            assertTrue(actual.contains("items=1"), actual);
            assertFalse(actual.contains("6411"), actual);
        }
    }

    @Nested
    class Value {

        @Test
        void equals_shouldFollowTheContract() {
            EqualsVerifier.forClass(SessionResult.class).verify();
        }

        @Test
        void roundtrip_shouldKeepEverySectionAndWriteConstantNames() {
            Gson gson = new Gson();

            String json = gson.toJson(full());

            assertTrue(json.contains("\"source\":\"FISHING\""), json);
            assertTrue(json.contains("\"source\":\"KILL\""), json);
            assertEquals(full(), gson.fromJson(json, SessionResult.class));
            assertEquals(full(), full().toBuilder().build());
            assertEquals(full().hashCode(), full().toBuilder().build().hashCode());
        }

        @Test
        void jackson_shouldBindThroughTheConstructor() throws Exception {
            ObjectMapper jackson = JsonMapper.builder()
                    .addModule(new ParameterNamesModule())
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();

            String json = new Gson().toJson(full());

            assertEquals(full(), jackson.readValue(json, SessionResult.class));
        }

        @Test
        void rows_shouldCompareByValue() {
            assertEquals(item().build(), item().build());
            assertNotEquals(item().build(), item().sourceRefId("90817").build());
            assertEquals(Arrays.asList(unique().build()), Arrays.asList(unique().build()));
            assertNotEquals(unique().build(), unique().delta(-1L).build());
        }
    }

    @Test
    void maxJsonBytes_shouldLeaveRoomInsideTheProducerLimit() {
        assertEquals(800_000, SessionResult.MAX_JSON_BYTES);
    }

    @Test
    void build_shouldPlaceEveryRowValueOnItsField() {
        SessionResult actual = full();

        SessionResultItem item = actual.getItems().get(0);
        assertEquals(6411L, item.getItemTemplateId());
        assertEquals(ItemSource.FISHING, item.getSource());
        assertEquals(37L, item.getDelta());
        assertEquals(36L, item.getEvents());
        assertEquals("90817", item.toBuilder().sourceRefId("90817").build().getSourceRefId());
        SessionResultUnique unique = actual.getUniques().get(0);
        assertEquals(1073812L, unique.getItem().getItemId());
        assertEquals(ItemSource.TRADE, unique.getSource());
        assertEquals(1L, unique.getDelta());
        assertEquals(268470002L, unique.getSourceCharacterId());
        assertEquals("01973b1f-6d02-7b8e-a4c1-77e0d2f4a903", unique.getSourceRefId());
        SessionResultExperience experience = actual.getExperience().get(0);
        assertEquals(ExperienceSource.KILL, experience.getSource());
        assertEquals(20432L, experience.getSourceNpcId());
        assertEquals(18230L, experience.getExp());
        assertEquals(910L, experience.getSp());
        SessionResultKill kill = actual.getKills().get(0);
        assertEquals(20432L, kill.getSourceNpcId());
        assertEquals(12L, kill.getCount());
        assertEquals(
                268470003L,
                SessionResultKill.builder()
                        .sourceCharacterId(268470003L)
                        .count(1L)
                        .build()
                        .getSourceCharacterId());
        SessionResultDeath death = actual.getDeaths().get(0);
        assertEquals(268470003L, death.getSourceCharacterId());
        assertEquals(1L, death.getCount());
        SessionResultUntracked untracked = actual.getUntracked().get(0);
        assertEquals(1864L, untracked.getItemTemplateId());
        assertEquals(-5L, untracked.getDelta());
        assertEquals("SomeEngineProcess", untracked.getProcess());
    }
}
