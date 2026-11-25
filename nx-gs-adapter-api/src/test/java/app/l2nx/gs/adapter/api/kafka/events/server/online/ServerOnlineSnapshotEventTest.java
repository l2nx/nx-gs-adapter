package app.l2nx.gs.adapter.api.kafka.events.server.online;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.events.server.online.model.ServerOnlineBucket;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ServerOnlineSnapshotEventTest {

    private static Map<ServerOnlineBucket, Long> required(long total, long unique) {
        Map<ServerOnlineBucket, Long> buckets = new EnumMap<ServerOnlineBucket, Long>(ServerOnlineBucket.class);
        buckets.put(ServerOnlineBucket.TOTAL, total);
        buckets.put(ServerOnlineBucket.UNIQUE, unique);
        return buckets;
    }

    private static ServerOnlineSnapshotEvent.Builder valid() {
        return ServerOnlineSnapshotEvent.builder().eventId(UUID.randomUUID()).buckets(required(100L, 95L));
    }

    static Stream<Arguments> withoutRequiredBuckets() {
        Map<ServerOnlineBucket, Long> withoutTotal = required(1L, 1L);
        withoutTotal.remove(ServerOnlineBucket.TOTAL);
        Map<ServerOnlineBucket, Long> withoutUnique = required(1L, 1L);
        withoutUnique.remove(ServerOnlineBucket.UNIQUE);

        return Stream.of(
                Arguments.of("buckets", ServerOnlineSnapshotEvent.builder().eventId(UUID.randomUUID())),
                Arguments.of("TOTAL", valid().buckets(withoutTotal)),
                Arguments.of("UNIQUE", valid().buckets(withoutUnique)));
    }

    @Nested
    class Constructor {

        @ParameterizedTest(name = "{0}")
        @MethodSource(
                "app.l2nx.gs.adapter.api.kafka.events.server.online.ServerOnlineSnapshotEventTest#withoutRequiredBuckets")
        void constructor_shouldReject_whenRequiredBucketMissing(
                String missing, ServerOnlineSnapshotEvent.Builder builder) {
            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void constructor_shouldDefensivelyCopyAndFreezeBucketsMap() {
            Map<ServerOnlineBucket, Long> source = required(100L, 95L);

            ServerOnlineSnapshotEvent event = valid().buckets(source).build();
            source.put(ServerOnlineBucket.APP, 5L);

            assertEquals(2, event.getBuckets().size());
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> event.getBuckets().put(ServerOnlineBucket.APP, 1L));
        }
    }

    @Nested
    class Builder {

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            UUID eventId = UUID.randomUUID();
            Map<ServerOnlineBucket, Long> buckets = required(1808L, 1640L);
            buckets.put(ServerOnlineBucket.OFFLINE_PRIVATE_STORE, 22L);
            buckets.put(ServerOnlineBucket.FISHING, 15L);
            buckets.put(ServerOnlineBucket.APP, 9L);
            buckets.put(ServerOnlineBucket.OFFLINE_FISHING, 4L);

            ServerOnlineSnapshotEvent original = valid().eventId(eventId)
                    .buckets(buckets)
                    .metadata(Collections.singletonMap("k", "v"))
                    .build();

            ServerOnlineSnapshotEvent copy = original.toBuilder().build();
            assertEquals(original, copy);
            assertNotSame(original, copy);
            assertEquals(eventId, copy.getEventId());
            assertEquals(buckets, copy.getBuckets());
            assertEquals(Collections.singletonMap("k", "v"), copy.getMetadata());
        }

        @Test
        void toBuilder_shouldRoundtrip_whenMetadataAbsent() {
            ServerOnlineSnapshotEvent original = valid().build();

            ServerOnlineSnapshotEvent copy = original.toBuilder().build();

            assertEquals(original, copy);
            assertNotSame(original, copy);
            assertNull(copy.getMetadata());
        }
    }

    @Nested
    class Equality {

        @Test
        void equals_shouldFollowTheContract() {
            EqualsVerifier.forClass(ServerOnlineSnapshotEvent.class).verify();
        }
    }

    @Nested
    class GsonRoundtrip {

        @Test
        void gson_shouldWriteEnumMapKeysAsConstantNames_andReadThemBack() {
            Gson gson = new Gson();
            Map<ServerOnlineBucket, Long> buckets = required(10L, 9L);
            buckets.put(ServerOnlineBucket.OFFLINE_FISHING, 3L);
            ServerOnlineSnapshotEvent original = valid().buckets(buckets).build();

            String json = gson.toJson(original);

            JsonObject written = gson.fromJson(json, JsonObject.class).getAsJsonObject("buckets");
            assertEquals(3, written.size());
            assertEquals(10L, written.get("TOTAL").getAsLong());
            assertEquals(3L, written.get("OFFLINE_FISHING").getAsLong());
            assertEquals(original, gson.fromJson(json, ServerOnlineSnapshotEvent.class));
        }
    }
}
