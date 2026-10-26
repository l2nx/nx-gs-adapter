package app.l2nx.gs.adapter.api.kafka.sync.settings.app;

public final class ReviveSettings {

    private final boolean enabled;

    public ReviveSettings(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Builder toBuilder() {
        return new Builder().enabled(enabled);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReviveSettings)) return false;
        return enabled == ((ReviveSettings) o).enabled;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled);
    }

    @Override
    public String toString() {
        return "ReviveSettings[enabled=" + enabled + "]";
    }

    public static final class Builder {
        private boolean enabled;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ReviveSettings build() {
            return new ReviveSettings(enabled);
        }
    }
}
