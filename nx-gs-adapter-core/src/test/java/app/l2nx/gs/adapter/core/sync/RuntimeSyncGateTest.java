package app.l2nx.gs.adapter.core.sync;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RuntimeSyncGateTest {

    private final RuntimeSyncGate gate = new RuntimeSyncGate();
    private final AtomicInteger fired = new AtomicInteger();

    @Nested
    class HostStarted {

        @Test
        void hostStarted_shouldFireTheWaitingCallback() {
            gate.onHostStarted(fired::incrementAndGet);

            gate.hostStarted();

            assertEquals(1, fired.get());
        }

        @Test
        void hostStarted_shouldFireOnce_whenCalledAgain() {
            gate.onHostStarted(fired::incrementAndGet);

            gate.hostStarted();
            gate.hostStarted();

            assertEquals(1, fired.get());
        }

        @Test
        void hostStarted_shouldNotThrow_whenTheCallbackDoes() {
            gate.onHostStarted(() -> {
                throw new IllegalStateException("engine failed");
            });

            assertDoesNotThrow(gate::hostStarted);
        }
    }

    @Nested
    class OnHostStarted {

        @Test
        void onHostStarted_shouldWait_untilTheHostDeclaresItsStart() {
            gate.onHostStarted(fired::incrementAndGet);

            assertEquals(0, fired.get());
        }

        @Test
        void onHostStarted_shouldRunAtOnce_whenTheStartWasDeclared() {
            gate.hostStarted();

            gate.onHostStarted(fired::incrementAndGet);

            assertEquals(1, fired.get());
        }

        @Test
        void onHostStarted_shouldRunEveryLaterRegistration_soAReconnectTicksAtOnce() {
            gate.hostStarted();

            gate.onHostStarted(fired::incrementAndGet);
            gate.onHostStarted(fired::incrementAndGet);

            assertEquals(2, fired.get());
        }

        @Test
        void onHostStarted_shouldLetTheLastRegistrationWin() {
            AtomicInteger replaced = new AtomicInteger();
            gate.onHostStarted(replaced::incrementAndGet);
            gate.onHostStarted(fired::incrementAndGet);

            gate.hostStarted();

            assertEquals(0, replaced.get());
            assertEquals(1, fired.get());
        }
    }
}
