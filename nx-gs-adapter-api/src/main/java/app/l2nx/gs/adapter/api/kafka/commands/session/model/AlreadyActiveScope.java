package app.l2nx.gs.adapter.api.kafka.commands.session.model;

/** Which limit an {@link AppSessionRefusalReason#ALREADY_ACTIVE} refusal hit. */
public enum AlreadyActiveScope {
    MASTER_ACCOUNT,

    ACCOUNT,

    HWID,

    CHARACTER
}
