package app.l2nx.gs.adapter.api.kafka.commands;

/**
 * {@code true}: the platform verified, against fresh master-account data, that the acting user owns the character. Not
 * authorization: the host may use it only to relax device- or session-bound checks (e.g. a device-bound item lock) that
 * a platform-issued command cannot satisfy.
 */
public interface OwnerVerified {

    boolean isOwnerVerified();

    /**
     * {@code true}: the initiator must be a user, whose {@code userId} is the owner. Commands a platform service can
     * send for itself override it. Not a bean getter on purpose: Jackson would put it on the wire.
     */
    default boolean ownerScoped() {
        return true;
    }
}
