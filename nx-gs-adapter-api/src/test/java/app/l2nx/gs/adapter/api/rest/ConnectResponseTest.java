package app.l2nx.gs.adapter.api.rest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectResponseTest {

    @Test
    void topics_shouldDefaultToNull_whenBuilderOmitsThem() {
        ConnectResponse response = ConnectResponse.builder()
                .tenantId(UUID.randomUUID())
                .tenantSlug("acme")
                .serverId(UUID.randomUUID())
                .serverSlug("primary")
                .serverName("Acme Primary")
                .kafka(KafkaCredentials.builder().bootstrap("localhost:9092").build())
                .build();

        assertNull(response.getSyncTopics());
        assertNull(response.getHeartbeatTopic());
        assertNull(response.getMessagingTopics());
    }

    @Test
    void syncTopics_shouldExposeNamespaces_whenBuilderProvidesIt() {
        SyncTopics topics = SyncTopics.builder()
                .db(Collections.singletonMap("clan", "acme.gs.sync.db.clan"))
                .runtime(Collections.singletonMap("character", "acme.gs.sync.runtime.character"))
                .build();

        ConnectResponse response = ConnectResponse.builder().syncTopics(topics).build();

        assertEquals(topics, response.getSyncTopics());
        assertEquals("acme.gs.sync.db.clan", response.getSyncTopics().getDb().get("clan"));
        assertEquals(
                "acme.gs.sync.runtime.character",
                response.getSyncTopics().getRuntime().get("character"));
    }

    @Test
    void syncTopics_namespacesShouldBeUnmodifiable() {
        SyncTopics topics = SyncTopics.builder()
                .db(Collections.singletonMap("clan", "acme.gs.sync.db.clan"))
                .build();

        ConnectResponse response = ConnectResponse.builder().syncTopics(topics).build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> response.getSyncTopics().getDb().put("character", "x"));
    }

    @Test
    void toBuilder_shouldRoundtripEveryField() {
        UUID tenantId = UUID.randomUUID();
        UUID serverId = UUID.randomUUID();
        KafkaCredentials kafka =
                KafkaCredentials.builder().bootstrap("localhost:9092").build();
        SyncTopics syncTopics = SyncTopics.builder()
                .db(Collections.singletonMap("clan", "acme.gs.sync.db.clan"))
                .build();
        MessagingTopics messagingTopics = MessagingTopics.builder()
                .events(Collections.singletonMap("premiumpurchase", "acme.gs.events.premiumpurchase"))
                .build();
        ConnectResponse original = ConnectResponse.builder()
                .tenantId(tenantId)
                .tenantSlug("acme")
                .serverId(serverId)
                .serverSlug("primary")
                .serverName("Acme")
                .kafka(kafka)
                .heartbeatTopic("acme.gs.heartbeat")
                .syncTopics(syncTopics)
                .messagingTopics(messagingTopics)
                .build();

        ConnectResponse actual = original.toBuilder().build();

        assertEquals(original, actual);
        assertEquals(tenantId, actual.getTenantId());
        assertEquals("acme", actual.getTenantSlug());
        assertEquals(serverId, actual.getServerId());
        assertEquals("primary", actual.getServerSlug());
        assertEquals("Acme", actual.getServerName());
        assertEquals(kafka, actual.getKafka());
        assertEquals("acme.gs.heartbeat", actual.getHeartbeatTopic());
        assertEquals(syncTopics, actual.getSyncTopics());
        assertEquals(messagingTopics, actual.getMessagingTopics());
    }

    @Test
    void messagingTopics_shouldExposeEventsMap_whenBuilderProvidesIt() {
        ConnectResponse response = ConnectResponse.builder()
                .messagingTopics(MessagingTopics.builder()
                        .events(Collections.singletonMap("premiumpurchase", "acme.gs.events.premiumpurchase"))
                        .build())
                .build();

        assertEquals(
                "acme.gs.events.premiumpurchase",
                response.getMessagingTopics().getEvents().get("premiumpurchase"));
    }

    @Test
    void syncTopics_shouldExposeSettingsNamespace_andDefaultToEmpty() {
        SyncTopics withSettings = SyncTopics.builder()
                .settings(Collections.singletonMap("appsettings", "acme.gs.settings.sync"))
                .build();
        SyncTopics without = SyncTopics.builder().build();

        assertEquals("acme.gs.settings.sync", withSettings.getSettings().get("appsettings"));
        assertTrue(without.getSettings().isEmpty());
        assertNotEquals(withSettings, without);
        assertEquals(withSettings, withSettings.toBuilder().build());
    }
}
