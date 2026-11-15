package app.l2nx.gs.adapter.api.kafka.commands.ban;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.commands.ban.model.BanRecord;
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
import org.junit.jupiter.api.Test;

class BanResultTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-12-01T00:00:00Z");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    private static BanResult.Builder valid() {
        return BanResult.builder()
                .banIds(Arrays.asList(11L, 12L))
                .bans(Arrays.asList(new BanRecord(11L, EXPIRES_AT), new BanRecord(12L, null)));
    }

    @Test
    void getBans_shouldBeNull_whenBuilderOmits() {
        BanResult actual = BanResult.builder().banIds(Arrays.asList(11L)).build();

        assertNull(actual.getBans());
        assertEquals(Collections.singletonList(11L), actual.getBanIds());
    }

    @Test
    void getBans_shouldBeUnmodifiableCopy() {
        List<BanRecord> source = new ArrayList<BanRecord>(Collections.singletonList(new BanRecord(11L, null)));
        BanResult actual = BanResult.builder()
                .banIds(Collections.singletonList(11L))
                .bans(source)
                .build();

        source.clear();

        assertEquals(1, actual.getBans().size());
        assertThrows(UnsupportedOperationException.class, () -> actual.getBans().add(new BanRecord(12L, null)));
    }

    @Test
    void constructor_shouldReject_whenBansSizeDiffersFromBanIds() {
        BanResult.Builder builder = valid().bans(Collections.singletonList(new BanRecord(11L, null)));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenBansIdsDifferFromBanIds() {
        BanResult.Builder builder =
                valid().bans(Arrays.asList(new BanRecord(12L, null), new BanRecord(11L, EXPIRES_AT)));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenBansHoldNull() {
        BanResult.Builder builder = valid().bans(Arrays.asList(new BanRecord(11L, null), null));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void constructor_shouldReject_whenBanIdsHoldNull() {
        BanResult.Builder builder = BanResult.builder().banIds(Arrays.asList(11L, null));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void getBanIds_shouldBeUnmodifiableCopy() {
        List<Long> source = new ArrayList<Long>(Arrays.asList(11L, 12L));
        BanResult actual = BanResult.builder().banIds(source).build();

        source.set(0, 99L);
        source.add(13L);

        assertEquals(Arrays.asList(11L, 12L), actual.getBanIds());
        assertThrows(
                UnsupportedOperationException.class, () -> actual.getBanIds().add(13L));
    }

    @Test
    void constructor_shouldAcceptEmptyBans_whenBanIdsEmpty() {
        BanResult actual = BanResult.builder()
                .banIds(Collections.<Long>emptyList())
                .bans(Collections.<BanRecord>emptyList())
                .build();

        assertEquals(Collections.<BanRecord>emptyList(), actual.getBans());
    }

    @Test
    void toBuilder_shouldRoundtripAllFields() {
        BanResult original = valid().build();

        BanResult copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotSame(original, copy);
    }

    @Test
    void gson_shouldRoundtrip_whenBansPresent() {
        BanResult original = valid().build();

        BanResult actual = GSON.fromJson(GSON.toJson(original), BanResult.class);

        assertEquals(original, actual);
        assertEquals(EXPIRES_AT, actual.getBans().get(0).getExpiresAt());
        assertNull(actual.getBans().get(1).getExpiresAt());
    }

    @Test
    void gson_shouldRoundtrip_whenBansNull() {
        BanResult original =
                BanResult.builder().banIds(Collections.singletonList(11L)).build();

        BanResult actual = GSON.fromJson(GSON.toJson(original), BanResult.class);

        assertEquals(original, actual);
        assertNull(actual.getBans());
    }

    @Test
    void gson_shouldReadNullBans_whenOlderHostOmitsField() {
        BanResult actual = GSON.fromJson("{\"banIds\":[11]}", BanResult.class);

        assertEquals(Collections.singletonList(11L), actual.getBanIds());
        assertNull(actual.getBans());
    }

    @Test
    void gson_shouldOmitPermanentExpiry() {
        JsonObject actual = GSON.toJsonTree(valid().build()).getAsJsonObject();

        JsonObject permanent = actual.getAsJsonArray("bans").get(1).getAsJsonObject();
        assertEquals(12L, permanent.get("id").getAsLong());
        assertFalse(permanent.has("expiresAt"));
    }
}
