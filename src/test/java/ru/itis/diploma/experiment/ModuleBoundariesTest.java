package ru.itis.diploma.experiment;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Фиксирует направление зависимостей модульного монолита без новой библиотеки сборки. */
class ModuleBoundariesTest {
    private static final Path SOURCE = Path.of("src/main/java/ru/itis/diploma");
    private static final Pattern UPSTREAM = Pattern.compile(
        "(?m)^\\s*import\\s+(?:static\\s+)?ru\\.itis\\.diploma\\.experiment\\."
    );
    private static final Pattern BOT_UPSTREAM = Pattern.compile(
        "(?m)^\\s*import\\s+(?:static\\s+)?ru\\.itis\\.diploma\\.experiment\\.(?!bot\\.)"
    );
    private static final Pattern ANALYTICS = Pattern.compile(
        "(?m)^\\s*import\\s+(?:static\\s+)?ru\\.itis\\.diploma\\.experiment\\.analytics\\."
    );

    @Test
    void gameAndBotsDoNotImportExperimentalImplementation() throws Exception {
        verify(SOURCE.resolve("game"), UPSTREAM);
        verify(SOURCE.resolve("experiment/bot"), BOT_UPSTREAM);
        verify(SOURCE.resolve("experiment"), ANALYTICS, true);
    }

    private void verify(Path root, Pattern forbidden) throws Exception { verify(root, forbidden, false); }

    private void verify(Path root, Pattern forbidden, boolean excludeAnalytics) throws Exception {
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java") &&
                (!excludeAnalytics || !p.startsWith(root.resolve("analytics")))).toList()) {
                assertFalse(forbidden.matcher(Files.readString(file)).find(),
                    () -> "Обратная зависимость модуля: " + file);
            }
        }
    }
}
