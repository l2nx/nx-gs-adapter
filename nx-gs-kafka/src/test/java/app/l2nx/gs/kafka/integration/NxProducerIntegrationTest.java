package app.l2nx.gs.kafka.integration;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.kafka.KafkaException;
import app.l2nx.gs.kafka.NxKafka;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class NxProducerIntegrationTest {

    @Container
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.7.0");

    @AfterEach
    void tearDown() {
        try {
            NxKafka.instance().shutdown();
        } catch (KafkaException ignored) {
        }
    }

    @Test
    void send_shouldDeliverMessage_fireAndForget() throws Exception {
        String topic = "test.fire-and-forget";
        NxKafka kafka = buildKafka("test-producer-ff");

        kafka.send(topic, new TestEvent("player1", 42));

        ConsumerRecord<String, byte[]> record = consumeOne(topic, "group-ff");
        assertNotNull(record);
        String json = new String(record.value(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"name\":\"player1\""));
        assertTrue(json.contains("\"score\":42"));
    }

    @Test
    void send_shouldInvokeCallback_withMetadata() throws Exception {
        String topic = "test.callback";
        NxKafka kafka = buildKafka("test-producer-cb");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RecordMetadata> metadataRef = new AtomicReference<>();
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        kafka.send(topic, new TestEvent("player2", 99), (metadata, exception) -> {
            metadataRef.set(metadata);
            exceptionRef.set(exception);
            latch.countDown();
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Callback was not invoked");
        assertNotNull(metadataRef.get());
        assertNull(exceptionRef.get());
        assertEquals(topic, metadataRef.get().topic());
    }

    @Test
    void send_shouldReportErrorToCallback_whenBrokerUnavailable() throws Exception {
        NxKafka kafka = NxKafka.configure()
                .brokers("localhost:19999")
                .clientId("test-producer-unavail")
                .connectTimeout(2, TimeUnit.SECONDS)
                .reconnect(false)
                .property("max.block.ms", 1000)
                .property("delivery.timeout.ms", 2000)
                .property("request.timeout.ms", 1000)
                .property("linger.ms", 0)
                .build();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        assertDoesNotThrow(() -> kafka.send("test.unavail", new TestEvent("p", 1), (metadata, exception) -> {
            exceptionRef.set(exception);
            latch.countDown();
        }));

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Callback was not invoked");
        assertInstanceOf(TimeoutException.class, exceptionRef.get());
    }

    @Test
    void send_shouldNotReachProducer_afterShutdown() {
        SentTopics.TOPICS.clear();
        NxKafka kafka = NxKafka.configure()
                .brokers(KAFKA.getBootstrapServers())
                .clientId("test-producer-shut")
                .reconnect(false)
                .property("interceptor.classes", SentTopics.class.getName())
                .build();
        kafka.send("test.open", new TestEvent("p", 1));
        kafka.shutdown();

        assertDoesNotThrow(() -> kafka.send("test.closed", new TestEvent("p", 1)));

        assertEquals(Collections.singletonList("test.open"), SentTopics.TOPICS);
    }

    @Test
    void send_shouldInvokeCallbackWithError_afterShutdown() throws Exception {
        NxKafka kafka = buildKafka("test-producer-shut-cb");
        kafka.shutdown();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        kafka.send("test.closed", new TestEvent("p", 1), (metadata, exception) -> {
            exceptionRef.set(exception);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Callback was not invoked");
        assertNotNull(exceptionRef.get());
        assertTrue(exceptionRef.get().getMessage().contains("shut down"));
    }

    private NxKafka buildKafka(String clientId) {
        return NxKafka.configure()
                .brokers(KAFKA.getBootstrapServers())
                .clientId(clientId)
                .reconnect(false)
                .build();
    }

    private ConsumerRecord<String, byte[]> consumeOne(String topic, String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, byte[]> consumer =
                new KafkaConsumer<>(props, new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(Collections.singletonList(topic));
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(10));
            if (records.isEmpty()) {
                return null;
            }
            return records.iterator().next();
        }
    }

    /** Sees every record handed to the producer, also after the producer is closed. */
    public static class SentTopics implements ProducerInterceptor<byte[], Object> {

        static final List<String> TOPICS = new CopyOnWriteArrayList<>();

        @Override
        public ProducerRecord<byte[], Object> onSend(ProducerRecord<byte[], Object> record) {
            TOPICS.add(record.topic());
            return record;
        }

        @Override
        public void onAcknowledgement(RecordMetadata metadata, Exception exception) {}

        @Override
        public void close() {}

        @Override
        public void configure(Map<String, ?> configs) {}
    }

    static class TestEvent {
        final String name;
        final int score;

        TestEvent(String name, int score) {
            this.name = name;
            this.score = score;
        }
    }
}
