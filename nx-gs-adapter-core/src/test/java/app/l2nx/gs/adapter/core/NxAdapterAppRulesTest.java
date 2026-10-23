package app.l2nx.gs.adapter.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import app.l2nx.gs.adapter.api.spi.provider.JdbcConnectionSource;
import app.l2nx.gs.adapter.api.spi.provider.SchemaMigrator;
import app.l2nx.gs.adapter.core.appsettings.AppSettingsRuntime;
import app.l2nx.gs.adapter.core.schema.SchemaMigration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NxAdapterAppRulesTest {

    private static final String CONFIG_FILE = "l2nx.config-file";

    @TempDir
    Path dir;

    private String savedConfigFile;

    @BeforeEach
    void setUp() throws IOException {
        savedConfigFile = System.getProperty(CONFIG_FILE);
        NxAdapter.resetForTesting();
        Files.write(
                dir.resolve("l2nx-app.properties"),
                "app.session.return-threshold-minutes=45\n".getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        NxAdapter.resetForTesting();
        if (savedConfigFile == null) {
            System.clearProperty(CONFIG_FILE);
        } else {
            System.setProperty(CONFIG_FILE, savedConfigFile);
        }
    }

    private void configFile(String content) throws IOException {
        Path config = dir.resolve("custom.properties");
        Files.write(config, content.getBytes(StandardCharsets.UTF_8));
        System.setProperty(CONFIG_FILE, config.toString());
    }

    @Test
    void appRules_shouldLoadTheFileNextToTheConfigFile_onTheFirstCall() throws IOException {
        configFile("l2nx.host-type=gs\n");

        assertEquals(45, NxAdapter.appRules().current().returnThresholdMinutes());
    }

    @Test
    void appRules_shouldKeepTheDefaults_whenTheHostIsALoginServer() throws IOException {
        configFile("l2nx.host-type=ls\n");

        assertSame(AppRules.defaults(), NxAdapter.appRules().current());
    }

    @Test
    void appRules_shouldNeitherLoadNorDecideUntilStart_whenTheHostTypeCannotBeResolved() throws IOException {
        configFile("l2nx.host-type=bogus\n");

        assertSame(AppRules.defaults(), NxAdapter.appRules().current());
        assertFalse(AppSettingsRuntime.initialized());

        configFile("l2nx.host-type=gs\n");
        assertSame(AppRules.defaults(), NxAdapter.appRules().current());
        assertFalse(AppSettingsRuntime.initialized());
    }

    @Test
    void start_shouldLoadTheFile_whenAppRulesCouldNotResolveTheHostTypeBefore() throws IOException {
        configFile("l2nx.host-type=bogus\n");
        NxAdapter.appRules();
        configFile("l2nx.host-type=gs\nl2nx.enabled=true\nl2nx.platform-url=https://127.0.0.1:1\n"
                + "l2nx.gs-key=nx_sk_cccccccccccccccccccccccccccccccc\n");
        NxAdapter.setSchemaMigrationForTesting(new SchemaMigration(
                Collections::<SchemaMigrator>emptyList, Collections::<JdbcConnectionSource>emptyList));

        NxAdapter.start();

        assertEquals(45, NxAdapter.appRules().current().returnThresholdMinutes());
    }

    @Test
    void appRules_shouldReturnTheSameInstanceOnEveryCall() throws IOException {
        configFile("l2nx.host-type=gs\n");

        assertSame(NxAdapter.appRules(), NxAdapter.appRules());
    }
}
