package app.l2nx.gs.adapter.api.spi.model.receipt;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Answer of {@code NxCommandReceipts.claim} and {@code lookup}: whether the caller may run the command. */
public final class ClaimOutcome {

    public enum Kind {
        /** The caller owns the key now and runs the command. */
        CLAIMED,

        /** The command already completed under this key; answer {@link #getResultJson()} instead of running it. */
        REPLAY_OK,

        /** Another attempt holds the key, or its state cannot be told; refuse rather than risk running twice. */
        IN_FLIGHT
    }

    private static final ClaimOutcome CLAIMED = new ClaimOutcome(Kind.CLAIMED, null);
    private static final ClaimOutcome IN_FLIGHT = new ClaimOutcome(Kind.IN_FLIGHT, null);

    private final Kind kind;
    private final @Nullable String resultJson;

    private ClaimOutcome(Kind kind, @Nullable String resultJson) {
        this.kind = kind;
        this.resultJson = resultJson;
    }

    public static ClaimOutcome claimed() {
        return CLAIMED;
    }

    /** {@code resultJson} may be {@code null} when the completing write stored none. */
    public static ClaimOutcome replayOk(@Nullable String resultJson) {
        return new ClaimOutcome(Kind.REPLAY_OK, resultJson);
    }

    public static ClaimOutcome inFlight() {
        return IN_FLIGHT;
    }

    public Kind getKind() {
        return kind;
    }

    /** The stored result of the completed command; only {@link Kind#REPLAY_OK} carries one. */
    public @Nullable String getResultJson() {
        return resultJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClaimOutcome)) return false;
        ClaimOutcome that = (ClaimOutcome) o;
        return kind == that.kind && Objects.equals(resultJson, that.resultJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, resultJson);
    }

    @Override
    public String toString() {
        return "ClaimOutcome[kind=" + kind + ", resultJson=" + resultJson + "]";
    }
}
