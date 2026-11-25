package app.l2nx.gs.adapter.api.spi;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.rest.SyncTopics;
import app.l2nx.gs.adapter.api.spi.capability.NxAppRules;
import app.l2nx.gs.adapter.api.spi.capability.NxRuntimeSync;
import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import app.l2nx.gs.adapter.api.spi.model.session.ClientLoginOutcome;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConnectContextTest {

    @Test
    void toBuilder_shouldRoundtripEveryField() {
        UUID tenantId = UUID.randomUUID();
        UUID serverId = UUID.randomUUID();
        SyncTopics syncTopics = SyncTopics.builder()
                .db(Collections.singletonMap("clan", "acme.gs.sync.db.clan"))
                .build();

        ConnectContext ctx = ConnectContext.builder()
                .tenantId(tenantId)
                .tenantSlug("acme")
                .serverId(serverId)
                .serverSlug("primary")
                .serverName("Acme Primary")
                .adapterVersion("0.1.0")
                .syncTopics(syncTopics)
                .build();

        assertEquals(tenantId, ctx.getTenantId());
        assertEquals("acme", ctx.getTenantSlug());
        assertEquals(serverId, ctx.getServerId());
        assertEquals("primary", ctx.getServerSlug());
        assertEquals("Acme Primary", ctx.getServerName());
        assertEquals("0.1.0", ctx.getAdapterVersion());
        assertEquals(syncTopics, ctx.getSyncTopics());
        assertEquals(ctx, ctx.toBuilder().build());
    }

    @Test
    void appSessions_shouldDefaultToNoOp_whenBuilderOmitsIt() {
        ConnectContext ctx = ConnectContext.builder().build();

        assertFalse(ctx.appSessions().findActive(1).isPresent());
        assertEquals(
                ClientLoginOutcome.Status.NONE,
                ctx.appSessions().onClientLogin(1).getStatus());
    }

    @Test
    void runtimeSync_shouldNeverFire_whenBuilderOmitsIt() {
        ConnectContext ctx = ConnectContext.builder().build();
        boolean[] fired = {false};

        ctx.runtimeSync().onHostStarted(() -> fired[0] = true);
        ctx.runtimeSync().hostStarted();

        assertFalse(fired[0]);
    }

    @Test
    void runtimeSync_shouldKeepTheWiredInstance_whenCopiedThroughToBuilder() {
        NxRuntimeSync wired = new NxRuntimeSync() {
            @Override
            public void hostStarted() {}

            @Override
            public void onHostStarted(Runnable callback) {}
        };

        ConnectContext ctx =
                ConnectContext.builder().runtimeSync(wired).build().toBuilder().build();

        assertSame(wired, ctx.runtimeSync());
    }

    @Test
    void appRules_shouldDefaultToTheDefaults_whenBuilderOmitsIt() {
        ConnectContext ctx = ConnectContext.builder().build();

        assertEquals(AppRules.defaults(), ctx.appRules().current());
    }

    @Test
    void appRules_shouldKeepTheWiredInstance_whenCopiedThroughToBuilder() {
        NxAppRules wired = () -> AppRules.builder().returnThresholdMinutes(5).build();

        ConnectContext ctx =
                ConnectContext.builder().appRules(wired).build().toBuilder().build();

        assertSame(wired, ctx.appRules());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buildersWithoutSyncTopics")
    void syncTopics_shouldDefaultToEmpty_whenBuilderOmitsItOrSetsNull(String name, ConnectContext.Builder builder) {
        ConnectContext ctx = builder.build();

        assertNotNull(ctx.getSyncTopics());
        assertTrue(ctx.getSyncTopics().getDb().isEmpty());
        assertTrue(ctx.getSyncTopics().getRuntime().isEmpty());
        assertTrue(ctx.getSyncTopics().getGd().isEmpty());
    }

    static Stream<Arguments> buildersWithoutSyncTopics() {
        return Stream.of(
                Arguments.of("omitted", ConnectContext.builder()),
                Arguments.of("null", ConnectContext.builder().syncTopics(null)));
    }

    @Test
    void syncTopics_shouldExposeNamespaces_whenBuilderProvidesIt() {
        SyncTopics topics = SyncTopics.builder()
                .db(Collections.singletonMap("clan", "acme.gs.sync.db.clan"))
                .runtime(Collections.singletonMap("character", "acme.gs.sync.runtime.character"))
                .build();

        ConnectContext ctx = ConnectContext.builder().syncTopics(topics).build();

        assertEquals("acme.gs.sync.db.clan", ctx.getSyncTopics().getDb().get("clan"));
        assertEquals(
                "acme.gs.sync.runtime.character",
                ctx.getSyncTopics().getRuntime().get("character"));
        assertTrue(ctx.getSyncTopics().getGd().isEmpty());
    }
}
