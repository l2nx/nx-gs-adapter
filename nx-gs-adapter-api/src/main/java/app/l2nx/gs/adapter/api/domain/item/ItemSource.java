package app.l2nx.gs.adapter.api.domain.item;

import app.l2nx.gs.adapter.api.Vocabularies;
import org.jspecify.annotations.Nullable;

/**
 * Why an item count of a character changed during a session; the wire value is the constant name. A closed vocabulary:
 * a host never invents a token, a new value ships in an api release first, then the platform, then the host. What no
 * value explains travels as an untracked row with the raw token, so a source is never {@code null} on the wire.
 *
 * <p>A consumer still reads leniently through {@link #fromToken}: an unknown token is {@code null}, logged and kept.
 * The sign of a row's delta is as the character sees it: {@code +} gained, {@code -} lost.</p>
 */
public enum ItemSource {

    /** Picked up from the ground or looted from a kill ({@code +}, NPC on loot); thrown on the ground ({@code -}). */
    DROP,

    /** Sweeper spoil. */
    SWEEP,

    FISHING,

    /** Given or taken by a quest. */
    QUEST,

    /** Reward of a game system with no other source: an event, an online or a daily reward. */
    REWARD,

    /** Product made ({@code +}); materials used ({@code -}). */
    CRAFT,

    /** Used up: potions, shots, arrows, bait. */
    CONSUME,

    /** Scrolls and stones spent, an item lost to a failed enchant ({@code -}); what the attempt returned ({@code +}). */
    ENCHANT,

    /** Item crystallized ({@code -}); crystals received ({@code +}). */
    CRYSTALLIZE,

    /** Dropped on death; the row names the killer when known. */
    DEATH_DROP,

    /** A fee the game charged: teleport, service, tax. The fee of a private store sale is not one. */
    FEE,

    /** The character bought from an NPC: goods gained, payment lost. */
    NPC_BUY,

    /** The character sold to an NPC: goods lost, payment gained. */
    NPC_SELL,

    /** Exchange by an NPC list: product gained, ingredients lost. */
    MULTISELL,

    /** Direct trade with a character. */
    TRADE,

    /** Deal in a private store, on either side. */
    PRIVATE_STORE,

    /** Attachment received, sent, returned or paid for. */
    MAIL,

    /** Loot handed out by the party rule; names the member when another member's kill or pickup gave it. */
    PARTY_DISTRIBUTE,

    /** Put into or taken from a warehouse. */
    WAREHOUSE,

    /** Moved between the character and its pet. */
    PET,

    /** Freight sent or received. */
    FREIGHT,

    /** Changed by staff in the game. */
    ADMIN,

    /** Granted by a paid purchase. */
    DONATE,

    /** Changed by a platform command while the session ran. */
    PLATFORM;

    public static @Nullable ItemSource fromToken(@Nullable String token) {
        return Vocabularies.fromToken(ItemSource.class, token);
    }
}
