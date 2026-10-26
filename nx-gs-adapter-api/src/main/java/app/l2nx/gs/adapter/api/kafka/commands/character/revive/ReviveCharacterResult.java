package app.l2nx.gs.adapter.api.kafka.commands.character.revive;

import java.util.Objects;

public final class ReviveCharacterResult {

    private final String restartPoint;
    private final int x;
    private final int y;
    private final int z;
    private final boolean clientDisconnected;

    public ReviveCharacterResult(String restartPoint, int x, int y, int z, boolean clientDisconnected) {
        this.restartPoint = Objects.requireNonNull(restartPoint, "ReviveCharacterResult.restartPoint is required");
        this.x = x;
        this.y = y;
        this.z = z;
        this.clientDisconnected = clientDisconnected;
    }

    public String getRestartPoint() {
        return restartPoint;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    /** {@code false} also for a character already out of the game, whatever {@code disconnectClient} said. */
    public boolean isClientDisconnected() {
        return clientDisconnected;
    }

    public Builder toBuilder() {
        return new Builder().restartPoint(restartPoint).x(x).y(y).z(z).clientDisconnected(clientDisconnected);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReviveCharacterResult)) return false;
        ReviveCharacterResult that = (ReviveCharacterResult) o;
        return x == that.x
                && y == that.y
                && z == that.z
                && clientDisconnected == that.clientDisconnected
                && restartPoint.equals(that.restartPoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(restartPoint, x, y, z, clientDisconnected);
    }

    @Override
    public String toString() {
        return "ReviveCharacterResult[restartPoint=" + restartPoint
                + ", x=" + x
                + ", y=" + y
                + ", z=" + z
                + ", clientDisconnected=" + clientDisconnected + "]";
    }

    public static final class Builder {
        private String restartPoint;
        private int x;
        private int y;
        private int z;
        private boolean clientDisconnected;

        public Builder restartPoint(String restartPoint) {
            this.restartPoint = restartPoint;
            return this;
        }

        public Builder x(int x) {
            this.x = x;
            return this;
        }

        public Builder y(int y) {
            this.y = y;
            return this;
        }

        public Builder z(int z) {
            this.z = z;
            return this;
        }

        public Builder clientDisconnected(boolean clientDisconnected) {
            this.clientDisconnected = clientDisconnected;
            return this;
        }

        public ReviveCharacterResult build() {
            return new ReviveCharacterResult(restartPoint, x, y, z, clientDisconnected);
        }
    }
}
