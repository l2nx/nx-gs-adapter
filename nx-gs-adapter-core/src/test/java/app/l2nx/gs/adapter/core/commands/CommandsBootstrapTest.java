package app.l2nx.gs.adapter.core.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommandsBootstrapTest {

    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @CsvSource({
        "acme.gs.commands, main, acme.gs.commands.main",
        "acme.ls.commands, login1, acme.ls.commands.login1"
    })
    void groupId_shouldAppendTheServerSlugToTheCommandsTopic(String topic, String serverSlug, String expected) {
        String actual = CommandsBootstrap.groupId(topic, serverSlug);

        assertEquals(expected, actual);
    }
}
