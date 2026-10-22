package app.l2nx.gs.adapter.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class VocabulariesTest {

    enum ByName {
        ALPHA,
        BETA
    }

    enum ByKey implements Vocabularies.Keyed {
        FIRST("first_key");

        private final String key;

        ByKey(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }
    }

    @Test
    void fromToken_shouldResolveByName_whenEnumIsNotKeyed() {
        assertEquals(ByName.BETA, Vocabularies.fromToken(ByName.class, "BETA"));
    }

    @Test
    void fromToken_shouldResolveByKey_whenEnumIsKeyed() {
        assertSame(ByKey.FIRST, Vocabularies.fromToken(ByKey.class, "first_key"));
        assertNull(Vocabularies.fromToken(ByKey.class, "FIRST"));
    }

    @ParameterizedTest(name = "[{0}]")
    @NullSource
    @ValueSource(strings = {"", " ", "GAMMA", "alpha", "ALPHA "})
    void fromToken_shouldReturnNull_whenTokenUnknownOrBlank(String token) {
        assertNull(Vocabularies.fromToken(ByName.class, token));
    }
}
