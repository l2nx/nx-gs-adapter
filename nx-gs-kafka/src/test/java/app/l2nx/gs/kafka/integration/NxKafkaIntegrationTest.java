package app.l2nx.gs.kafka.integration;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.kafka.KafkaException;
import app.l2nx.gs.kafka.KafkaState;
import app.l2nx.gs.kafka.NxKafka;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class NxKafkaIntegrationTest {

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
    void shutdown_shouldWaitNoLongerThanProducerCloseTimeout_whenBrokerHangs() throws Exception {
        NxKafka kafka = NxKafka.configure()
                .brokers(KAFKA.getBootstrapServers())
                .clientId("test-close-timeout")
                .reconnect(false)
                .producerCloseTimeout(Duration.ofSeconds(2))
                .build();
        CountDownLatch delivered = new CountDownLatch(1);
        kafka.send("test.close-timeout", "warm-up", (metadata, exception) -> delivered.countDown());
        assertTrue(delivered.await(15, TimeUnit.SECONDS), "the topic metadata must be known before the broker hangs");

        AtomicReference<Exception> pendingFailure = new AtomicReference<>();
        long elapsedMs;
        DockerClientFactory.instance()
                .client()
                .pauseContainerCmd(KAFKA.getContainerId())
                .exec();
        try {
            kafka.send("test.close-timeout", "pending", (metadata, exception) -> pendingFailure.set(exception));
            long startedNanos = System.nanoTime();
            kafka.shutdown();
            elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        } finally {
            DockerClientFactory.instance()
                    .client()
                    .unpauseContainerCmd(KAFKA.getContainerId())
                    .exec();
        }

        assertNotNull(pendingFailure.get(), "the pending record must be dropped by the forced close");
        assertTrue(elapsedMs < 8000L, "shutdown took " + elapsedMs + "ms with a 2s close timeout");
    }

    @Test
    void healthCheck_shouldDetectDisconnectAndReconnect() throws InterruptedException {
        NxKafka kafka = NxKafka.configure()
                .brokers(KAFKA.getBootstrapServers())
                .clientId("test-health")
                .reconnect(true)
                .reconnectInterval(2, TimeUnit.SECONDS)
                .build();

        assertTrue(kafka.isConnected());

        DockerClientFactory.instance()
                .client()
                .pauseContainerCmd(KAFKA.getContainerId())
                .exec();

        try {
            awaitState(KafkaState.DISCONNECTED, 15000);
            assertFalse(kafka.isConnected());
        } finally {
            DockerClientFactory.instance()
                    .client()
                    .unpauseContainerCmd(KAFKA.getContainerId())
                    .exec();
        }

        awaitState(KafkaState.CONNECTED, 15000);
        assertTrue(kafka.isConnected());
    }

    private void awaitState(KafkaState expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (NxKafka.instance().state() == expected) {
                return;
            }
            Thread.sleep(500);
        }
        assertEquals(expected, NxKafka.instance().state(), "Timed out waiting for state " + expected);
    }
}
