package app.l2nx.gs.adapter.api.kafka.commands.ban.model;

import app.l2nx.gs.adapter.api.kafka.commands.ban.BanCommand;
import app.l2nx.gs.adapter.api.kafka.commands.ban.UnbanCommand;

/**
 * Former {@code targetType} values on {@link BanCommand} / {@link UnbanCommand}: those of
 * {@link WellKnownBanSubjectTypes} plus {@code HARD}, a command-only marker the host expands into character, account,
 * IP and HWID bans, so a persisted ban row never carries it.
 *
 * @deprecated use {@link WellKnownBanSubjectTypes}; {@code HARD} has no replacement, send one command per subject. Hosts
 *     keep accepting {@code HARD} until it is removed.
 */
// TODO: drop together with target* and HARD once the adapter of every active game server reads subject*.
@Deprecated
public final class WellKnownBanTargetTypes {

    private WellKnownBanTargetTypes() {}

    public static final String CHARACTER = "CHARACTER";
    public static final String ACCOUNT = "ACCOUNT";
    public static final String IP = "IP";
    public static final String HWID = "HWID";
    public static final String HARD = "HARD";
}
