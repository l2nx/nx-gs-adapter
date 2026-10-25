package app.l2nx.gs.adapter.api.kafka.sync.settings.game;

import app.l2nx.gs.adapter.api.kafka.sync.settings.SettingsSyncEvent;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Payload of {@link SettingsSyncEvent} with entity name {@value #ENTITY_NAME}. A {@code null} group means the build
 * has no such setting.
 */
public final class GameSettings {

    public static final String ENTITY_NAME = "gamesettings";

    private final @Nullable EnchantSettings enchant;
    private final @Nullable AttributeSettings attribute;
    private final @Nullable MailSettings mail;
    private final @Nullable PrivateStoreSettings privateStore;
    private final Instant updatedAt;

    public GameSettings(
            @Nullable EnchantSettings enchant,
            @Nullable AttributeSettings attribute,
            @Nullable MailSettings mail,
            @Nullable PrivateStoreSettings privateStore,
            Instant updatedAt) {
        this.enchant = enchant;
        this.attribute = attribute;
        this.mail = mail;
        this.privateStore = privateStore;
        this.updatedAt = Objects.requireNonNull(updatedAt, "GameSettings.updatedAt is required");
    }

    public @Nullable EnchantSettings getEnchant() {
        return enchant;
    }

    public @Nullable AttributeSettings getAttribute() {
        return attribute;
    }

    public @Nullable MailSettings getMail() {
        return mail;
    }

    public @Nullable PrivateStoreSettings getPrivateStore() {
        return privateStore;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Builder toBuilder() {
        return new Builder()
                .enchant(enchant)
                .attribute(attribute)
                .mail(mail)
                .privateStore(privateStore)
                .updatedAt(updatedAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameSettings)) return false;
        GameSettings that = (GameSettings) o;
        return Objects.equals(enchant, that.enchant)
                && Objects.equals(attribute, that.attribute)
                && Objects.equals(mail, that.mail)
                && Objects.equals(privateStore, that.privateStore)
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enchant, attribute, mail, privateStore, updatedAt);
    }

    @Override
    public String toString() {
        return "GameSettings[enchant=" + enchant
                + ", attribute=" + attribute
                + ", mail=" + mail
                + ", privateStore=" + privateStore
                + ", updatedAt=" + updatedAt + "]";
    }

    public static final class Builder {
        private @Nullable EnchantSettings enchant;
        private @Nullable AttributeSettings attribute;
        private @Nullable MailSettings mail;
        private @Nullable PrivateStoreSettings privateStore;
        private @Nullable Instant updatedAt;

        public Builder enchant(@Nullable EnchantSettings enchant) {
            this.enchant = enchant;
            return this;
        }

        public Builder attribute(@Nullable AttributeSettings attribute) {
            this.attribute = attribute;
            return this;
        }

        public Builder mail(@Nullable MailSettings mail) {
            this.mail = mail;
            return this;
        }

        public Builder privateStore(@Nullable PrivateStoreSettings privateStore) {
            this.privateStore = privateStore;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public GameSettings build() {
            return new GameSettings(enchant, attribute, mail, privateStore, updatedAt);
        }
    }
}
