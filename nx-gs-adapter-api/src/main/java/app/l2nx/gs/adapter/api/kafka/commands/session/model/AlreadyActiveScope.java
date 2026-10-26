package app.l2nx.gs.adapter.api.kafka.commands.session.model;

/** Which limit an {@link AppSessionRefusalReason#ALREADY_ACTIVE} refusal hit. */
public enum AlreadyActiveScope {
    MASTER_ACCOUNT,

    ACCOUNT,

    HWID,

    CHARACTER,

    /**
     * The host's per-IP limit: counted under the start's {@code initiator.ip} and under the character's last game-client
     * IP; an absent IP limits nothing.
     */
    IP
}
