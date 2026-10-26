package app.l2nx.gs.adapter.api.kafka.commands;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Who triggered a command: a platform user ({@code source = "user"} with {@code userId}) or a platform service
 * ({@code source} names the service, no {@code userId}). Context for host rules and audit, never authorization.
 * Gson bypasses the constructor, so the commands consumer validates a deserialized initiator before the handler runs.
 */
public final class CommandInitiator {

    public static final String USER_SOURCE = "user";

    static final int SOURCE_MAX = 32;
    static final int LABEL_MAX = 255;
    static final int EMAIL_MAX = 254;
    static final int IP_MAX = 45;

    private final String source;
    private final @Nullable UUID userId;
    private final @Nullable String label;
    private final @Nullable String email;
    private final @Nullable String ip;

    /** Trims every string, lowercases {@code source} and {@code email}, turns blank optional strings into {@code null}. */
    public CommandInitiator(
            String source, @Nullable UUID userId, @Nullable String label, @Nullable String email, @Nullable String ip) {
        String normalizedSource = trimToNull(source, "source", SOURCE_MAX);
        if (normalizedSource == null) {
            throw new IllegalArgumentException("CommandInitiator.source is required");
        }
        this.source = normalizedSource.toLowerCase(Locale.ROOT);
        if (USER_SOURCE.equals(this.source) && userId == null) {
            throw new IllegalArgumentException("CommandInitiator.userId is required for source=user");
        }
        if (!USER_SOURCE.equals(this.source) && userId != null) {
            throw new IllegalArgumentException(
                    "CommandInitiator.userId must be null for service source " + this.source);
        }
        this.userId = userId;
        this.label = trimToNull(label, "label", LABEL_MAX);
        String normalizedEmail = trimToNull(email, "email", EMAIL_MAX);
        this.email = normalizedEmail == null ? null : normalizedEmail.toLowerCase(Locale.ROOT);
        this.ip = trimToNull(ip, "ip", IP_MAX);
    }

    private static @Nullable String trimToNull(@Nullable String value, String field, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > max) {
            throw new IllegalArgumentException("CommandInitiator." + field + " exceeds " + max + " characters");
        }
        return trimmed;
    }

    /** {@value #USER_SOURCE} for a platform user, otherwise the name of the sending platform service. */
    public String getSource() {
        return source;
    }

    /** The platform user (master account); non-null exactly when {@link #isUser()}. */
    public @Nullable UUID getUserId() {
        return userId;
    }

    /** Human-readable attribution of a service source: a job name, a messenger handle. */
    public @Nullable String getLabel() {
        return label;
    }

    /** The user's email at dispatch time; {@code null} when the user has none or the source is a service. */
    public @Nullable String getEmail() {
        return email;
    }

    /** IP of the triggering request; {@code null} when unknown or a placeholder. A rule treats {@code null} as "limits nothing", never as a match. */
    public @Nullable String getIp() {
        return ip;
    }

    public boolean isUser() {
        return USER_SOURCE.equals(source);
    }

    public Builder toBuilder() {
        return new Builder()
                .source(source)
                .userId(userId)
                .label(label)
                .email(email)
                .ip(ip);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommandInitiator)) return false;
        CommandInitiator that = (CommandInitiator) o;
        return source.equals(that.source)
                && Objects.equals(userId, that.userId)
                && Objects.equals(label, that.label)
                && Objects.equals(email, that.email)
                && Objects.equals(ip, that.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, userId, label, email, ip);
    }

    @Override
    public String toString() {
        return "CommandInitiator[source=" + source
                + ", userId=" + userId
                + ", label=" + label
                + ", email=" + email
                + ", ip=" + ip + "]";
    }

    public static final class Builder {
        private String source;
        private @Nullable UUID userId;
        private @Nullable String label;
        private @Nullable String email;
        private @Nullable String ip;

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder userId(@Nullable UUID userId) {
            this.userId = userId;
            return this;
        }

        public Builder label(@Nullable String label) {
            this.label = label;
            return this;
        }

        public Builder email(@Nullable String email) {
            this.email = email;
            return this;
        }

        public Builder ip(@Nullable String ip) {
            this.ip = ip;
            return this;
        }

        public CommandInitiator build() {
            return new CommandInitiator(source, userId, label, email, ip);
        }
    }
}
