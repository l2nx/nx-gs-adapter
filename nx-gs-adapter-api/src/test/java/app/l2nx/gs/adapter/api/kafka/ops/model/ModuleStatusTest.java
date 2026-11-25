package app.l2nx.gs.adapter.api.kafka.ops.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ModuleStatusTest {

    @Test
    void empty_shouldHaveNoSlots() {
        assertFalse(ModuleStatus.Stats.empty().getPool().isPresent());
        assertFalse(ModuleStatus.Stats.empty().getEntities().isPresent());
    }

    @Test
    void entities_shouldBeAnUnmodifiableCopy() {
        EntityStats a =
                EntityStats.builder().name("clan").state(EntityState.HEALTHY).build();
        EntityStats b = EntityStats.builder()
                .name("character")
                .state(EntityState.HEALTHY)
                .build();
        java.util.ArrayList<EntityStats> source = new java.util.ArrayList<EntityStats>();
        source.add(a);

        ModuleStatus.Stats stats = ModuleStatus.Stats.builder().entities(source).build();
        source.add(b);

        List<EntityStats> seen = stats.getEntities().get();
        assertEquals(Collections.singletonList(a), seen);
        assertThrows(UnsupportedOperationException.class, () -> seen.add(a));
    }

    @Test
    void constructor_shouldDefaultStatsToEmpty_whenNullPassed() {
        ModuleStatus status = new ModuleStatus("db-sync", "ACTIVE", null);

        assertSame(ModuleStatus.Stats.empty(), status.getStats());
    }

    @Test
    void builder_shouldMapEachFieldToConstructorPosition() {
        PoolStats pool = new PoolStats(1, 3, 4, null);
        EntityStats clan = EntityStats.builder()
                .name("clan")
                .state(EntityState.HEALTHY)
                .rowCount(1069L)
                .build();
        EntityStats character = EntityStats.builder()
                .name("character")
                .state(EntityState.DEGRADED)
                .rowCount(152_088L)
                .build();
        ModuleStatus status = ModuleStatus.builder()
                .name("db-sync")
                .state("ACTIVE")
                .stats(ModuleStatus.Stats.builder()
                        .pool(pool)
                        .entities(Arrays.asList(clan, character))
                        .build())
                .build();

        assertEquals("db-sync", status.getName());
        assertEquals("ACTIVE", status.getState());
        assertEquals(Optional.of(pool), status.getStats().getPool());
        assertEquals(
                Optional.of(Arrays.asList(clan, character)), status.getStats().getEntities());
        assertEquals(status, status.toBuilder().build());
    }

    private static Stream<ModuleStatus.Stats> partialStats() {
        EntityStats clan = EntityStats.builder()
                .name("clan")
                .state(EntityState.HEALTHY)
                .rowCount(3L)
                .build();
        return Stream.of(
                ModuleStatus.Stats.builder().pool(new PoolStats(1, 3, 4, null)).build(),
                ModuleStatus.Stats.builder()
                        .entities(Collections.singletonList(clan))
                        .build(),
                ModuleStatus.Stats.builder()
                        .pool(new PoolStats(0, 4, 4, null))
                        .entities(Collections.singletonList(clan))
                        .build());
    }

    @ParameterizedTest
    @MethodSource("partialStats")
    void toBuilder_shouldRoundtrip(ModuleStatus.Stats stats) {
        ModuleStatus original = ModuleStatus.builder()
                .name("db-sync")
                .state("DEGRADED")
                .stats(stats)
                .build();

        assertEquals(original, original.toBuilder().build());
        assertEquals(stats, original.getStats().toBuilder().build());
    }
}
