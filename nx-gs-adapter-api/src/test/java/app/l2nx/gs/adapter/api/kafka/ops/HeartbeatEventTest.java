package app.l2nx.gs.adapter.api.kafka.ops;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.kafka.ops.model.ModuleStatus;
import app.l2nx.gs.adapter.api.kafka.ops.model.PoolStats;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeartbeatEventTest {

    @Test
    void builder_shouldMapEachFieldToConstructorPosition() {
        ModuleStatus a = ModuleStatus.builder()
                .name("a")
                .state("ACTIVE")
                .stats(ModuleStatus.Stats.builder()
                        .pool(new PoolStats(1, 3, 4, null))
                        .build())
                .build();
        ModuleStatus b = ModuleStatus.builder().name("b").state("DEGRADED").build();

        HeartbeatEvent event = HeartbeatEvent.builder()
                .tenantId("tenant-id")
                .tenantSlug("tenant-slug")
                .serverId("server-id")
                .serverSlug("server-slug")
                .serverName("Server Name")
                .adapterVersion("0.1.0")
                .uptime(Duration.ofSeconds(123))
                .enabledModules(Arrays.asList(a, b))
                .build();

        assertEquals("tenant-id", event.getTenantId());
        assertEquals("tenant-slug", event.getTenantSlug());
        assertEquals("server-id", event.getServerId());
        assertEquals("server-slug", event.getServerSlug());
        assertEquals("Server Name", event.getServerName());
        assertEquals("0.1.0", event.getAdapterVersion());
        assertEquals(Duration.ofSeconds(123), event.getUptime());
        assertEquals(Arrays.asList(a, b), event.getEnabledModules());
        assertEquals(event, event.toBuilder().build());
    }

    @Test
    void enabledModules_shouldDefaultToEmptyList_whenBuilderOmitsIt() {
        HeartbeatEvent event = HeartbeatEvent.builder()
                .tenantId("t")
                .tenantSlug("ts")
                .serverId("s")
                .serverSlug("ss")
                .serverName("sn")
                .adapterVersion("v")
                .uptime(Duration.ZERO)
                .build();

        assertEquals(Collections.emptyList(), event.getEnabledModules());
    }

    @Test
    void enabledModules_shouldBeAnUnmodifiableCopy() {
        ModuleStatus a = ModuleStatus.builder().name("a").state("ACTIVE").build();
        ModuleStatus b = ModuleStatus.builder().name("b").state("ACTIVE").build();
        java.util.ArrayList<ModuleStatus> source = new java.util.ArrayList<ModuleStatus>();
        source.add(a);

        HeartbeatEvent event = HeartbeatEvent.builder().enabledModules(source).build();
        source.add(b);

        List<ModuleStatus> seen = event.getEnabledModules();
        assertEquals(1, seen.size());
        assertTrue(seen.contains(a));
        assertThrows(UnsupportedOperationException.class, () -> seen.add(a));
    }
}
