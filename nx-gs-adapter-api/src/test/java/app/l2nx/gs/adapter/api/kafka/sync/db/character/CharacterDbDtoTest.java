package app.l2nx.gs.adapter.api.kafka.sync.db.character;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.character.CharacterRace;
import app.l2nx.gs.adapter.api.domain.character.CharacterSex;
import app.l2nx.gs.adapter.api.domain.character.clazz.CharacterClass;
import app.l2nx.gs.adapter.api.domain.character.clazz.CharacterClassKind;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CharacterDbDtoTest {

    private static final Instant DELETE_AT = Instant.parse("2026-06-01T12:00:00Z");

    private static List<CharacterClassDbDto> roster() {
        return Arrays.asList(
                CharacterClassDbDto.builder()
                        .classId(CharacterClass.HUMAN_FIGHTER)
                        .kind(CharacterClassKind.MAIN)
                        .level(85)
                        .exp(9_000L)
                        .sp(70L)
                        .build(),
                CharacterClassDbDto.builder()
                        .classId(CharacterClass.SOULTAKER)
                        .kind(CharacterClassKind.SUB)
                        .level(76)
                        .build());
    }

    private static List<CharacterInstanceCooldownDbDto> cooldowns() {
        return Collections.singletonList(CharacterInstanceCooldownDbDto.builder()
                .instanceId(42)
                .reentryAt(Instant.parse("2026-07-02T00:00:00Z"))
                .build());
    }

    private static List<CharacterLockDbDto> locks() {
        return Collections.singletonList(CharacterLockDbDto.builder()
                .lockType(WellKnownCharacterLockTypes.IP)
                .lockValue("127.0.0.1")
                .build());
    }

    private static CharacterDbDto.Builder fullyPopulated() {
        return CharacterDbDto.builder()
                .id(54321L)
                .name("Cliodhna")
                .accountName("kiryl@nexus")
                .title("Hellbound")
                .level(85)
                .display(CharacterDisplayDbDto.builder()
                        .race(CharacterRace.ELF)
                        .sex(CharacterSex.FEMALE)
                        .build())
                .classId(CharacterClass.EVA_SAINT)
                .baseClassId(CharacterClass.ELDER)
                .classes(roster())
                .clanId(909L)
                .pvpCounter(1200)
                .pkCounter(7)
                .karma(0)
                .noblesse(Boolean.TRUE)
                .scheduledDeletionAt(DELETE_AT)
                .online(Boolean.TRUE)
                .onlineTimeSeconds(86_400L)
                .hero(Boolean.TRUE)
                .expBlocked(Boolean.TRUE)
                .gearScore(1500)
                .fame(98_765L)
                .accessLevel("7")
                .instanceCooldowns(cooldowns())
                .locks(locks());
    }

    @Test
    void builder_shouldMapEachFieldToConstructorPosition() {
        CharacterDbDto ch = fullyPopulated().build();

        assertEquals(54321L, ch.getId());
        assertEquals("Cliodhna", ch.getName());
        assertEquals("kiryl@nexus", ch.getAccountName());
        assertEquals("Hellbound", ch.getTitle());
        assertEquals(Integer.valueOf(85), ch.getLevel());
        assertEquals(CharacterRace.ELF, ch.getDisplay().getRace());
        assertEquals(CharacterSex.FEMALE, ch.getDisplay().getSex());
        assertEquals(CharacterClass.EVA_SAINT, ch.getClassId());
        assertEquals(CharacterClass.ELDER, ch.getBaseClassId());
        assertEquals(roster(), ch.getClasses());
        assertEquals(Long.valueOf(909L), ch.getClanId());
        assertEquals(Integer.valueOf(1200), ch.getPvpCounter());
        assertEquals(Integer.valueOf(7), ch.getPkCounter());
        assertEquals(Integer.valueOf(0), ch.getKarma());
        assertEquals(Boolean.TRUE, ch.getNoblesse());
        assertEquals(DELETE_AT, ch.getScheduledDeletionAt());
        assertEquals(Boolean.TRUE, ch.getOnline());
        assertEquals(Long.valueOf(86_400L), ch.getOnlineTimeSeconds());
        assertEquals(Boolean.TRUE, ch.getHero());
        assertEquals(Boolean.TRUE, ch.getExpBlocked());
        assertEquals(Integer.valueOf(1500), ch.getGearScore());
        assertEquals(Long.valueOf(98_765L), ch.getFame());
        assertEquals("7", ch.getAccessLevel());
        assertEquals(cooldowns(), ch.getInstanceCooldowns());
        assertEquals(locks(), ch.getLocks());
    }

    private static CharacterDbDto withEmptyTitleAndNoClan() {
        return new CharacterDbDto(
                1L,
                "X",
                "acc",
                "",
                10,
                new CharacterDisplayDbDto(CharacterRace.HUMAN, CharacterSex.MALE, 1, 2, 3, 0x00FF00, null),
                CharacterClass.HUMAN_FIGHTER,
                CharacterClass.HUMAN_FIGHTER,
                Arrays.asList(
                        CharacterClassDbDto.builder()
                                .classId(CharacterClass.HUMAN_FIGHTER)
                                .kind(CharacterClassKind.MAIN)
                                .level(10)
                                .exp(1234L)
                                .sp(56L)
                                .build(),
                        CharacterClassDbDto.builder()
                                .classId(CharacterClass.SOULTAKER)
                                .kind(CharacterClassKind.SUB)
                                .level(76)
                                .build()),
                null,
                0,
                0,
                0,
                Boolean.FALSE,
                Instant.parse("2026-07-01T00:00:00Z"),
                Boolean.TRUE,
                86_400L,
                Boolean.TRUE,
                Boolean.TRUE,
                1500,
                98_765L,
                "7",
                cooldowns(),
                locks());
    }

    private static Stream<Arguments> roundTripped() {
        return Stream.of(
                Arguments.of(
                        fullyPopulated().build(),
                        "Hellbound",
                        CharacterClass.EVA_SAINT,
                        CharacterClass.ELDER,
                        909L,
                        Boolean.TRUE),
                Arguments.of(
                        fullyPopulated()
                                .classId(CharacterClass.SOULTAKER)
                                .baseClassId(CharacterClass.HUMAN_FIGHTER)
                                .build(),
                        "Hellbound",
                        CharacterClass.SOULTAKER,
                        CharacterClass.HUMAN_FIGHTER,
                        909L,
                        Boolean.TRUE),
                Arguments.of(
                        withEmptyTitleAndNoClan(),
                        "",
                        CharacterClass.HUMAN_FIGHTER,
                        CharacterClass.HUMAN_FIGHTER,
                        null,
                        Boolean.FALSE));
    }

    @ParameterizedTest
    @MethodSource("roundTripped")
    void toBuilder_shouldRoundtrip(
            CharacterDbDto original,
            String title,
            CharacterClass classId,
            CharacterClass baseClassId,
            Long clanId,
            Boolean noblesse) {
        CharacterDbDto copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertEquals(title, copy.getTitle());
        assertEquals(classId, copy.getClassId());
        assertEquals(baseClassId, copy.getBaseClassId());
        assertEquals(clanId, copy.getClanId());
        assertEquals(noblesse, copy.getNoblesse());
        assertEquals(2, copy.getClasses().size());
        assertEquals(CharacterClassKind.MAIN, copy.getClasses().get(0).getKind());
        assertEquals(CharacterClassKind.SUB, copy.getClasses().get(1).getKind());
    }

    @Test
    void allOptionalFields_shouldBeNullable_whenTenantOmitsThemButNameRequired() {
        CharacterDbDto ch = CharacterDbDto.builder().id(1L).name("OnlyName").build();

        assertEquals(1L, ch.getId());
        assertEquals("OnlyName", ch.getName());
        assertNull(ch.getAccountName());
        assertNull(ch.getTitle());
        assertNull(ch.getLevel());
        assertNull(ch.getDisplay());
        assertNull(ch.getClassId());
        assertNull(ch.getBaseClassId());
        assertNull(ch.getClasses());
        assertNull(ch.getClanId());
        assertNull(ch.getPvpCounter());
        assertNull(ch.getPkCounter());
        assertNull(ch.getKarma());
        assertNull(ch.getNoblesse());
        assertNull(ch.getScheduledDeletionAt());
        assertNull(ch.getOnline());
        assertNull(ch.getOnlineTimeSeconds());
        assertNull(ch.getHero());
        assertNull(ch.getExpBlocked());
        assertNull(ch.getGearScore());
        assertNull(ch.getFame());
        assertNull(ch.getAccessLevel());
        assertNull(ch.getInstanceCooldowns());
        assertNull(ch.getLocks());
    }

    @Test
    void build_shouldThrowNpe_whenNameIsNull() {
        CharacterDbDto.Builder b = CharacterDbDto.builder().id(1L);

        assertThrows(NullPointerException.class, b::build);
    }
}
