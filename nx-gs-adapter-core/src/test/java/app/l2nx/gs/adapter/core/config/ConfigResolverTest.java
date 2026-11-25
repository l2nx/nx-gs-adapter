package app.l2nx.gs.adapter.core.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigResolverTest {

    private static final String VALID_KEY = "nx_sk_abcdefghijklmnopqrstuvwxyz012345";
    private static final String VALID_LS_KEY = "nx_sk_lsxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    private static final String VALID_PLATFORM_URL = "https://acme.api.l2nx.app";

    @Test
    void resolveString_shouldPreferFile_whenBothSourcesPresent() {
        Map<String, String> sys = singletonMap("l2nx.gs-key", "from-sysprop");
        Properties file = props("l2nx.gs-key", "from-file");

        ConfigResolver resolver = new ConfigResolver(sys::get, file);

        assertEquals(Optional.of("from-file"), resolver.resolveString("l2nx.gs-key"));
    }

    @Test
    void resolveString_shouldFallbackToSysprop_whenFileMissingKey() {
        Map<String, String> sys = singletonMap("l2nx.gs-key", "from-sysprop");

        ConfigResolver resolver = new ConfigResolver(sys::get, new Properties());

        assertEquals(Optional.of("from-sysprop"), resolver.resolveString("l2nx.gs-key"));
    }

    @Test
    void resolveString_shouldReturnEmpty_whenNoSourceProvidesValue() {
        ConfigResolver resolver = new ConfigResolver(empty(), new Properties());

        assertEquals(Optional.empty(), resolver.resolveString("l2nx.gs-key"));
    }

    @Test
    void resolveString_shouldTreatBlankValueAsAbsent() {
        Properties file = props("l2nx.gs-key", "   ");
        Map<String, String> sys = singletonMap("l2nx.gs-key", "from-sysprop");

        ConfigResolver resolver = new ConfigResolver(sys::get, file);

        assertEquals(Optional.of("from-sysprop"), resolver.resolveString("l2nx.gs-key"));
    }

    @Test
    void resolveString_shouldTrimSurroundingWhitespace_whenValuePresent() {
        Properties file = props("l2nx.platform-url", "  https://acme.api.l2nx.app  ");

        ConfigResolver resolver = new ConfigResolver(empty(), file);

        assertEquals(Optional.of("https://acme.api.l2nx.app"), resolver.resolveString("l2nx.platform-url"));
    }

    @ParameterizedTest(name = "l2nx.gs-key=\"{0}\"")
    @NullSource
    @ValueSource(strings = "   ")
    void resolveServerKey_shouldFailListingBothSources_whenMissingOrBlank(String value) {
        ConfigResolver resolver = withFileValue("l2nx.gs-key", value);

        IllegalStateException ex = assertThrows(IllegalStateException.class, resolver::resolveServerKey);
        assertTrue(ex.getMessage().contains("Missing"));
        assertTrue(ex.getMessage().contains("-Dl2nx.gs-key"));
        assertTrue(ex.getMessage().contains("-Dl2nx.config-file"));
        assertTrue(ex.getMessage().contains("l2nx.properties"));
    }

    @Test
    void resolveServerKey_shouldRejectInvalidServerKeyFormat_whenWrongPrefix() {
        ConfigResolver resolver = withSysprop("l2nx.gs-key", "wrong_abcdefghijklmnopqrstuvwxyz012345");

        IllegalStateException ex = assertThrows(IllegalStateException.class, resolver::resolveServerKey);
        assertTrue(ex.getMessage().contains("nx_sk_"));
    }

    @Test
    void resolveServerKey_shouldRejectInvalidServerKeyFormat_whenWrongLength() {
        ConfigResolver resolver = withSysprop("l2nx.gs-key", "nx_sk_short");

        IllegalStateException ex = assertThrows(IllegalStateException.class, resolver::resolveServerKey);
        assertTrue(ex.getMessage().contains("38"));
    }

    @ParameterizedTest(name = "l2nx.platform-url=\"{0}\"")
    @NullSource
    @ValueSource(strings = "    ")
    void resolvePlatformUrl_shouldFail_whenMissingOrBlank(String value) {
        ConfigResolver resolver = withFileValue("l2nx.platform-url", value);

        IllegalStateException ex = assertThrows(IllegalStateException.class, resolver::resolvePlatformUrl);
        assertTrue(ex.getMessage().contains("l2nx.platform-url"));
        assertTrue(ex.getMessage().contains("l2nx.config-file"));
    }

    @Test
    void resolvePlatformUrl_shouldReturnValue_whenSyspropProvides() {
        ConfigResolver resolver = withSysprop("l2nx.platform-url", VALID_PLATFORM_URL);

        assertEquals(VALID_PLATFORM_URL, resolver.resolvePlatformUrl());
    }

    @Test
    void resolvePlatformUrl_shouldStripTrailingSlash() {
        ConfigResolver resolver = withSysprop("l2nx.platform-url", VALID_PLATFORM_URL + "/");

        assertEquals(VALID_PLATFORM_URL, resolver.resolvePlatformUrl());
    }

    @ParameterizedTest(name = "rejects {0}")
    @ValueSource(
            strings = {
                "http://acme.api.l2nx.app", // wrong scheme — bearer would travel plaintext
                "ftp://acme.api.l2nx.app",
                "https:///path",
                "https://acme.api.l2nx.app?route=evil",
                "https://acme.api.l2nx.app#frag",
                "https://acme api.l2nx.app"
            })
    void resolvePlatformUrl_shouldRejectInvalidValues(String value) {
        ConfigResolver resolver = withSysprop("l2nx.platform-url", value);

        IllegalStateException ex = assertThrows(IllegalStateException.class, resolver::resolvePlatformUrl);
        assertTrue(
                ex.getMessage().contains("l2nx.platform-url"),
                "rejection message must reference key, got: " + ex.getMessage());
    }

    @Test
    void resolveEnabled_shouldDefaultToFalse_whenMissing() {
        ConfigResolver resolver = new ConfigResolver(empty(), new Properties());

        assertFalse(resolver.resolveEnabled());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"true", "TRUE", "True"})
    void resolveEnabled_shouldParseTrue_inAnyCase(String value) {
        assertTrue(withSysprop("l2nx.enabled", value).resolveEnabled());
        assertTrue(withFileValue("l2nx.enabled", value).resolveEnabled());
    }

    @Test
    void resolveEnabled_shouldReturnFalse_whenValueIsFalse() {
        ConfigResolver resolver = withSysprop("l2nx.enabled", "false");

        assertFalse(resolver.resolveEnabled());
    }

    @Test
    void resolveEnabled_shouldThrow_whenValueIsNotTrueOrFalse() {
        ConfigResolver resolver = withSysprop("l2nx.enabled", "yes");

        IllegalStateException ex = assertThrows(IllegalStateException.class, resolver::resolveEnabled);
        assertTrue(ex.getMessage().contains("yes"));
        assertTrue(ex.getMessage().contains("true"));
    }

    @Test
    void resolve_shouldBuildAdapterConfig_whenAllRequiredPresent() {
        Map<String, String> sys = new HashMap<>();
        sys.put("l2nx.gs-key", VALID_KEY);
        sys.put("l2nx.platform-url", VALID_PLATFORM_URL);
        sys.put("l2nx.enabled", "true");

        ConfigResolver resolver = new ConfigResolver(sys::get, new Properties());
        AdapterConfig config = resolver.resolve();

        assertEquals("gs", config.getHostType());
        assertEquals(VALID_KEY, config.getServerKey());
        assertEquals(VALID_PLATFORM_URL, config.getPlatformUrl());
        assertTrue(config.isEnabled());
        assertNotNull(config.getAdapterVersion());
        assertFalse(config.getAdapterVersion().isEmpty());
    }

    @Test
    void loadFileProperties_shouldReadTheFileConfigFilePathNames(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("adapter.properties");
        Files.write(configFile, "l2nx.enabled=true\n".getBytes(StandardCharsets.UTF_8));
        Map<String, String> sys = singletonMap("l2nx.config-file", "  " + configFile.toAbsolutePath() + "  ");

        Properties loaded = ConfigResolver.loadFileProperties(sys::get);

        assertEquals(configFile.toAbsolutePath(), ConfigResolver.configFilePath(sys::get));
        assertEquals("true", loaded.getProperty("l2nx.enabled"));
    }

    @Test
    void loadFileProperties_shouldReadAsUtf8_whenFileContainsNonAscii(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("adapter.properties");
        Files.write(configFile, "label=кофе ☕\n".getBytes(StandardCharsets.UTF_8));

        Map<String, String> sys =
                singletonMap("l2nx.config-file", configFile.toAbsolutePath().toString());

        Properties loaded = ConfigResolver.loadFileProperties(sys::get);

        assertEquals("кофе ☕", loaded.getProperty("label"));
    }

    @Test
    void loadFileProperties_shouldThrow_whenExplicitPathDoesNotExist(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("nonexistent-adapter.properties");
        Map<String, String> sys = singletonMap("l2nx.config-file", missing.toString());

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> ConfigResolver.loadFileProperties(sys::get));
        assertTrue(ex.getMessage().contains("l2nx.config-file"));
        assertTrue(ex.getMessage().contains(missing.toString()));
    }

    @Test
    void loadFileProperties_shouldReturnEmpty_whenConfigFileSyspropAbsentAndDefaultFileMissing(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("l2nx.properties");

        Properties loaded = ConfigResolver.loadFileProperties(empty(), missing);

        assertTrue(loaded.isEmpty());
    }

    @Test
    void loadFileProperties_shouldReadDefaultFile_whenConfigFileSyspropAbsentAndDefaultFileExists(@TempDir Path tempDir)
            throws IOException {
        Path defaultFile = tempDir.resolve("l2nx.properties");
        Files.write(
                defaultFile, ("l2nx.gs-key=" + VALID_KEY + "\nl2nx.enabled=true\n").getBytes(StandardCharsets.UTF_8));

        Properties loaded = ConfigResolver.loadFileProperties(empty(), defaultFile);

        assertEquals(VALID_KEY, loaded.getProperty("l2nx.gs-key"));
        assertEquals("true", loaded.getProperty("l2nx.enabled"));
    }

    @Test
    void loadFileProperties_shouldThrow_whenDefaultFileExistsButUnreadable(@TempDir Path tempDir) throws IOException {
        // Directory at the file path: newBufferedReader fails with IOException
        Path defaultPath = tempDir.resolve("l2nx.properties");
        Files.createDirectory(defaultPath);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> ConfigResolver.loadFileProperties(empty(), defaultPath));
        assertTrue(ex.getMessage().contains("l2nx.properties"));
        // Must NOT claim the operator set -Dl2nx.config-file
        assertTrue(
                ex.getMessage().contains("default config file"),
                "expected default-file wording, got: " + ex.getMessage());
    }

    @Test
    void loadFileProperties_shouldFallbackToDefault_whenConfigFileSyspropIsBlank(@TempDir Path tempDir)
            throws IOException {
        Path defaultFile = tempDir.resolve("l2nx.properties");
        Files.write(defaultFile, ("k=from-default\n").getBytes(StandardCharsets.UTF_8));
        Map<String, String> sys = singletonMap("l2nx.config-file", "   ");

        Properties loaded = ConfigResolver.loadFileProperties(sys::get, defaultFile);

        assertEquals("from-default", loaded.getProperty("k"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void configFilePath_shouldBeTheDefaultFile_whenConfigFileSyspropIsBlank(String blank) {
        assertEquals(Paths.get("l2nx.properties"), ConfigResolver.configFilePath(k -> blank));
        assertEquals(Paths.get("l2nx.properties"), ConfigResolver.configFilePath(k -> null));
    }

    @Test
    void configFilePath_shouldBeTheTrimmedSysprop_whenConfigFileIsGiven() {
        Path expected = Paths.get("conf", "custom.properties");

        assertEquals(expected, ConfigResolver.configFilePath(k -> " " + expected + " "));
    }

    @Test
    void resolveCommandsConfig_shouldPickUpSyspropOnlyKafkaOverrides() {
        Map<String, String> sys = new HashMap<>();
        sys.put("l2nx.commands.kafka.max.poll.records", "25");
        sys.put("l2nx.commands.kafka.fetch.min.bytes", "1024");
        Supplier<Set<String>> names = () -> new LinkedHashSet<>(sys.keySet());

        ConfigResolver resolver = new ConfigResolver(sys::get, names, new Properties());

        Map<String, Object> overrides = resolver.resolveCommandsConfig().getKafkaOverrides();
        assertEquals("25", overrides.get("max.poll.records"));
        assertEquals("1024", overrides.get("fetch.min.bytes"));
    }

    @Test
    void resolveCommandsConfig_shouldLetFilePropertiesWin_overSysprops() {
        Map<String, String> sys = new HashMap<>();
        sys.put("l2nx.commands.kafka.max.poll.records", "25");
        Supplier<Set<String>> names = () -> new LinkedHashSet<>(sys.keySet());
        Properties file = props("l2nx.commands.kafka.max.poll.records", "75");

        ConfigResolver resolver = new ConfigResolver(sys::get, names, file);

        Map<String, Object> overrides = resolver.resolveCommandsConfig().getKafkaOverrides();
        assertEquals("75", overrides.get("max.poll.records"), "file-supplied value must beat sysprop-supplied value");
    }

    @Test
    void resolveIoWorkers_shouldDefaultWhenAbsent() {
        ConfigResolver resolver = new ConfigResolver(empty(), new Properties());

        assertTrue(resolver.resolveIoWorkers() >= 2, "default must respect the DEFAULT_IO_WORKERS_MIN floor");
    }

    @Test
    void resolveIoWorkers_shouldRejectNonPositive() {
        ConfigResolver resolver = withSysprop("l2nx.io.workers", "0");

        assertThrows(IllegalStateException.class, resolver::resolveIoWorkers);
    }

    @Test
    void resolveHostType_shouldDefaultToGs_whenAbsent() {
        ConfigResolver resolver = new ConfigResolver(empty(), new Properties());

        assertEquals("gs", resolver.resolveHostType());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ls", "LS"})
    void resolveHostType_shouldAcceptLs_inAnyCase(String value) {
        ConfigResolver resolver = withSysprop("l2nx.host-type", value);

        assertEquals("ls", resolver.resolveHostType());
    }

    @Test
    void resolveHostType_shouldRejectUnknownValue() {
        ConfigResolver resolver = withSysprop("l2nx.host-type", "dp");

        IllegalStateException ex = assertThrows(IllegalStateException.class, resolver::resolveHostType);
        assertTrue(ex.getMessage().contains("l2nx.host-type"));
        assertTrue(ex.getMessage().contains("dp"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hostTypeKeys")
    void resolveServerKey_shouldReturnTheKeyOfTheHostType(
            String hostType, String property, String key, Function<ConfigResolver, String> resolve) {
        assertEquals(key, resolve.apply(withSysprop(property, key)));
    }

    private static Stream<Arguments> hostTypeKeys() {
        return Stream.of(
                hostTypeKey("default", "l2nx.gs-key", VALID_KEY, resolver -> resolver.resolveServerKey()),
                hostTypeKey("gs", "l2nx.gs-key", VALID_KEY, resolver -> resolver.resolveServerKey("gs")),
                hostTypeKey("ls", "l2nx.ls-key", VALID_LS_KEY, resolver -> resolver.resolveServerKey("ls")));
    }

    private static Arguments hostTypeKey(
            String hostType, String property, String key, Function<ConfigResolver, String> resolve) {
        return Arguments.of(hostType, property, key, resolve);
    }

    @ParameterizedTest(name = "host-type={0}")
    @CsvSource({"ls, l2nx.gs-key", "gs, l2nx.ls-key"})
    void resolveServerKey_shouldRejectMismatch_whenTheOtherKeyIsPresent(String hostType, String otherKey) {
        Map<String, String> sys = new HashMap<>();
        sys.put("l2nx.gs-key", VALID_KEY);
        sys.put("l2nx.ls-key", VALID_LS_KEY);
        ConfigResolver resolver = new ConfigResolver(sys::get, new Properties());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> resolver.resolveServerKey(hostType));
        assertTrue(ex.getMessage().contains(otherKey));
        assertTrue(ex.getMessage().contains(hostType));
    }

    @Test
    void resolveServerKey_shouldFailMissing_whenLsKeyAbsentAndHostTypeLs() {
        ConfigResolver resolver = new ConfigResolver(empty(), new Properties());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> resolver.resolveServerKey("ls"));
        assertTrue(ex.getMessage().contains("l2nx.ls-key"));
        assertTrue(ex.getMessage().contains("Missing"));
    }

    @Test
    void resolve_shouldPickLsKey_whenHostTypeLs() {
        Map<String, String> sys = new HashMap<>();
        sys.put("l2nx.host-type", "ls");
        sys.put("l2nx.ls-key", VALID_LS_KEY);
        sys.put("l2nx.platform-url", VALID_PLATFORM_URL);
        sys.put("l2nx.enabled", "true");
        ConfigResolver resolver = new ConfigResolver(sys::get, new Properties());

        AdapterConfig config = resolver.resolve();

        assertEquals("ls", config.getHostType());
        assertEquals(VALID_LS_KEY, config.getServerKey());
    }

    @Test
    void loadFileProperties_shouldThrow_whenExplicitPathIsMalformed() {
        // NUL is illegal in paths on every platform; Paths.get throws InvalidPathException
        Map<String, String> sys = singletonMap("l2nx.config-file", "bad\u0000path");

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> ConfigResolver.loadFileProperties(sys::get));
        assertTrue(
                ex.getMessage().contains("l2nx.config-file"),
                "expected config-file key in error, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("invalid path"), "expected invalid-path wording, got: " + ex.getMessage());
    }

    private static ConfigResolver withSysprop(String key, String value) {
        return new ConfigResolver(singletonMap(key, value)::get, new Properties());
    }

    private static ConfigResolver withFileValue(String key, String value) {
        return new ConfigResolver(empty(), value == null ? new Properties() : props(key, value));
    }

    private static Function<String, String> empty() {
        return key -> null;
    }

    private static Map<String, String> singletonMap(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private static Properties props(String key, String value) {
        Properties p = new Properties();
        p.setProperty(key, value);
        return p;
    }
}
