package app.l2nx.gs.adapter.api.spi.model.session;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Result of {@code NxAppSessions.check}: the engine's own start checks without a start. Every refusal carries what the
 * matching {@link AppSessionStartOutcome} status carries, so a host maps both to one command refusal.
 */
public final class AppSessionCheckOutcome {

    public enum Status {
        ALLOWED,

        /** A check has no idempotency key, so any open session of the character counts. */
        CHARACTER_BUSY,

        SPOT_TAKEN,

        /** The engine is not running, the database failed, or the spot is one a start request rejects. */
        UNAVAILABLE
    }

    private final Status status;
    private final @Nullable OfflineSession session;
    private final @Nullable String detail;

    private AppSessionCheckOutcome(Status status, @Nullable OfflineSession session, @Nullable String detail) {
        this.status = status;
        this.session = session;
        this.detail = detail;
    }

    public static AppSessionCheckOutcome allowed() {
        return new AppSessionCheckOutcome(Status.ALLOWED, null, null);
    }

    public static AppSessionCheckOutcome characterBusy(OfflineSession openSession) {
        return new AppSessionCheckOutcome(
                Status.CHARACTER_BUSY, Objects.requireNonNull(openSession, "openSession"), null);
    }

    public static AppSessionCheckOutcome spotTaken() {
        return new AppSessionCheckOutcome(Status.SPOT_TAKEN, null, null);
    }

    public static AppSessionCheckOutcome unavailable(String detail) {
        return new AppSessionCheckOutcome(Status.UNAVAILABLE, null, Objects.requireNonNull(detail, "detail"));
    }

    public Status getStatus() {
        return status;
    }

    /** Set for {@link Status#CHARACTER_BUSY}. */
    public @Nullable OfflineSession getSession() {
        return session;
    }

    /** Log text for {@link Status#UNAVAILABLE}; never shown to a player. */
    public @Nullable String getDetail() {
        return detail;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSessionCheckOutcome)) return false;
        AppSessionCheckOutcome that = (AppSessionCheckOutcome) o;
        return status == that.status && Objects.equals(session, that.session) && Objects.equals(detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, session, detail);
    }

    @Override
    public String toString() {
        return "AppSessionCheckOutcome[status=" + status + ", session=" + session + ", detail=" + detail + "]";
    }
}
