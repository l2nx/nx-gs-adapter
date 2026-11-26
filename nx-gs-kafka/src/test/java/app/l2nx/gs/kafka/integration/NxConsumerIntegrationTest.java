package app.l2nx.gs.kafka.integration;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.kafka.KafkaException;
import app.l2nx.gs.kafka.NxKafka;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class NxConsumerIntegrationTest {

    @Container
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.9.11");

    @AfterEach
    void tearDown() {
        try {
            NxKafka.instance().shutdown();
        } catch (KafkaException ignored) {
        }
    }

    @Test
    void subscribe_shouldReceiveAndDeserializeMessage() throws Exception {
        String topic = "test.consumer.basic";
        NxKafka kafka = buildKafka("test-consumer-basic");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TestEvent> received = new AtomicReference<>();

        kafka.subscribe(topic, "g-" + topic, TestEvent.class, event -> {
            received.set(event);
            latch.countDown();
        });

        publishJson(topic, "{\"name\":\"player1\",\"score\":42}");

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Message was not received");
        assertNotNull(received.get());
        assertEquals("player1", received.get().name);
        assertEquals(42, received.get().score);
    }

    @Test
    void subscribe_shouldReceiveMultipleMessages() throws Exception {
        String topic = "test.consumer.multi";
        NxKafka kafka = buildKafka("test-consumer-multi");

        CountDownLatch latch = new CountDownLatch(3);

        kafka.subscribe(topic, "g-" + topic, TestEvent.class, event -> latch.countDown());

        publishJson(topic, "{\"name\":\"a\",\"score\":1}");
        publishJson(topic, "{\"name\":\"b\",\"score\":2}");
        publishJson(topic, "{\"name\":\"c\",\"score\":3}");

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Not all messages were received");
    }

    @Test
    void subscribe_shouldThrow_whenAlreadySubscribed() {
        String topic = "test.consumer.dup";
        NxKafka kafka = buildKafka("test-consumer-dup");

        kafka.subscribe(topic, "g-" + topic, TestEvent.class, event -> {});

        assertThrows(KafkaException.class, () -> kafka.subscribe(topic, "g-" + topic, TestEvent.class, event -> {}));
    }

    @Test
    void unsubscribe_shouldStopReceiving() throws Exception {
        String topic = "test.consumer.unsub";
        NxKafka kafka = buildKafka("test-consumer-unsub");

        CountDownLatch latch = new CountDownLatch(1);
        List<String> seenBefore = new CopyOnWriteArrayList<>();

        kafka.subscribe(topic, "g-" + topic, TestEvent.class, event -> {
            seenBefore.add(event.name);
            latch.countDown();
        });

        publishJson(topic, "{\"name\":\"first\",\"score\":1}");
        assertTrue(latch.await(10, TimeUnit.SECONDS), "First message not received");

        kafka.unsubscribe(topic);

        // Published while nobody is subscribed: a consumer left running would take it before the resubscribe.
        publishJson(topic, "{\"name\":\"second\",\"score\":2}");
        CountDownLatch latch2 = new CountDownLatch(1);
        kafka.subscribe(topic, "g-" + topic, TestEvent.class, event -> {
            if ("second".equals(event.name)) {
                latch2.countDown();
            }
        });

        assertTrue(latch2.await(10, TimeUnit.SECONDS), "Second message not received after resubscribe");
        assertEquals(Collections.singletonList("first"), seenBefore);
    }

    @Test
    void subscribe_shouldHandleDeserializationError_withoutCrashing() throws Exception {
        String topic = "test.consumer.bad-json";
        NxKafka kafka = buildKafka("test-consumer-badjson");

        CountDownLatch latch = new CountDownLatch(1);

        kafka.subscribe(topic, "g-" + topic, TestEvent.class, event -> latch.countDown());

        publishJson(topic, "not-json");
        publishJson(topic, "{\"name\":\"valid\",\"score\":1}");

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Valid message not received after bad JSON");
    }

    @Test
    void shutdown_shouldStopAllConsumers() throws Exception {
        NxKafka kafka = buildKafka("test-consumer-shutdown");

        CountDownLatch latch = new CountDownLatch(1);
        List<String> seenBefore = new CopyOnWriteArrayList<>();
        kafka.subscribe("test.consumer.shut1", "g-shut", TestEvent.class, event -> {
            seenBefore.add(event.name);
            latch.countDown();
        });
        kafka.subscribe("test.consumer.shut2", "g-shut", TestEvent.class, event -> seenBefore.add(event.name));

        publishJson("test.consumer.shut1", "{\"name\":\"p\",\"score\":1}");
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertDoesNotThrow(kafka::shutdown);

        // Published after the shutdown: a consumer left running would take them before the new subscriptions.
        publishJson("test.consumer.shut1", "{\"name\":\"late1\",\"score\":2}");
        publishJson("test.consumer.shut2", "{\"name\":\"late2\",\"score\":3}");
        NxKafka next = buildKafka("test-consumer-shutdown-next");
        CountDownLatch late1 = new CountDownLatch(1);
        CountDownLatch late2 = new CountDownLatch(1);
        next.subscribe("test.consumer.shut1", "g-shut", TestEvent.class, event -> {
            if ("late1".equals(event.name)) {
                late1.countDown();
            }
        });
        next.subscribe("test.consumer.shut2", "g-shut", TestEvent.class, event -> {
            if ("late2".equals(event.name)) {
                late2.countDown();
            }
        });

        assertTrue(late1.await(15, TimeUnit.SECONDS), "Message of the first topic not received after shutdown");
        assertTrue(late2.await(15, TimeUnit.SECONDS), "Message of the second topic not received after shutdown");
        assertEquals(Collections.singletonList("p"), seenBefore);
    }

    private NxKafka buildKafka(String clientId) {
        return NxKafka.configure()
                .brokers(KAFKA.getBootstrapServers())
                .clientId(clientId)
                .reconnect(false)
                .build();
    }

    private void publishJson(String topic, String json) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());

        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(
                props, new StringSerializer(), new org.apache.kafka.common.serialization.ByteArraySerializer())) {
            producer.send(new ProducerRecord<>(topic, json.getBytes(StandardCharsets.UTF_8)));
            producer.flush();
        }
    }

    static class TestEvent {
        String name;
        int score;
    }
}
