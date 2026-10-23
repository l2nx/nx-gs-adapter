package app.l2nx.gs.adapter.core.config;

import app.l2nx.gs.adapter.api.spi.model.appsettings.AppRules;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Keys are read ad hoc through {@link ConfigResolver}, so the {@code l2nx.*} source literals are the only key
 * registry that configuration.md can be checked against.
 */
class DocsCoverageTest {

    private static final Pattern CODE_KEY = Pattern.compile("\"(l2nx\\.[a-z0-9.-]+)\"");
    private static final Pattern DOC_KEY_ROW = Pattern.compile("^\\|\\s*`(l2nx\\.[^`]+)`\\s*\\|");
    // A row like `l2nx.commands.kafka.<property>` documents the pass-through prefix "l2nx.commands.kafka.".
    private static final Pattern DOC_PLACEHOLDER = Pattern.compile("(<[^>]+>|\\*)$");
    private static final String PROPERTIES_FILE_NAME = "l2nx.properties";

    // Gradle runs tests with the module directory as the working directory.
    private final Path repoRoot = Paths.get("").toAbsolutePath().getParent();

    @Test
    void configurationTables_shouldListExactlyTheKeysReadInCode() throws IOException {
        Set<String> code = codeKeys();
        Set<String> en = docKeys(repoRoot.resolve("docs/public/en/configuration.md"));
        Set<String> ru = docKeys(repoRoot.resolve("docs/public/ru/configuration.md"));

        List<String> problems = new ArrayList<>();
        report(problems, "read in code but missing from en configuration.md", minus(code, en));
        report(problems, "documented in en configuration.md but not read in code", minus(en, code));
        report(problems, "in en configuration.md but not in ru", minus(en, ru));
        report(problems, "in ru configuration.md but not in en", minus(ru, en));

        assertTrue(code.size() > 0, "no l2nx.* literals found under " + repoRoot);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private Set<String> codeKeys() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (DirectoryStream<Path> modules = Files.newDirectoryStream(repoRoot)) {
            for (Path module : modules) {
                Path sources = module.resolve("src/main/java");
                if (!Files.isDirectory(sources)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(sources)) {
                    for (Path file :
                            files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                        Matcher m = CODE_KEY.matcher(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                        while (m.find()) {
                            keys.add(m.group(1));
                        }
                    }
                }
            }
        }
        keys.remove(PROPERTIES_FILE_NAME);
        // A setting value, not a key: the default title over a placed character.
        keys.remove(AppRules.DEFAULT_PHANTOM_TITLE_TEXT);
        return keys;
    }

    private static Set<String> docKeys(Path doc) throws IOException {
        Set<String> keys = new TreeSet<>();
        for (String line : Files.readAllLines(doc, StandardCharsets.UTF_8)) {
            Matcher m = DOC_KEY_ROW.matcher(line);
            if (m.find()) {
                keys.add(DOC_PLACEHOLDER.matcher(m.group(1)).replaceFirst(""));
            }
        }
        return keys;
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> result = new TreeSet<>(a);
        result.removeAll(b);
        return result;
    }

    private static void report(List<String> problems, String what, Set<String> keys) {
        if (!keys.isEmpty()) {
            problems.add(what + ": " + keys);
        }
    }
}
