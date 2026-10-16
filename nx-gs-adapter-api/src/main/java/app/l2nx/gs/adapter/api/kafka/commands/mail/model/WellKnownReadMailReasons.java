package app.l2nx.gs.adapter.api.kafka.commands.mail.model;

import app.l2nx.gs.adapter.api.kafka.commands.mail.ReadMailCommand;

/**
 * Canonical {@code extensions.reason} values on refusals of {@link ReadMailCommand}; an open string, so a new value is
 * not breaking. Malformed commands carry {@code field} instead.
 */
public final class WellKnownReadMailReasons {

    private WellKnownReadMailReasons() {}

    public static final String MAIL_NOT_FOUND = "MAIL_NOT_FOUND";
}
