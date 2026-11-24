package app.l2nx.gs.adapter.api.kafka.commands.ban;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.ban.model.BanRecord;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.BanSource;
import app.l2nx.gs.adapter.api.kafka.sync.db.ban.BanDbDto;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BanResultTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-12-01T00:00:00Z");
    private static final Instant OBSERVED_AT = Instant.parse("2026-11-17T10:15:30.123Z");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    private static BanRecord standard() {
        return new BanRecord(
                BanSource.STANDARD.name(),
                BanDbDto.builder()
                        .id(11L)
                        .subjectType("ACCOUNT")
                        .subjectValue("login1")
                        .banType("GAME_LOGIN")
                        .expiresAt(EXPIRES_AT)
                        .build());
    }

    private static BanRecord shadow() {
        return new BanRecord(
                BanSource.SHADOW.name(),
                BanDbDto.builder()
                        .id(268435457L)
                        .subjectType("CHARACTER")
                        .subjectValue("268435457")
                        .banType("CHAT_SHADOW")
                        .build());
    }

    private static BanResult.Builder valid() {
        return BanResult.builder().bans(Arrays.asList(standard(), shadow())).observedAt(OBSERVED_AT);
    }

    @Nested
    class Constructor {

        @Test
        void constructor_shouldReject_whenBansEmpty() {
            BanResult.Builder builder = valid().bans(Collections.<BanRecord>emptyList());

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void constructor_shouldReject_whenBansNull() {
            BanResult.Builder builder = valid().bans(null);

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void constructor_shouldReject_whenBansHoldNull() {
            BanResult.Builder builder = valid().bans(Arrays.asList(standard(), null));

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void constructor_shouldReject_whenObservedAtNull() {
            BanResult.Builder builder = valid().observedAt(null);

            assertThrows(IllegalArgumentException.class, builder::build);
        }
    }

    @Nested
    class GetBans {

        @Test
        void getBans_shouldBeUnmodifiableCopy() {
            List<BanRecord> source = new ArrayList<BanRecord>(Collections.singletonList(standard()));
            BanResult actual = valid().bans(source).build();

            source.clear();

            assertEquals(Collections.singletonList(standard()), actual.getBans());
            assertThrows(
                    UnsupportedOperationException.class, () -> actual.getBans().add(shadow()));
        }
    }

    @Nested
    class ValueSemantics {

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            BanResult original = valid().build();

            BanResult copy = original.toBuilder().build();

            assertEquals(original, copy);
            assertEquals(original.hashCode(), copy.hashCode());
            assertNotSame(original, copy);
        }

        @Test
        void equals_shouldDistinguishObservedAt() {
            assertNotEquals(valid().build(), valid().observedAt(EXPIRES_AT).build());
        }
    }

    @Nested
    class Json {

        @Test
        void gson_shouldRoundtripAllFields() {
            BanResult original = valid().build();

            BanResult actual = GSON.fromJson(GSON.toJson(original), BanResult.class);

            assertEquals(original, actual);
            assertEquals(OBSERVED_AT, actual.getObservedAt());
            assertEquals(EXPIRES_AT, actual.getBans().get(0).getRow().getExpiresAt());
            assertNull(actual.getBans().get(1).getRow().getExpiresAt());
        }

        @Test
        void gson_shouldWriteSourceTokenAndRow() {
            JsonObject actual = GSON.toJsonTree(valid().build()).getAsJsonObject();

            assertEquals(OBSERVED_AT.toString(), actual.get("observedAt").getAsString());
            assertFalse(actual.has("banIds"));
            JsonObject shadow = actual.getAsJsonArray("bans").get(1).getAsJsonObject();
            assertEquals("SHADOW", shadow.get("source").getAsString());
            assertEquals(268435457L, shadow.getAsJsonObject("row").get("id").getAsLong());
            assertFalse(shadow.getAsJsonObject("row").has("expiresAt"));
        }
    }
}
