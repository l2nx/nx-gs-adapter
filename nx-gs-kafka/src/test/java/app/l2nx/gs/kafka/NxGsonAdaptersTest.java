package app.l2nx.gs.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetTime;
import org.junit.jupiter.api.Test;

class NxGsonAdaptersTest {

    @Test
    void instant_shouldSerializeAsIso8601ZSuffix() {
        Gson gson = NxGsonAdapters.defaultGson();
        Instant instant = Instant.parse("2026-05-17T12:00:00Z");

        String json = gson.toJson(instant);

        assertEquals("\"2026-05-17T12:00:00Z\"", json);
    }

    @Test
    void instant_shouldRoundtrip() {
        Gson gson = NxGsonAdapters.defaultGson();
        Instant original = Instant.parse("2026-05-17T12:34:56.789Z");

        Instant decoded = gson.fromJson(gson.toJson(original), Instant.class);

        assertEquals(original, decoded);
    }

    @Test
    void duration_shouldSerializeAsIso8601() {
        Gson gson = NxGsonAdapters.defaultGson();

        assertEquals("\"PT1M\"", gson.toJson(Duration.ofSeconds(60)));
        assertEquals("\"PT1H30M\"", gson.toJson(Duration.ofMinutes(90)));
        assertEquals("\"PT0S\"", gson.toJson(Duration.ZERO));
    }

    @Test
    void duration_shouldRoundtrip() {
        Gson gson = NxGsonAdapters.defaultGson();
        Duration original = Duration.ofHours(2).plusMinutes(15).plusSeconds(30);

        Duration decoded = gson.fromJson(gson.toJson(original), Duration.class);

        assertEquals(original, decoded);
    }

    @Test
    void offsetTime_shouldSerializeAsIsoOffsetTime() {
        Gson gson = NxGsonAdapters.defaultGson();

        assertEquals("\"22:00:00+03:00\"", gson.toJson(OffsetTime.parse("22:00:00+03:00")));
        assertEquals("\"19:00:00Z\"", gson.toJson(OffsetTime.parse("19:00:00Z")));
    }

    @Test
    void offsetTime_shouldRoundtrip() {
        Gson gson = NxGsonAdapters.defaultGson();
        OffsetTime original = OffsetTime.parse("21:45:00+03:00");

        OffsetTime decoded = gson.fromJson(gson.toJson(original), OffsetTime.class);

        assertEquals(original, decoded);
    }
}
