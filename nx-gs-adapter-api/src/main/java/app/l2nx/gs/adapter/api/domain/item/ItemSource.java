package app.l2nx.gs.adapter.api.domain.item;

import app.l2nx.gs.adapter.api.Vocabularies;
import org.jspecify.annotations.Nullable;

/**
 * Why an item count of a character changed during a session; the wire value is the constant name. A host never
 * invents a token: what no value explains travels as an untracked row. Read leniently through {@link #fromToken}.
 * A row's delta sign is as the character sees it: {@code +} gained, {@code -} lost.
 */
public enum ItemSource {

    /** Picked up from the ground or looted from a kill ({@code +}, NPC on loot); thrown on the ground ({@code -}). */
    DROP,

    SWEEP,

    FISHING,

    QUEST,

    REWARD,

    CRAFT,

    CONSUME,

    /** Scrolls and stones spent, an item lost to a failed enchant ({@code -}); what the attempt returned ({@code +}). */
    ENCHANT,

    CRYSTALLIZE,

    /** Dropped on death; the row names the killer when known. */
    DEATH_DROP,

    /** A fee the game charged: teleport, service, tax. The fee of a private store sale is not one. */
    FEE,

    NPC_BUY,

    NPC_SELL,

    MULTISELL,

    TRADE,

    PRIVATE_STORE,

    MAIL,

    /** Loot handed out by the party rule; names the member when another member's kill or pickup gave it. */
    PARTY_DISTRIBUTE,

    WAREHOUSE,

    PET,

    FREIGHT,

    ADMIN,

    DONATE,

    PLATFORM;

    public static @Nullable ItemSource fromToken(@Nullable String token) {
        return Vocabularies.fromToken(ItemSource.class, token);
    }
}
