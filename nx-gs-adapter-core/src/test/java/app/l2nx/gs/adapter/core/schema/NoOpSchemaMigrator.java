package app.l2nx.gs.adapter.core.schema;

import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.adapter.api.spi.provider.SchemaMigrator;

/** Registered on the core test classpath so tests that call {@code NxAdapter.start()} get past the migration. */
public final class NoOpSchemaMigrator implements SchemaMigrator {

    @Override
    public void migrate(JdbcConnectionSource source) {}
}
