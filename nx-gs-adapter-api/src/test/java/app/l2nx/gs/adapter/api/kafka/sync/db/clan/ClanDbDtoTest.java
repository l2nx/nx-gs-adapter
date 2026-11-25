package app.l2nx.gs.adapter.api.kafka.sync.db.clan;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ClanDbDtoTest {

    @Test
    void builder_shouldMapEachFieldToConstructorPosition() {
        List<ClanSkillDbDto> skills = Arrays.asList(
                ClanSkillDbDto.builder().id(101).level(3).build(),
                ClanSkillDbDto.builder().id(202).level(7).build());

        ClanDbDto clan = ClanDbDto.builder()
                .id(12345L)
                .name("Hellbound")
                .level(8)
                .leaderId(67890L)
                .allyId(42L)
                .skills(skills)
                .icon(new byte[] {1, 2, 3})
                .build();

        assertEquals(12345L, clan.getId());
        assertEquals("Hellbound", clan.getName());
        assertEquals(Integer.valueOf(8), clan.getLevel());
        assertEquals(Long.valueOf(67890L), clan.getLeaderId());
        assertEquals(Long.valueOf(42L), clan.getAllyId());
        assertEquals(skills, clan.getSkills());
        assertArrayEquals(new byte[] {1, 2, 3}, clan.getIcon());
        assertEquals(clan, clan.toBuilder().build());
        assertEquals(101, clan.getSkills().get(0).getId());
        assertEquals(3, clan.getSkills().get(0).getLevel());
    }

    @Test
    void toBuilder_shouldRoundtrip_whenClanHasNoAlly() {
        List<ClanSkillDbDto> skills = Collections.singletonList(
                ClanSkillDbDto.builder().id(1).level(2).build());
        ClanDbDto original = ClanDbDto.builder()
                .id(1L)
                .name("X")
                .level(5)
                .leaderId(2L)
                .skills(skills)
                .icon(new byte[] {1, 2, 3})
                .build();

        ClanDbDto copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertNull(copy.getAllyId());
    }

    private static Stream<Arguments> withoutOptionalFields() {
        return Stream.of(
                Arguments.of(ClanDbDto.builder().id(1L).name("X").build(), null),
                Arguments.of(
                        ClanDbDto.builder()
                                .id(1L)
                                .name("LonelyClan")
                                .level(0)
                                .leaderId(null)
                                .allyId(null)
                                .build(),
                        0),
                Arguments.of(ClanDbDto.builder().id(1L).name("X").level(1).build(), 1));
    }

    @ParameterizedTest
    @MethodSource("withoutOptionalFields")
    void optionalFields_shouldBeNull_whenTenantOmitsThem(ClanDbDto clan, Integer level) {
        assertEquals(level, clan.getLevel());
        assertNull(clan.getLeaderId());
        assertNull(clan.getAllyId());
        assertNull(clan.getSkills());
        assertNull(clan.getIcon());
    }

    @Test
    void build_shouldThrowNpe_whenNameIsNull() {
        ClanDbDto.Builder b = ClanDbDto.builder().id(1L);

        assertThrows(NullPointerException.class, b::build);
    }

    @Test
    void skills_shouldBeEmptyList_whenTenantSyncsThemButClanHasNone() {
        ClanDbDto clan = ClanDbDto.builder()
                .id(1L)
                .name("X")
                .level(1)
                .skills(Collections.emptyList())
                .build();

        assertNotNull(clan.getSkills());
        assertTrue(clan.getSkills().isEmpty());
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(ClanDbDto.class).withNonnullFields("name").verify();
    }
}
