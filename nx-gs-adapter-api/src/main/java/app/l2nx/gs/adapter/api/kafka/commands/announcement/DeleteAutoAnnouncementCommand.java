package app.l2nx.gs.adapter.api.kafka.commands.announcement;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import java.util.Objects;

/**
 * Deletes one row from the host's native {@code auto_announcements} table. Used for operator "delete in game"
 * and the GAME->L2NX transfer, where the source row is removed so the next db-sync does not re-ingest it.
 *
 * <p>Reply: {@code CommandResult<Void>}; {@code NOT_FOUND} if no such row, {@code INTERNAL_ERROR} on host-side failure.</p>
 */
public final class DeleteAutoAnnouncementCommand implements NxCommand<Void> {

    private final long gameId;
    private final CommandInitiator initiator;

    public DeleteAutoAnnouncementCommand(long gameId, CommandInitiator initiator) {
        this.gameId = gameId;
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    /** Host's native row id; same value as {@code AutoAnnouncementDbDto.id} on the db-sync stream. */
    public long getGameId() {
        return gameId;
    }

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    public Builder toBuilder() {
        return new Builder().gameId(gameId).initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeleteAutoAnnouncementCommand)) return false;
        DeleteAutoAnnouncementCommand that = (DeleteAutoAnnouncementCommand) o;
        return gameId == that.gameId && Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameId, initiator);
    }

    @Override
    public String toString() {
        return "DeleteAutoAnnouncementCommand[gameId=" + gameId + ", initiator=" + initiator + "]";
    }

    public static final class Builder {
        private long gameId;
        private CommandInitiator initiator;

        public Builder gameId(long gameId) {
            this.gameId = gameId;
            return this;
        }

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public DeleteAutoAnnouncementCommand build() {
            return new DeleteAutoAnnouncementCommand(gameId, initiator);
        }
    }
}
