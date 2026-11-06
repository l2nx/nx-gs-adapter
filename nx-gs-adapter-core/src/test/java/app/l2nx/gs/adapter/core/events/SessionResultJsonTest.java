package app.l2nx.gs.adapter.core.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.Attribute;
import app.l2nx.gs.adapter.api.domain.item.ItemSnapshot;
import app.l2nx.gs.adapter.api.domain.item.ItemSource;
import app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResult;
import app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResultItem;
import app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResultUnique;
import app.l2nx.gs.adapter.api.kafka.events.character.result.SessionResultUntracked;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class SessionResultJsonTest {

    private static final UUID SESSION = UUID.fromString("01973a10-5e21-7c44-8f02-3b9d4e6a2c55");

    /** A block whose text is just over {@link SessionResult#MAX_JSON_BYTES}. */
    public static SessionResult overBudget() {
        return ofProcessChars(SessionResult.MAX_JSON_BYTES);
    }

    private static SessionResult ofProcessChars(int chars) {
        char[] process = new char[chars];
        Arrays.fill(process, 'x');
        return SessionResult.builder()
                .untracked(Collections.singletonList(SessionResultUntracked.builder()
                        .itemTemplateId(1L)
                        .process(new String(process))
                        .build()))
                .build();
    }

    private static int bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    @Nested
    class ToJson {

        @Test
        void toJson_shouldDrop_whenTheBlockIsOverTheBudget() {
            assertNull(SessionResultJson.toJson(overBudget(), SESSION));
        }

        @Test
        void toJson_shouldKeepABlockOfExactlyTheBudget() {
            int frame = bytes(SessionResultJson.toJson(ofProcessChars(1), SESSION)) - 1;

            String actual = SessionResultJson.toJson(ofProcessChars(SessionResult.MAX_JSON_BYTES - frame), SESSION);

            assertNotNull(actual);
            assertEquals(SessionResult.MAX_JSON_BYTES, bytes(actual));
        }

        @Test
        void toJson_shouldCountBytesNotCharacters() {
            char[] process = new char[SessionResult.MAX_JSON_BYTES / 2 + 1];
            Arrays.fill(process, 'я');
            SessionResult result = SessionResult.builder()
                    .untracked(Collections.singletonList(SessionResultUntracked.builder()
                            .itemTemplateId(1L)
                            .process(new String(process))
                            .build()))
                    .build();

            assertNull(SessionResultJson.toJson(result, SESSION));
        }
    }

    @Nested
    class Parse {

        @Test
        void parse_shouldRebuildTheBlock_withEveryRowKind() {
            List<SessionResultItem> items = new ArrayList<SessionResultItem>();
            items.add(SessionResultItem.builder()
                    .itemTemplateId(57L)
                    .source(ItemSource.MAIL)
                    .sourceCharacterId(268470001L)
                    .sourceRefId("90817")
                    .delta(150000L)
                    .events(1L)
                    .build());
            SessionResult expected = SessionResult.builder()
                    .partial(true)
                    .items(items)
                    .uniques(Collections.singletonList(SessionResultUnique.builder()
                            .item(ItemSnapshot.builder()
                                    .itemId(1L)
                                    .itemTemplateId(6579L)
                                    .attributes(Collections.singletonMap(Attribute.FIRE, 120))
                                    .build())
                            .source(ItemSource.TRADE)
                            .delta(1L)
                            .build()))
                    .build();

            String json = SessionResultJson.toJson(expected, SESSION);

            assertEquals(expected, SessionResultJson.parse(json));
            assertEquals(json, SessionResultJson.toJson(SessionResultJson.parse(json), SESSION));
        }

        @Test
        void parse_shouldReadAnEmptyObjectAsEmptySections() {
            SessionResult actual = SessionResultJson.parse("{}");

            assertTrue(actual.getItems().isEmpty());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(
                strings = {
                    "{broken",
                    "null",
                    "{\"items\":[{\"itemTemplateId\":57,\"source\":\"FROM_THE_FUTURE\",\"delta\":1,\"events\":1}]}",
                    "{\"kills\":[{\"count\":1}]}",
                    "{\"overflow\":-1}",
                    "{\"uniques\":[{\"item\":{\"itemId\":0,\"itemTemplateId\":1},\"source\":\"TRADE\",\"delta\":1}]}",
                    "{\"uniques\":[{\"item\":{\"itemId\":1,\"itemTemplateId\":1,\"attributes\":{\"FROM_THE_FUTURE\":1}},\"source\":\"TRADE\",\"delta\":1}]}"
                })
        void parse_shouldThrow_whenThisVersionCannotCarryTheBlock(String json) {
            assertThrows(RuntimeException.class, () -> SessionResultJson.parse(json));
        }
    }
}
