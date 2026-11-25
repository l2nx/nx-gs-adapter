package app.l2nx.gs.adapter.api.kafka.commands.ban.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class BanRefTest {

    @Nested
    class Constructor {

        @ParameterizedTest(name = "[{0}]")
        @NullSource
        @ValueSource(strings = {"", " "})
        void constructor_shouldReject_whenSourceMissingOrBlank(String source) {
            BanRef.Builder builder = BanRef.builder().source(source).id(11L);

            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void constructor_shouldKeepToken_whenSourceUnknownToThisApi() {
            BanRef actual = new BanRef("SOMETHING_NEW", 11L);

            assertEquals("SOMETHING_NEW", actual.getSource());
            assertNull(BanSource.fromToken(actual.getSource()));
        }
    }

    @Nested
    class Builder {

        @Test
        void builder_shouldRoundtripAllFields_whenSourceGivenAsEnum() {
            BanRef original =
                    BanRef.builder().source(BanSource.SHADOW).id(268435457L).build();

            BanRef copy = original.toBuilder().build();

            assertEquals("SHADOW", original.getSource());
            assertEquals(268435457L, original.getId());
            assertEquals(original, copy);
            assertEquals(original.hashCode(), copy.hashCode());
            assertNotSame(original, copy);
        }
    }

    @Nested
    class ValueSemantics {

        @Test
        void equals_shouldFollowTheContract() {
            EqualsVerifier.forClass(BanRef.class).verify();
        }
    }

    @Nested
    class Json {

        @Test
        void gson_shouldKeepToken_whenSourceUnknownToThisApi() {
            BanRef actual = new Gson().fromJson("{\"source\":\"SOMETHING_NEW\",\"id\":11}", BanRef.class);

            assertEquals("SOMETHING_NEW", actual.getSource());
            assertEquals(11L, actual.getId());
        }
    }
}
