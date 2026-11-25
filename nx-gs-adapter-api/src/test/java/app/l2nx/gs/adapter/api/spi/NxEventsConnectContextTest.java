package app.l2nx.gs.adapter.api.spi;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.events.premiumpurchase.PremiumPurchaseEvent;
import app.l2nx.gs.adapter.api.rest.SyncTopics;
import app.l2nx.gs.adapter.api.spi.capability.NxEvents;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class NxEventsConnectContextTest {

    @Test
    void events_shouldDefaultToNoOp_whenBuilderOmits() {
        ConnectContext ctx = ConnectContext.builder()
                .tenantId(UUID.randomUUID())
                .tenantSlug("acme")
                .serverId(UUID.randomUUID())
                .serverSlug("primary")
                .serverName("Acme")
                .adapterVersion("0.13.0")
                .syncTopics(new SyncTopics(null, null, null, null))
                .build();

        assertNotNull(ctx.events());
        ctx.events().publish(stubPurchase());
    }

    @Test
    void events_shouldReturnProvidedImpl_whenBuilderSets() {
        AtomicReference<Object> seen = new AtomicReference<Object>();
        NxEvents capturing = new NxEvents() {
            @Override
            public void publish(Object event) {
                seen.set(event);
            }

            @Override
            public boolean flush(long timeoutMs) {
                return true;
            }
        };

        ConnectContext ctx = ConnectContext.builder()
                .tenantId(UUID.randomUUID())
                .tenantSlug("acme")
                .serverId(UUID.randomUUID())
                .serverSlug("primary")
                .serverName("Acme")
                .adapterVersion("0.13.0")
                .events(capturing)
                .build();

        PremiumPurchaseEvent event = stubPurchase();
        ctx.events().publish(event);

        assertSame(event, seen.get());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(ConnectContext.class)
                .withIgnoredFields(
                        "events", "commands", "io", "sync", "gameData", "appSessions", "appRules", "runtimeSync")
                .verify();
    }

    private static PremiumPurchaseEvent stubPurchase() {
        return PremiumPurchaseEvent.builder()
                .eventId(UUID.randomUUID())
                .characterId(42L)
                .build();
    }
}
