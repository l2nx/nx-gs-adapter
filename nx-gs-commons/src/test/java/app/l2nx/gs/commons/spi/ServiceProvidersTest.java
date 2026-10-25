package app.l2nx.gs.commons.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServiceProvidersTest {

    interface Greeter {}

    static final class English implements Greeter {}

    static final class French implements Greeter {}

    @Nested
    class Single {

        @Test
        void single_shouldGiveTheProvider_whenExactlyOneIsRegistered() {
            Greeter only = new English();

            SingleProvider<Greeter> actual = ServiceProviders.single(Greeter.class, Collections.singletonList(only));

            assertSame(only, actual.get());
            assertFalse(actual.absent());
            assertFalse(actual.ambiguous());
        }

        @Test
        void single_shouldBeAbsent_whenNoneIsRegistered() {
            SingleProvider<Greeter> actual = ServiceProviders.single(Greeter.class, Collections.<Greeter>emptyList());

            assertNull(actual.get());
            assertTrue(actual.absent());
            assertFalse(actual.ambiguous());
        }

        @Test
        void single_shouldUseNoneAndNameThemAll_whenSeveralAreRegistered() {
            SingleProvider<Greeter> actual =
                    ServiceProviders.single(Greeter.class, Arrays.<Greeter>asList(new English(), new French()));

            assertNull(actual.get());
            assertTrue(actual.ambiguous());
            assertFalse(actual.absent());
            assertEquals(
                    "Several Greeter implementations on the classpath: [" + English.class.getName() + ", "
                            + French.class.getName() + "]; keep exactly one",
                    actual.ambiguity());
        }
    }
}
