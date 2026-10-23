package app.l2nx.gs.adapter.core.appsessions;

import app.l2nx.gs.adapter.api.kafka.ops.model.ModuleStatus;
import app.l2nx.gs.adapter.api.spi.AdapterModule;
import app.l2nx.gs.adapter.api.spi.ConnectContext;
import app.l2nx.gs.adapter.core.appsettings.AppSettingsRuntime;

/** Binds the engine to the platform connection; the engine itself boots earlier, see {@link AppSessionsRuntime}. */
public final class AppSessionsModule implements AdapterModule {

    static final String NAME = "app-sessions";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void onConnect(ConnectContext ctx) {
        AppSettingsRuntime.connect(ctx.getSyncTopics(), ctx.getServerId());
        AppSessionEngine engine = AppSessionsRuntime.engine();
        if (engine != null) {
            engine.bindEvents(ctx.events());
        }
    }

    @Override
    public void start() {
        AppSessionEngine engine = AppSessionsRuntime.engine();
        if (engine != null) {
            engine.startScheduling();
        }
    }

    @Override
    public void stop() {
        AppSessionsRuntime.shutdown();
    }

    @Override
    public void onDisconnect() {
        AppSettingsRuntime.disconnect();
        AppSessionEngine engine = AppSessionsRuntime.engine();
        if (engine != null) {
            engine.unbindEvents();
        }
    }

    @Override
    public ModuleStatus currentStatus() {
        return ModuleStatus.builder()
                .name(NAME)
                .state(AppSessionsRuntime.moduleState())
                .stats(ModuleStatus.Stats.empty())
                .build();
    }
}
