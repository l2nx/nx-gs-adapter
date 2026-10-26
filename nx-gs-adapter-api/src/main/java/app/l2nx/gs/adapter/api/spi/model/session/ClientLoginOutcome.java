package app.l2nx.gs.adapter.api.spi.model;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Result of {@code NxAppSessions.onClientLogin}: whether the host may let the client in now. */
public final class ClientLoginOutcome {

    public enum Status {
        /** The open session was closed with {@code APP_LOGIN}; {@link #getSession()} is it. */
        CLOSED,

        /** The character had no open session. */
        NONE,

        /**
         * The bounded wait ran out. The host refuses this login with "try again": letting the client in now could put a
         * played character in the world while the app-loaded one still is.
         */
        PENDING
    }

    private static final ClientLoginOutcome NONE = new ClientLoginOutcome(Status.NONE, null);
    private static final ClientLoginOutcome PENDING = new ClientLoginOutcome(Status.PENDING, null);

    private final Status status;
    private final @Nullable OfflineSession session;

    private ClientLoginOutcome(Status status, @Nullable OfflineSession session) {
        this.status = status;
        this.session = session;
    }

    public static ClientLoginOutcome closed(OfflineSession session) {
        return new ClientLoginOutcome(Status.CLOSED, Objects.requireNonNull(session, "session"));
    }

    public static ClientLoginOutcome none() {
        return NONE;
    }

    public static ClientLoginOutcome pending() {
        return PENDING;
    }

    public Status getStatus() {
        return status;
    }

    /** Set for {@link Status#CLOSED} only. */
    public @Nullable OfflineSession getSession() {
        return session;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClientLoginOutcome)) return false;
        ClientLoginOutcome that = (ClientLoginOutcome) o;
        return status == that.status && Objects.equals(session, that.session);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, session);
    }

    @Override
    public String toString() {
        return "ClientLoginOutcome[status=" + status + ", session=" + session + "]";
    }
}
