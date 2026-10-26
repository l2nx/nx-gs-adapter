package app.l2nx.gs.adapter.api.kafka.commands;

import java.util.UUID;

public final class TestInitiators {

    public static final CommandInitiator USER = CommandInitiator.builder()
            .source(CommandInitiator.USER_SOURCE)
            .userId(UUID.fromString("5b1f3c2e-0000-4000-8000-0000000000aa"))
            .email("player@example.com")
            .ip("203.0.113.7")
            .build();

    /** {@link #USER} on the wire, as Gson writes it (absent values omitted). */
    public static final String USER_JSON = "{\"source\":\"user\",\"userId\":\"5b1f3c2e-0000-4000-8000-0000000000aa\","
            + "\"email\":\"player@example.com\",\"ip\":\"203.0.113.7\"}";

    public static final CommandInitiator SERVICE = CommandInitiator.builder()
            .source("nx-gameservers")
            .label("scheduled-job")
            .build();

    private TestInitiators() {}
}
