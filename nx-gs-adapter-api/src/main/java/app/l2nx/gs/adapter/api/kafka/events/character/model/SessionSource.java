package app.l2nx.gs.adapter.api.kafka.events.character.model;

public enum SessionSource {
    CLIENT,

    /** Loaded by an app request, without a client. */
    APP
}
