package app.l2nx.gs.adapter.api.spi.provider;

import app.l2nx.gs.adapter.api.kafka.sync.gd.gearscore.GearScoreRuleset;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Source of the global gear-score ruleset for the {@code gd-sync} module, discovered via
 * {@link ServiceLoader}. A singleton entity: builds without gear score skip registration or return
 * {@link Optional#empty()}. An empty result publishes a {@code count=0} marker, on which the platform deletes the
 * server's ruleset; a registered provider that is not loaded yet relies on {@link GameDataReadinessProvider}.
 */
public interface GearScoreRulesetProvider {

    /**
     * gd-sync entity name ({@code "gearscore"}); resolves the Kafka topic via
     * {@code ctx.getSyncTopics().getGd()}.
     */
    String entityName();

    /** Current ruleset, or empty when the build has no gear-score system. */
    Optional<GearScoreRuleset> snapshot();
}
