package app.l2nx.gs.adapter.api.kafka.commands.ban;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.ban.model.BanRef;
import app.l2nx.gs.adapter.api.kafka.commands.ban.model.BanSource;
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

class UnbanResultTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-11-17T10:15:30.123Z");
    private static final BanRef STANDARD = new BanRef(BanSource.STANDARD.name(), 11L);
    private static final BanRef SHADOW = new BanRef(BanSource.SHADOW.name(), 268435457L);
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    private static UnbanResult.Builder valid() {
        return UnbanResult.builder()
                .removedBans(Arrays.asList(STANDARD, SHADOW))
                .observedAt(OBSERVED_AT);
    }

    @Nested
    class Constructor {

        @Test
        void constructor_shouldAcceptEmptyRemovedBans_whenNothingMatched() {
            UnbanResult actual =
                    valid().removedBans(Collections.<BanRef>emptyList()).build();

            assertEquals(Collections.<BanRef>emptyList(), actual.getRemovedBans());
        }

        @Test
        void constructor_shouldReject_whenRemovedBansNull() {
            UnbanResult.Builder builder = valid().removedBans(null);

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void constructor_shouldReject_whenRemovedBansHoldNull() {
            UnbanResult.Builder builder = valid().removedBans(Arrays.asList(STANDARD, null));

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void constructor_shouldReject_whenObservedAtNull() {
            UnbanResult.Builder builder = valid().observedAt(null);

            assertThrows(IllegalArgumentException.class, builder::build);
        }
    }

    @Nested
    class GetRemovedBans {

        @Test
        void getRemovedBans_shouldBeUnmodifiableCopy() {
            List<BanRef> source = new ArrayList<BanRef>(Collections.singletonList(STANDARD));
            UnbanResult actual = valid().removedBans(source).build();

            source.clear();

            assertEquals(Collections.singletonList(STANDARD), actual.getRemovedBans());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> actual.getRemovedBans().add(SHADOW));
        }
    }

    @Nested
    class ToBuilder {

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            UnbanResult original = valid().build();

            UnbanResult copy = original.toBuilder().build();

            assertEquals(original, copy);
            assertEquals(original.hashCode(), copy.hashCode());
            assertNotSame(original, copy);
        }
    }

    @Nested
    class Json {

        @Test
        void gson_shouldRoundtripAllFields() {
            UnbanResult original = valid().build();

            UnbanResult actual = GSON.fromJson(GSON.toJson(original), UnbanResult.class);

            assertEquals(original, actual);
            assertEquals(OBSERVED_AT, actual.getObservedAt());
        }

        @Test
        void gson_shouldWriteSourceTokenAndId() {
            JsonObject actual = GSON.toJsonTree(valid().build()).getAsJsonObject();

            assertEquals(OBSERVED_AT.toString(), actual.get("observedAt").getAsString());
            assertFalse(actual.has("removedBanIds"));
            assertFalse(actual.has("removed"));
            JsonObject shadow = actual.getAsJsonArray("removedBans").get(1).getAsJsonObject();
            assertEquals("SHADOW", shadow.get("source").getAsString());
            assertEquals(268435457L, shadow.get("id").getAsLong());
        }
    }
}
