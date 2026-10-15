package app.l2nx.gs.adapter.api.kafka.events.server.online.model;

import app.l2nx.gs.adapter.api.kafka.events.server.online.ServerOnlineSnapshotEvent;

/**
 * Key of {@link ServerOnlineSnapshotEvent#getBuckets()}. Buckets may overlap, so a total is never their sum.
 *
 * <p>An activity bucket counts every character with that activity whatever started it: an app fisher counts in
 * {@link #OFFLINE_FISHING}, not in {@link #FISHING}. A later offline action adds an {@code OFFLINE_<ACTION>} bucket
 * named like its {@code ActivityType}.</p>
 */
public enum ServerOnlineBucket {

    /** Required. Every character the host tracks, including offline private-store sessions and bot-driven phantoms. */
    TOTAL,

    /** Required. Distinct active human players, deduplicated by a host-defined identity tuple (e.g. HWID + IP). */
    UNIQUE,

    OFFLINE_PRIVATE_STORE,

    FISHING,

    APP,

    OFFLINE_FISHING
}
