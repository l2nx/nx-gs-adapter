package app.l2nx.gs.adapter.api.domain.item;

import app.l2nx.gs.adapter.api.Vocabularies;
import org.jspecify.annotations.Nullable;

/**
 * When a skill of an item or item option is in effect. Unprefixed constants are grants held while the condition lasts;
 * {@code ON_} constants are one-time casts. A host sets one only when its engine token means exactly that, keeping the
 * token in {@code typeRaw}.
 */
public enum ItemSkillType {
    EQUIPPED(false, false),

    EQUIPPED_ENCHANT(false, true),

    EQUIPPED_BLESSED(false, false),

    ON_EQUIP(false, false),

    ON_UNEQUIP(false, false),

    ON_USE(false, false),

    /**
     * Cast on a non-critical normal-attack hit and on every physical skill of the owner, once per affected target; a
     * normal-attack hit fires this or {@link #ON_NORMAL_ATTACK_CRITICAL_HIT}, never both.
     */
    ON_PHYSICAL_HIT(true, false),

    /** Cast on a critical normal-attack hit; a critical hit of a skill does not fire it. */
    ON_NORMAL_ATTACK_CRITICAL_HIT(true, false),

    /** Cast when a magic skill of the owner lands on a target, once per affected target. */
    ON_MAGIC_SKILL_CAST(true, false);

    private final boolean chanceEvent;
    private final boolean enchantGated;

    ItemSkillType(boolean chanceEvent, boolean enchantGated) {
        this.chanceEvent = chanceEvent;
        this.enchantGated = enchantGated;
    }

    /** {@code true} for an event cast with a chance: {@code chancePercent} is set exactly for these. */
    public boolean chanceEvent() {
        return chanceEvent;
    }

    /** {@code true} for a grant held from an enchant threshold: {@code minEnchantLevel} is set exactly for it. */
    public boolean enchantGated() {
        return enchantGated;
    }

    public static @Nullable ItemSkillType fromToken(@Nullable String token) {
        return Vocabularies.fromToken(ItemSkillType.class, token);
    }
}
