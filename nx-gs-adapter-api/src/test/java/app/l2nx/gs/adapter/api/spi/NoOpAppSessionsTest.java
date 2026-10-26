package app.l2nx.gs.adapter.api.spi;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import app.l2nx.gs.adapter.api.spi.model.session.OfflineSession;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NoOpAppSessionsTest {

    @Test
    void findOpen_shouldBeEmpty() {
        assertTrue(
                NoOpAppSessions.INSTANCE.findOpen(ActivityType.OFFLINE_FISHING).isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lastStartedReads")
    void findLastStarted_shouldFail_insteadOfReportingNoPreviousSession(
            String name, Supplier<CompletableFuture<Optional<OfflineSession>>> read) {
        CompletionException actual =
                assertThrows(CompletionException.class, () -> read.get().join());

        assertInstanceOf(IllegalStateException.class, actual.getCause());
    }

    static Stream<Arguments> lastStartedReads() {
        Supplier<CompletableFuture<Optional<OfflineSession>>> byMasterAccount =
                () -> NoOpAppSessions.INSTANCE.findLastStartedByMasterAccount(
                        UUID.randomUUID(), ActivityType.OFFLINE_FISHING);
        Supplier<CompletableFuture<Optional<OfflineSession>>> byCharacter =
                () -> NoOpAppSessions.INSTANCE.findLastStartedByCharacter(7, ActivityType.OFFLINE_PRIVATE_STORE);
        return Stream.of(
                Arguments.of("findLastStartedByMasterAccount", byMasterAccount),
                Arguments.of("findLastStartedByCharacter", byCharacter));
    }
}
