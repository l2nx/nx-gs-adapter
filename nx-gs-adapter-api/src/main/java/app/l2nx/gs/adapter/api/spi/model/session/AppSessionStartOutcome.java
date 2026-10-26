package app.l2nx.gs.adapter.api.spi.model;

import app.l2nx.gs.adapter.api.kafka.commands.session.model.AppSessionRefusalReason;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Result of {@code NxAppSessions.start}. Every way a start can fail is a {@link Status}, never an exception. */
public final class AppSessionStartOutcome {

    public enum Status {
        /** A new session runs. */
        STARTED,

        /** A session with the same idempotency key exists (a retry); {@link #getSession()} is it, open or closed. */
        EXISTING,

        /**
         * Another session is open for the character; {@link #getSession()} is it. It may already carry {@code endedAt}
         * while its close is retried against the table, which keeps the character busy until then.
         */
        CHARACTER_BUSY,

        /** Another open session holds the requested spot; the host may pick another and start again. */
        SPOT_TAKEN,

        /** {@code OfflineSessionHost.placeInWorld} refused; {@link #getRefusal()} is its reason. No event, row deleted. */
        PLACEMENT_REFUSED,

        /**
         * The engine could not start the session: it is not running here, the database failed, or the host's
         * {@code startAction} threw; {@link #getDetail()} says which. No event was published, a placed character went
         * back to its origin and the row is deleted. When that delete fails it is retried in the background and the
         * character counts as busy ({@link #CHARACTER_BUSY}, or {@code UNAVAILABLE} again for a retry of the same
         * request); if the JVM stops first, the row is restored as an open session at the next start.
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
