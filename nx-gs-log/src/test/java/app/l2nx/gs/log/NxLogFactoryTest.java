package app.l2nx.gs.log;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class NxLogFactoryTest {

    @Test
    void getLogger_shouldReturnSlf4jImpl_whenBindingPresent() {
        NxLog log = NxLogFactory.getLogger(NxLogFactoryTest.class);
        assertTrue(NxLogFactory.isSlf4jAvailable());
        assertEquals("Slf4jNxLog", log.getClass().getSimpleName());
    }

    @Test
    void log_shouldWritePrefixedMessageAtItsLevel() throws Exception {
        String name = DebugLogger.class.getName();
        System.setProperty("org.slf4j.simpleLogger.log." + name, "debug");
        NxLog log = NxLogFactory.getLogger(DebugLogger.class);

        String logged = captureStandardError(() -> {
            log.debug("debug {}", "test");
            log.info("info {}", "test");
            log.warn("warn {}", "test");
            log.error("error {}", "test");
        });

        assertTrue(logged.contains("DEBUG " + name + " - [L2NX] debug test"), logged);
        assertTrue(logged.contains("INFO " + name + " - [L2NX] info test"), logged);
        assertTrue(logged.contains("WARN " + name + " - [L2NX] warn test"), logged);
        assertTrue(logged.contains("ERROR " + name + " - [L2NX] error test"), logged);
    }

    @Test
    void log_shouldWriteStackTrace_whenLastArgIsThrowable() throws Exception {
        NxLog log = NxLogFactory.getLogger(NxLogFactoryTest.class);
        Exception ex = new RuntimeException("test error");

        String logged = captureStandardError(() -> log.error("failed: {}", ex.getMessage(), ex));

        assertTrue(logged.contains("[L2NX] failed: test error"), logged);
        assertTrue(logged.contains("java.lang.RuntimeException: test error"), logged);
    }

    /** slf4j-simple writes to whatever System.err is at the time of the call. */
    private static String captureStandardError(Runnable action) throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.err;
        System.setErr(new PrintStream(captured, true, "UTF-8"));
        try {
            action.run();
        } finally {
            System.setErr(original);
        }
        return captured.toString("UTF-8");
    }

    private static final class DebugLogger {}
}
