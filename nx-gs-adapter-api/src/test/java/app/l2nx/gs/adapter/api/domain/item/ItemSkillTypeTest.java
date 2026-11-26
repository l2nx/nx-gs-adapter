package app.l2nx.gs.adapter.api.domain.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ItemSkillTypeTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(ItemSkillType.class)
    void fromToken_shouldReadEveryConstantName(ItemSkillType type) {
        assertEquals(type, ItemSkillType.fromToken(type.name()));
    }

    @ParameterizedTest(name = "token={0}")
    @NullSource
    @ValueSource(strings = {"", "UNKNOWN", "equipped"})
    void fromToken_shouldBeNull_whenTokenIsUnknown(String token) {
        assertNull(ItemSkillType.fromToken(token));
    }

    @Test
    void values_shouldBeTheWireVocabulary() {
        List<String> expected = Arrays.asList(
                "EQUIPPED",
                "EQUIPPED_ENCHANT",
                "EQUIPPED_BLESSED",
                "ON_EQUIP",
                "ON_UNEQUIP",
                "ON_USE",
                "ON_PHYSICAL_HIT",
                "ON_NORMAL_ATTACK_CRITICAL_HIT",
                "ON_MAGIC_SKILL_CAST");
        List<String> actual = new ArrayList<String>();
        for (ItemSkillType type : ItemSkillType.values()) {
            actual.add(type.name());
        }

        assertEquals(expected, actual);
    }

    @Test
    void chanceEvent_shouldBeTrue_onlyForTheThreeChanceEvents() {
        EnumSet<ItemSkillType> expected = EnumSet.of(
                ItemSkillType.ON_PHYSICAL_HIT,
                ItemSkillType.ON_NORMAL_ATTACK_CRITICAL_HIT,
                ItemSkillType.ON_MAGIC_SKILL_CAST);
        EnumSet<ItemSkillType> actual = EnumSet.noneOf(ItemSkillType.class);
        for (ItemSkillType type : ItemSkillType.values()) {
            if (type.chanceEvent()) {
                actual.add(type);
            }
        }

        assertEquals(expected, actual);
    }

    @Test
    void enchantGated_shouldBeTrue_onlyForEquippedEnchant() {
        EnumSet<ItemSkillType> actual = EnumSet.noneOf(ItemSkillType.class);
        for (ItemSkillType type : ItemSkillType.values()) {
            if (type.enchantGated()) {
                actual.add(type);
            }
        }

        assertEquals(EnumSet.of(ItemSkillType.EQUIPPED_ENCHANT), actual);
    }
}
