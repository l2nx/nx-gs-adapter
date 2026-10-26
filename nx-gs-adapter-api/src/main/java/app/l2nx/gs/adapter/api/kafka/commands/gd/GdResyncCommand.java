package app.l2nx.gs.adapter.api.kafka.commands.gd;

import app.l2nx.gs.adapter.api.kafka.commands.CommandInitiator;
import app.l2nx.gs.adapter.api.kafka.commands.NxCommand;
import java.util.Objects;

/**
 * Re-publishes a full snapshot of every registered game-data entity, the same burst as on connect / datapack reload.
 * Always full snapshot; partition key {@code null}.
 *
 * <p>Ack at schedule time; completion shows as per-entity {@code SNAPSHOT_COMPLETE} markers in nx-gamedata.
 * {@code UNAVAILABLE} when gd-sync is inactive (disabled, failed, no provider, no gd topics from {@code /connect}).</p>
 */
public final class GdResyncCommand implements NxCommand<GdResyncResult> {

    private final CommandInitiator initiator;

    public GdResyncCommand(CommandInitiator initiator) {
        this.initiator = Objects.requireNonNull(initiator, "initiator");
    }

    @Override
    public CommandInitiator getInitiator() {
        return initiator;
    }

    public Builder toBuilder() {
        return new Builder().initiator(initiator);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GdResyncCommand)) return false;
        GdResyncCommand that = (GdResyncCommand) o;
        return Objects.equals(initiator, that.initiator);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(initiator);
    }

    @Override
    public String toString() {
        return "GdResyncCommand[initiator=" + initiator + "]";
    }

    public static final class Builder {
        private CommandInitiator initiator;

        public Builder initiator(CommandInitiator initiator) {
            this.initiator = initiator;
            return this;
        }

        public GdResyncCommand build() {
            return new GdResyncCommand(initiator);
        }
    }
}
