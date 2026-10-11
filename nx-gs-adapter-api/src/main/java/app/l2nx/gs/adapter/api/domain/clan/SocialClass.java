package app.l2nx.gs.adapter.api.domain.clan;

/**
 * Social class (pledge class) of a clan member, in ascending order of rank; the declaration order is the rank, so
 * {@code ordinal()} comparisons are meaningful.
 */
public enum SocialClass {
    VAGABOND,
    VASSAL,
    APPRENTICE,
    HEIR,
    KNIGHT,
    ELDER,
    BARON,
    VISCOUNT,
    COUNT,
    MARQUIS,
    DUKE,
    GRAND_DUKE,
    DISTINGUISHED_KING,
    EMPEROR
}
