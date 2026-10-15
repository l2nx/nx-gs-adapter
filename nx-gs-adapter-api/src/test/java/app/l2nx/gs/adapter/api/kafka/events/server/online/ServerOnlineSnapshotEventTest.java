package app.l2nx.gs.adapter.api.kafka.events.server.online;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.events.server.online.model.ServerOnlineBucket;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

    @Nested
    class Constructor {

        @Test
        void constructor_shouldReject_whenBucketsOmitted() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ServerOnlineSnapshotEvent.builder()
                            .eventId(UUID.randomUUID())
                            .build());
        }

        @Test
        void constructor_shouldReject_whenTotalMissing() {
            Map<ServerOnlineBucket, Long> buckets = required(1L, 1L);
            buckets.remove(ServerOnlineBucket.TOTAL);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> valid().buckets(buckets).build());
        }

        @Test
        void constructor_shouldReject_whenUniqueMissing() {
            Map<ServerOnlineBucket, Long> buckets = required(1L, 1L);
            buckets.remove(ServerOnlineBucket.UNIQUE);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> valid().buckets(buckets).build());
        }

        @Test
        void getBuckets_shouldBeUnmodifiable() {
            ServerOnlineSnapshotEvent event = valid().build();

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> event.getBuckets().put(ServerOnlineBucket.APP, 1L));
        }

        @Test
        void constructor_shouldDefensivelyCopyBucketsMap() {
            Map<ServerOnlineBucket, Long> source = required(100L, 95L);

            ServerOnlineSnapshotEvent event = valid().buckets(source).build();
            source.put(ServerOnlineBucket.APP, 5L);

            assertEquals(2, event.getBuckets().size());
        }
    }

    @Nested
    class Builder {

        @Test
        void toBuilder_shouldRoundtripAllFields() {
            Map<ServerOnlineBucket, Long> buckets = required(1808L, 1640L);
            buckets.put(ServerOnlineBucket.OFFLINE_PRIVATE_STORE, 22L);
            buckets.put(ServerOnlineBucket.FISHING, 15L);
            buckets.put(ServerOnlineBucket.APP, 9L);
            buckets.put(ServerOnlineBucket.OFFLINE_FISHING, 4L);

            ServerOnlineSnapshotEvent original = valid().buckets(buckets).build();

            ServerOnlineSnapshotEvent copy = original.toBuilder().build();
            assertEquals(original, copy);
            assertNotSame(original, copy);
        }
    }

    @Nested
    class Equality {

        @Test
        void equals_shouldDistinguishEventId() {
            assertNotEquals(valid().build(), valid().build());
        }

        @Test
        void equals_shouldDistinguishBucketCounts() {
            UUID id = UUID.randomUUID();

            assertNotEquals(
                    valid().eventId(id).buckets(required(100L, 95L)).build(),
                    valid().eventId(id).buckets(required(101L, 95L)).build());
        }

        @Test
        void toString_shouldRenderEventIdAndBuckets() {
            UUID id = UUID.fromString("018f5fa3-1e3d-7000-8000-000000000000");

            String s = valid().eventId(id).buckets(required(1808L, 1L)).build().toString();

            assertTrue(s.contains("eventId=" + id));
            assertTrue(s.contains("TOTAL=1808"));
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
