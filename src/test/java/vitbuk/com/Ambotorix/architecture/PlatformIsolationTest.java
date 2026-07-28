package vitbuk.com.Ambotorix.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that keeps the multi-platform separation from rotting: platform SDKs may only be imported
 * inside {@code adapters/}. Everything else — commands, services, draft strategies, views, entities —
 * talks to the {@code chat} port instead.
 *
 * <p>Without this test the separation is a convention, and conventions decay one convenient import
 * at a time. See MULTIPLATFORM_PLAN.md D1.
 */
class PlatformIsolationTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path ADAPTERS = Path.of("src/main/java/vitbuk/com/Ambotorix/adapters");
    private static final List<String> PLATFORM_PACKAGES = List.of("org.telegram.", "net.dv8tion.");

    @Test
    void platformSdksAreConfinedToAdapters() {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(MAIN)) {
            offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.startsWith(ADAPTERS))
                    .filter(PlatformIsolationTest::importsPlatformSdk)
                    .map(MAIN::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertTrue(offenders.isEmpty(),
                () -> "Platform SDK types must stay inside adapters/, but these files import one:\n  "
                        + String.join("\n  ", offenders)
                        + "\nRoute the call through the chat port (ChatGateway / ChatEvent / Component) instead.");
    }

    private static boolean importsPlatformSdk(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .filter(line -> line.startsWith("import "))
                    .anyMatch(line -> PLATFORM_PACKAGES.stream().anyMatch(line::contains));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
