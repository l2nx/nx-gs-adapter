package app.l2nx.gs.adapter.api.spi.model.session;

import app.l2nx.gs.adapter.api.kafka.commands.session.model.AppSessionRefusalReason;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Result of {@code NxAppSessions.start}. Every way a start can fail is a {@link Status}, never an exception; its check
 * mode answers with {@link AppSessionCheckOutcome}, whose refusals carry the same data.
 */
public final class AppSessionStartOutcome {

    public enum Status {
        STARTED,

        /** Same idempotency key (a retry); the session may be open or closed. */
        EXISTING,

        /** The session may already carry {@code endedAt} while its close is retried; the character stays busy until then. */
        CHARACTER_BUSY,

        /** The host may pick another spot and start again. */
        SPOT_TAKEN,

        /** {@code OfflineSessionHost.placeInWorld} refused. No event, row deleted. */
        PLACEMENT_REFUSED,

        /**
         * Engine not running, database failure, or the host's {@code startAction} threw. No event, a placed character
         * went back to its origin, the row is deleted; a failed delete is retried and keeps the character busy, and
         * after a JVM stop the row is restored as an open session.
         */
        UNAVAILABLE
    }

    private final Status status;
    private final @Nullable OfflineSession session;
    private final @Nullable AppSessionRefusalReason refusal;
    private final @Nullable String detail;

    private AppSessionStartOutcome(
            Status status,
            @Nullable OfflineSession session,
            @Nullable AppSessionRefusalReason refusal,
            @Nullable String detail) {
        this.status = status;
        this.session = session;
        this.refusal = refusal;
        this.detail = detail;
    }

    public static AppSessionStartOutcome started(OfflineSession session) {
        return new AppSessionStartOutcome(Status.STARTED, Objects.requireNonNull(session, "session"), null, null);
    }

    public static AppSessionStartOutcome existing(OfflineSession session) {
        return new AppSessionStartOutcome(Status.EXISTING, Objects.requireNonNull(session, "session"), null, null);
    }

    public static AppSessionStartOutcome characterBusy(OfflineSession openSession) {
        return new AppSessionStartOutcome(
                Status.CHARACTER_BUSY, Objects.requireNonNull(openSession, "openSession"), null, null);
    }

    public static AppSessionStartOutcome spotTaken() {
        return new AppSessionStartOutcome(Status.SPOT_TAKEN, null, null, null);
    }

    public static AppSessionStartOutcome placementRefused(AppSessionRefusalReason refusal, @Nullable String detail) {
        return new AppSessionStartOutcome(
                Status.PLACEMENT_REFUSED, null, Objects.requireNonNull(refusal, "refusal"), detail);
    }

    public static AppSessionStartOutcome unavailable(String detail) {
        return new AppSessionStartOutcome(Status.UNAVAILABLE, null, null, Objects.requireNonNull(detail, "detail"));
    }

    public Status getStatus() {
        return status;
    }

    /** Set for {@link Status#STARTED}, {@link Status#EXISTING} and {@link Status#CHARACTER_BUSY}. */
    public @Nullable OfflineSession getSession() {
        return session;
    }

    /** Set for {@link Status#PLACEMENT_REFUSED}. */
    public @Nullable AppSessionRefusalReason getRefusal() {
        return refusal;
    }

    /** Log text for {@link Status#PLACEMENT_REFUSED} and {@link Status#UNAVAILABLE}; never shown to a player. */
    public @Nullable String getDetail() {
        return detail;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSessionStartOutcome)) return false;
        AppSessionStartOutcome that = (AppSessionStartOutcome) o;
        return status == that.status
                && Objects.equals(session, that.session)
                && refusal == that.refusal
                && Objects.equals(detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, session, refusal, detail);
    }

    @Override
    public String toString() {
        return "AppSessionStartOutcome[status=" + status
                + ", session=" + session
                + ", refusal=" + refusal
                + ", detail=" + detail + "]";
    }
}
