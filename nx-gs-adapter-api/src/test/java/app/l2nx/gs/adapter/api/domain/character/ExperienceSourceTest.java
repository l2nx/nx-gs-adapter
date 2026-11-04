package app.l2nx.gs.adapter.api.domain.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExperienceSourceTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExperienceSource.class)
    void fromToken_shouldReadEveryConstantName(ExperienceSource source) {
        assertEquals(source, ExperienceSource.fromToken(source.name()));
    }

    @ParameterizedTest(name = "token={0}")
    @NullSource
    @ValueSource(strings = {"", "SOME_FUTURE_SOURCE", "kill"})
    void fromToken_shouldBeNull_whenTokenIsUnknown(String token) {
        assertNull(ExperienceSource.fromToken(token));
    }

    @Test
    void values_shouldBeTheWireVocabulary() {
        Set<String> expected = new HashSet<String>(
                Arrays.asList("KILL", "QUEST", "REWARD", "DEATH_PENALTY", "SPEND", "RESURRECT", "ADMIN", "PLATFORM"));
        Set<String> actual = new HashSet<String>();
        for (ExperienceSource source : ExperienceSource.values()) {
            actual.add(source.name());
        }

        assertEquals(expected, actual);
    }
}
