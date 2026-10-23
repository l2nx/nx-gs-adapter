package app.l2nx.gs.adapter.api.spi;

import app.l2nx.gs.adapter.api.spi.capability.NxAppRules;
import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;

final class NoOpAppRules implements NxAppRules {

    static final NoOpAppRules INSTANCE = new NoOpAppRules();

    private NoOpAppRules() {}

    @Override
    public AppRules current() {
        return AppRules.defaults();
    }
}
