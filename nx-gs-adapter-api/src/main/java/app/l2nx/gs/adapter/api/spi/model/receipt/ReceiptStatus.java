package app.l2nx.gs.adapter.api.spi.model.receipt;

/**
 * Lifecycle of a command receipt. The constant names are the values stored in the receipts table, so a rename orphans
 * every stored row. {@link #STARTED} and {@link #CHARGED} are open: a mutation may be under way and a retry is refused;
 * {@link #OK} and {@link #FAILED} are terminal.
 */
public enum ReceiptStatus {

    /** Claimed; nothing has been charged yet as far as the receipt knows. */
    STARTED,

    /** Money was (or is about to be) taken; a crash here needs recovery, never a plain retry. */
    CHARGED,

    /** Done; a replay answers the stored result. */
    OK,

    /** Released without effect; the same key may be claimed again. */
    FAILED
}
