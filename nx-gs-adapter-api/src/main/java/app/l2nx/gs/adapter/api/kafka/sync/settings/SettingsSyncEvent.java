package app.l2nx.gs.adapter.api.kafka.sync.settings;

import java.util.Objects;

/**
 * Wire envelope of the settings family, one record per snapshot on the entity's {@code syncTopics.settings} topic; all
 * settings entities share {@code <tenant>.gs.settings.sync} and the consumer routes by {@code entityName}. {@code serverId}
 * rides the {@code Nx-Server-Id} header and the tenant comes from the topic slug, not the body.
 *
 * <p>Every record is the full current settings of one server, so the newest one wins.</p>
 *
 * @param <T> entity payload type
 */
public final class SettingsSyncEvent<T> {

    private final String entityName;
    private final T payload;
    private final long timestampEpochMs;

    public SettingsSyncEvent(String entityName, T payload, long timestampEpochMs) {
        this.entityName = Objects.requireNonNull(entityName, "SettingsSyncEvent.entityName is required");
        this.payload = Objects.requireNonNull(payload, "SettingsSyncEvent.payload is required");
        this.timestampEpochMs = timestampEpochMs;
    }

    public String getEntityName() {
        return entityName;
    }

    public T getPayload() {
        return payload;
    }

    public long getTimestampEpochMs() {
        return timestampEpochMs;
    }

    public Builder<T> toBuilder() {
        return new Builder<T>().entityName(entityName).payload(payload).timestampEpochMs(timestampEpochMs);
    }

    public static <T> Builder<T> builder() {
        return new Builder<T>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SettingsSyncEvent)) return false;
        SettingsSyncEvent<?> that = (SettingsSyncEvent<?>) o;
        return timestampEpochMs == that.timestampEpochMs
                && Objects.equals(entityName, that.entityName)
                && Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityName, payload, timestampEpochMs);
    }

    @Override
    public String toString() {
        return "SettingsSyncEvent[entityName=" + entityName + ", payload=" + payload + ", timestampEpochMs="
                + timestampEpochMs + "]";
    }

    public static final class Builder<T> {
        private String entityName;
        private T payload;
        private long timestampEpochMs;

        public Builder<T> entityName(String entityName) {
            this.entityName = entityName;
            return this;
        }

        public Builder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }

        public Builder<T> timestampEpochMs(long timestampEpochMs) {
            this.timestampEpochMs = timestampEpochMs;
            return this;
        }

        public SettingsSyncEvent<T> build() {
            return new SettingsSyncEvent<T>(entityName, payload, timestampEpochMs);
        }
    }
}
