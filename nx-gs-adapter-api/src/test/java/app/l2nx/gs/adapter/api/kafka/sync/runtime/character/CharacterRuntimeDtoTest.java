package app.l2nx.gs.adapter.api.kafka.sync.runtime.character;

import static org.junit.jupiter.api.Assertions.*;

import app.l2nx.gs.adapter.api.domain.character.clazz.CharacterClass;
import app.l2nx.gs.adapter.api.domain.skill.effect.SkillEffectCategory;
import app.l2nx.gs.adapter.api.kafka.sync.runtime.character.model.CharacterEffect;
import app.l2nx.gs.adapter.api.kafka.sync.runtime.character.model.CharacterEffectOffline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CharacterRuntimeDtoTest {

    private static CharacterEffect haste() {
        return CharacterEffect.builder()
                .skillId(1086)
                .skillLevel(2)
                .category(SkillEffectCategory.BUFF)
                .remainingSec(1180)
                .offline(CharacterEffectOffline.FROZEN)
                .build();
    }

    @Test
    void builder_shouldPopulateOnlineLiveStateRow() {
        CharacterRuntimeDto dto = CharacterRuntimeDto.builder()
                .id(42L)
                .curHp(1234)
                .maxHp(2000)
                .x(100)
                .y(200)
                .z(-3000)
                .build();

        assertEquals(42L, dto.getId());
        assertEquals(Integer.valueOf(1234), dto.getCurHp());
        assertEquals(Integer.valueOf(2000), dto.getMaxHp());
        assertNull(dto.getOnline());
    }

    @Test
    void builder_shouldPopulateOfflineTombstone() {
        CharacterRuntimeDto dto =
                CharacterRuntimeDto.builder().id(42L).online(Boolean.FALSE).build();

        assertEquals(42L, dto.getId());
        assertEquals(Boolean.FALSE, dto.getOnline());
        assertNull(dto.getCurHp());
        assertNull(dto.getMaxHp());
        assertNull(dto.getX());
    }

    @Test
    void toBuilder_shouldRoundtripEveryField() {
        CharacterRuntimeDto original = new CharacterRuntimeDto(
                123L,
                100,
                200,
                50,
                100,
                25,
                50,
                1000,
                2000,
                500,
                600,
                -700,
                Boolean.TRUE,
                "attack",
                CharacterClass.SOULTAKER,
                76,
                4_500_000_000L,
                12_345L,
                80,
                100,
                12,
                50,
                45000,
                60000,
                Collections.singletonList(haste()),
                24,
                30);

        CharacterRuntimeDto actual = original.toBuilder().build();

        assertEquals(original, actual);
        assertEquals(123L, actual.getId());
        assertEquals(Integer.valueOf(100), actual.getCurHp());
        assertEquals(Integer.valueOf(200), actual.getMaxHp());
        assertEquals(Integer.valueOf(50), actual.getCurMp());
        assertEquals(Integer.valueOf(100), actual.getMaxMp());
        assertEquals(Integer.valueOf(25), actual.getCurCp());
        assertEquals(Integer.valueOf(50), actual.getMaxCp());
        assertEquals(Integer.valueOf(1000), actual.getCurVit());
        assertEquals(Integer.valueOf(2000), actual.getMaxVit());
        assertEquals(Integer.valueOf(500), actual.getX());
        assertEquals(Integer.valueOf(600), actual.getY());
        assertEquals(Integer.valueOf(-700), actual.getZ());
        assertEquals(Boolean.TRUE, actual.getOnline());
        assertEquals("attack", actual.getAiStatus());
        assertEquals(CharacterClass.SOULTAKER, actual.getClassId());
        assertEquals(Integer.valueOf(76), actual.getLevel());
        assertEquals(Long.valueOf(4_500_000_000L), actual.getExp());
        assertEquals(Long.valueOf(12_345L), actual.getSp());
        assertEquals(Integer.valueOf(80), actual.getCurInventorySlots());
        assertEquals(Integer.valueOf(100), actual.getMaxInventorySlots());
        assertEquals(Integer.valueOf(12), actual.getCurQuestInventorySlots());
        assertEquals(Integer.valueOf(50), actual.getMaxQuestInventorySlots());
        assertEquals(Integer.valueOf(45000), actual.getCurWeight());
        assertEquals(Integer.valueOf(60000), actual.getMaxWeight());
        assertEquals(Collections.singletonList(haste()), actual.getEffects());
        assertEquals(Integer.valueOf(24), actual.getPrivateStoreSellSlots());
        assertEquals(Integer.valueOf(30), actual.getPrivateStoreBuySlots());
    }

    @Test
    void carriesState_shouldBeFalse_forTheOfflineTombstone() {
        CharacterRuntimeDto tombstone =
                CharacterRuntimeDto.builder().id(42L).online(Boolean.FALSE).build();

        assertFalse(tombstone.carriesState());
    }

    @Test
    void carriesState_shouldBeTrue_forAnOfflineTraderTick() {
        CharacterRuntimeDto trader = CharacterRuntimeDto.builder()
                .id(42L)
                .online(Boolean.FALSE)
                .curHp(3000)
                .build();

        assertTrue(trader.carriesState());
    }

    @ParameterizedTest(name = "{0} alone marks the row as state-bearing")
    @MethodSource("singleFieldRows")
    void carriesState_shouldBeTrue_whenAnySingleObservableFieldIsSet(String field, CharacterRuntimeDto dto) {
        assertTrue(dto.carriesState(), field);
    }

    static Stream<Arguments> singleFieldRows() {
        return Stream.of(
                Arguments.of(
                        "curHp", CharacterRuntimeDto.builder().id(1L).curHp(1).build()),
                Arguments.of(
                        "maxHp", CharacterRuntimeDto.builder().id(1L).maxHp(1).build()),
                Arguments.of(
                        "curMp", CharacterRuntimeDto.builder().id(1L).curMp(1).build()),
                Arguments.of(
                        "maxMp", CharacterRuntimeDto.builder().id(1L).maxMp(1).build()),
                Arguments.of(
                        "curCp", CharacterRuntimeDto.builder().id(1L).curCp(1).build()),
                Arguments.of(
                        "maxCp", CharacterRuntimeDto.builder().id(1L).maxCp(1).build()),
                Arguments.of(
                        "curVit", CharacterRuntimeDto.builder().id(1L).curVit(1).build()),
                Arguments.of(
                        "maxVit", CharacterRuntimeDto.builder().id(1L).maxVit(1).build()),
                Arguments.of("x", CharacterRuntimeDto.builder().id(1L).x(1).build()),
                Arguments.of("y", CharacterRuntimeDto.builder().id(1L).y(1).build()),
                Arguments.of("z", CharacterRuntimeDto.builder().id(1L).z(1).build()),
                Arguments.of(
                        "aiStatus",
                        CharacterRuntimeDto.builder().id(1L).aiStatus("idle").build()),
                Arguments.of(
                        "classId",
                        CharacterRuntimeDto.builder()
                                .id(1L)
                                .classId(CharacterClass.DUELIST)
                                .build()),
                Arguments.of(
                        "level", CharacterRuntimeDto.builder().id(1L).level(1).build()),
                Arguments.of("exp", CharacterRuntimeDto.builder().id(1L).exp(1L).build()),
                Arguments.of("sp", CharacterRuntimeDto.builder().id(1L).sp(1L).build()),
                Arguments.of(
                        "curInventorySlots",
                        CharacterRuntimeDto.builder()
                                .id(1L)
                                .curInventorySlots(1)
                                .build()),
                Arguments.of(
                        "maxInventorySlots",
                        CharacterRuntimeDto.builder()
                                .id(1L)
                                .maxInventorySlots(1)
                                .build()),
                Arguments.of(
                        "curQuestInventorySlots",
                        CharacterRuntimeDto.builder()
                                .id(1L)
                                .curQuestInventorySlots(1)
                                .build()),
                Arguments.of(
                        "maxQuestInventorySlots",
                        CharacterRuntimeDto.builder()
                                .id(1L)
                                .maxQuestInventorySlots(1)
                                .build()),
                Arguments.of(
                        "curWeight",
                        CharacterRuntimeDto.builder().id(1L).curWeight(1).build()),
                Arguments.of(
                        "maxWeight",
                        CharacterRuntimeDto.builder().id(1L).maxWeight(1).build()),
                Arguments.of(
                        "effects",
                        CharacterRuntimeDto.builder()
                                .id(1L)
                                .effects(Collections.<CharacterEffect>emptyList())
                                .build()),
                Arguments.of(
                        "privateStoreSellSlots",
                        CharacterRuntimeDto.builder()
                                .id(1L)
                                .privateStoreSellSlots(12)
                                .build()),
                Arguments.of(
                        "privateStoreBuySlots",
                        CharacterRuntimeDto.builder()
                                .id(1L)
                                .privateStoreBuySlots(9)
                                .build()));
    }

    @Test
    void build_shouldLeaveEveryOptionalFieldNull_whenOmitted() {
        CharacterRuntimeDto dto = CharacterRuntimeDto.builder().id(123L).build();

        assertEquals(123L, dto.getId());
        assertNull(dto.getCurHp());
        assertNull(dto.getMaxHp());
        assertNull(dto.getCurMp());
        assertNull(dto.getMaxMp());
        assertNull(dto.getCurCp());
        assertNull(dto.getMaxCp());
        assertNull(dto.getCurVit());
        assertNull(dto.getMaxVit());
        assertNull(dto.getX());
        assertNull(dto.getY());
        assertNull(dto.getZ());
        assertNull(dto.getOnline());
        assertNull(dto.getAiStatus());
        assertNull(dto.getClassId());
        assertNull(dto.getLevel());
        assertNull(dto.getExp());
        assertNull(dto.getSp());
        assertNull(dto.getCurInventorySlots());
        assertNull(dto.getMaxInventorySlots());
        assertNull(dto.getCurQuestInventorySlots());
        assertNull(dto.getMaxQuestInventorySlots());
        assertNull(dto.getCurWeight());
        assertNull(dto.getMaxWeight());
        assertNull(dto.getEffects());
        assertNull(dto.getPrivateStoreSellSlots());
        assertNull(dto.getPrivateStoreBuySlots());
    }

    @Test
    void privateStoreSlots_shouldBeKeptIndependentlyOfEachOther() {
        CharacterRuntimeDto sellOnly =
                CharacterRuntimeDto.builder().id(1L).privateStoreSellSlots(12).build();
        CharacterRuntimeDto both = CharacterRuntimeDto.builder()
                .id(1L)
                .privateStoreSellSlots(4)
                .privateStoreBuySlots(9)
                .build();

        assertEquals(Integer.valueOf(12), sellOnly.getPrivateStoreSellSlots());
        assertNull(sellOnly.getPrivateStoreBuySlots());
        assertEquals(sellOnly, sellOnly.toBuilder().build());
        assertEquals(Integer.valueOf(4), both.getPrivateStoreSellSlots());
        assertEquals(Integer.valueOf(9), both.getPrivateStoreBuySlots());
        assertNotEquals(both, both.toBuilder().privateStoreBuySlots(4).build());
        assertEquals(both, both.toBuilder().build());
    }

    @Test
    void class_shouldExposeExactlyOneConstructor() {
        assertEquals(1, CharacterRuntimeDto.class.getDeclaredConstructors().length);
    }

    @Test
    void equals_shouldFollowTheContract() {
        EqualsVerifier.forClass(CharacterRuntimeDto.class).verify();
    }

    @Test
    void equals_shouldDiffer_whenOneSideHasNoValue() {
        CharacterRuntimeDto empty = CharacterRuntimeDto.builder().id(1L).build();

        assertNotEquals(empty, empty.toBuilder().online(Boolean.TRUE).build());
        assertNotEquals(empty, empty.toBuilder().exp(4_500_000_000L).build());
        assertNotEquals(empty, empty.toBuilder().privateStoreSellSlots(12).build());
        assertNotEquals(
                empty,
                empty.toBuilder()
                        .classId(CharacterClass.SOULTAKER)
                        .level(76)
                        .exp(4_500_000_000L)
                        .sp(12_345L)
                        .build());
    }

    @Test
    void effects_shouldBeUnmodifiableAndDefensivelyCopied() {
        List<CharacterEffect> source = new ArrayList<CharacterEffect>(Collections.singletonList(haste()));
        CharacterRuntimeDto dto =
                CharacterRuntimeDto.builder().id(1L).effects(source).build();

        source.clear();

        assertEquals(Collections.singletonList(haste()), dto.getEffects());
        assertThrows(UnsupportedOperationException.class, () -> dto.getEffects().add(haste()));
    }
}
