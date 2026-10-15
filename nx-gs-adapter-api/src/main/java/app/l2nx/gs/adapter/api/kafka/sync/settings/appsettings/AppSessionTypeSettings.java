package app.l2nx.gs.adapter.api.kafka.sync.settings.appsettings;

import app.l2nx.gs.adapter.api.domain.character.ActivityType;
import java.util.Objects;

public final class AppSessionTypeSettings {

    private final ActivityType activity;
    private final boolean enabled;

    public AppSessionTypeSettings(ActivityType activity, boolean enabled) {
        this.activity = Objects.requireNonNull(activity, "AppSessionTypeSettings.activity is required");
        this.enabled = enabled;
    }

    public ActivityType getActivity() {
        return activity;
    }

    /** {@code false} refuses new starts; sessions already running finish. */
    public boolean isEnabled() {
        return enabled;
    }

    public Builder toBuilder() {
        return new Builder().activity(activity).enabled(enabled);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSessionTypeSettings)) return false;
        AppSessionTypeSettings that = (AppSessionTypeSettings) o;
        return enabled == that.enabled && activity == that.activity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(activity, enabled);
    }

    @Override
    public String toString() {
        return "AppSessionTypeSettings[activity=" + activity + ", enabled=" + enabled + "]";
    }

    public static final class Builder {
        private ActivityType activity;
        private boolean enabled;

        public Builder activity(ActivityType activity) {
            this.activity = activity;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public AppSessionTypeSettings build() {
            return new AppSessionTypeSettings(activity, enabled);
        }
    }
}
