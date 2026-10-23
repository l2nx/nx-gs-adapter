package app.l2nx.gs.adapter.api.spi.capability;

import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;

/**
 * Live values of the app settings file. The same instance comes from {@code NxAdapter.appRules()},
 * {@code ConnectContext} and {@code CommandContext}, usable from JVM start. No change listener: read
 * {@link #current()} once per rule check.
 */
public interface NxAppRules {

    /** Defaults until a valid file is loaded, and on a login server. No IO, any thread; one call is one consistent snapshot. */
    AppRules current();
}
