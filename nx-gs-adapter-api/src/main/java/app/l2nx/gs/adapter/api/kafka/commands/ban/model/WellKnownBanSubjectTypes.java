package app.l2nx.gs.adapter.api.kafka.commands.ban.model;

import app.l2nx.gs.adapter.api.kafka.commands.ban.BanCommand;
import app.l2nx.gs.adapter.api.kafka.commands.ban.UnbanCommand;
import app.l2nx.gs.adapter.api.kafka.sync.db.ban.BanDbDto;

/**
 * Canonical {@code subjectType} values on {@link BanCommand} / {@link UnbanCommand} and {@link BanDbDto}. Open string so
 * a new host dimension is not a breaking change; unknown values are stored verbatim.
 *
 * <ul>
 *   <li>{@link #CHARACTER} - {@code subjectValue} is the char id as a string.</li>
 *   <li>{@link #ACCOUNT} - whole login account; {@code subjectValue} is the account login.</li>
 *   <li>{@link #IP} - plaintext IP.</li>
 *   <li>{@link #HWID} - HWID hash.</li>
 * </ul>
 */
public final class WellKnownBanSubjectTypes {

    private WellKnownBanSubjectTypes() {}

    public static final String CHARACTER = "CHARACTER";
    public static final String ACCOUNT = "ACCOUNT";
    public static final String IP = "IP";
    public static final String HWID = "HWID";
}
