package app.l2nx.gs.kafka;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class KafkaConfigTest {

    @AfterEach
    void tearDown() {
        try {
            NxKafka.instance().shutdown();
        } catch (KafkaException ignored) {
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "   ")
    void build_shouldThrow_whenBrokersNullOrBlank(String brokers) {
        KafkaConfig.Builder builder = NxKafka.configure().brokers(brokers).clientId("test");

        KafkaException ex = assertThrows(KafkaException.class, builder::build);
        assertTrue(ex.getMessage().contains("Brokers"));
    }

    @Test
    void build_shouldPassCustomPropertiesToClients() {
        RecordingReporter.SECURITY_PROTOCOL_BY_CLIENT_ID.clear();

        NxKafka.configure()
                .brokers("localhost:19999")
                .clientId("custom-id")
                .connectTimeout(1, TimeUnit.SECONDS)
                .reconnect(false)
                .property("security.protocol", "PLAINTEXT")
                .property("metric.reporters", RecordingReporter.class.getName())
                .build();

        Map<Object, Object> expected = new HashMap<>();
        expected.put("custom-id-admin", "PLAINTEXT");
        expected.put("custom-id-producer", "PLAINTEXT");
        assertEquals(expected, RecordingReporter.SECURITY_PROTOCOL_BY_CLIENT_ID);
    }

    @Test
    void build_shouldReject_whenConnectTimeoutExceedsBound() {
        KafkaConfig.Builder builder = NxKafka.configure()
                .brokers("localhost:19999")
                .connectTimeout(2, TimeUnit.MINUTES)
                .reconnect(false);

        assertThrows(KafkaException.class, builder::build);
    }

    @Test
    void build_shouldReject_whenReconnectIntervalExceedsBound() {
        KafkaConfig.Builder builder = NxKafka.configure()
                .brokers("localhost:19999")
                .connectTimeout(1, TimeUnit.SECONDS)
                .reconnectInterval(10, TimeUnit.MINUTES)
                .reconnect(false);

        assertThrows(KafkaException.class, builder::build);
    }

    /** Instantiated by the Kafka clients with the configuration each of them was built from. */
    public static class RecordingReporter implements MetricsReporter {

        static final Map<Object, Object> SECURITY_PROTOCOL_BY_CLIENT_ID = new ConcurrentHashMap<>();

        @Override
        public void configure(Map<String, ?> configs) {
            SECURITY_PROTOCOL_BY_CLIENT_ID.put(configs.get("client.id"), configs.get("security.protocol"));
        }

        @Override
        public void init(List<KafkaMetric> metrics) {}

        @Override
        public void metricChange(KafkaMetric metric) {}

        @Override
        public void metricRemoval(KafkaMetric metric) {}

        @Override
        public void close() {}
    }
}
